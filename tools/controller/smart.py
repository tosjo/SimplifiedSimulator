#!/usr/bin/env python3
"""The best external controller we could make for the Harlingerstraatweg crossing.

    python tools/controller/smart.py                    # listens on port 1338
    ./gradlew run --args="--duel external,external"     # right side

It knows only what the protocol tells it: which group a road user just reached a detector
of. It has no cycle and no fixed phases. Every moment it decides as follows:

1. Who waits, and how badly. Each waiting group gets a priority:
   weight × (1 + BETA × road users waiting) × (seconds waiting + WAIT_OFFSET).
   Road users are counted from the detector reports since the group turned red (a car
   makes two reports, upstream and at the stop line). Buses weigh more, and anyone who has
   waited MAX_WAIT seconds goes first.
2. Claim the space. The highest-priority waiting groups that are compatible with each other
   claim the crossing, best first. A claim becomes a commitment once a conflicting green
   ends for it, or once nothing conflicting is lit. Commitments stay until the group gets
   green, so the plan does not flip back and forth.
3. Make room. A green that conflicts with a claimed group ends once it has had its minimum
   and one of these holds: no road user arrived for GAP seconds, it reached its maximum,
   the claimed group has waited too long, or a bus is waiting (minimum + BUS_CUT seconds).
   A green nobody conflicting needs stays green, so traffic on a free road never stops.
4. Go. A claimed group turns green as soon as every conflicting light is red and its
   intergreen time (from shared.INTERGREEN) has passed.
"""

import sys

from shared import (AMBER_STATE, GREEN, INTERGREEN, PEDESTRIANS, RED, argument_parser, format_groups,
                    run_server, validate)

# --------------------------------------------------------------------------------------
# Settings (tuned with ./gradlew showdown on the morning rush)
# --------------------------------------------------------------------------------------

KIND = {2: "main", 8: "main", 5: "side", 11: "side",
        1: "turn", 3: "turn", 4: "turn", 6: "turn", 7: "turn", 9: "turn", 10: "turn", 12: "turn",
        22: "bike", 24: "bike", 26: "bike", 32: "foot", 34: "foot", 36: "foot", 41: "bus", 42: "bus"}
MODE = {"main": "car", "side": "car", "turn": "car", "bike": "bike", "foot": "foot", "bus": "bus"}

MIN_BY_KIND = {"main": 6.0, "side": 6.0, "turn": 5.0, "bike": 6.0, "foot": 7.0, "bus": 4.0}
MAX_BY_KIND = {"main": 35.0, "side": 20.0, "turn": 15.0, "bike": 12.0, "foot": 7.0, "bus": 12.0}
GAP = {"car": 2.5, "bus": 3.0, "bike": 2.0, "foot": 0.0}
WEIGHT = {"car": 1.0, "bus": 4.0, "bike": 1.0, "foot": 1.0}
REPORTS_PER_ROAD_USER = {"car": 2, "bus": 2, "bike": 1, "foot": 1}

BETA = 0.5           # how much a longer queue counts against a longer wait
WAIT_OFFSET = 5.0    # seconds added to every wait, so a queue counts from the first moment
MAX_WAIT = 90.0      # nobody waits longer than this before going first
BUS_CUT = 4.0        # a waiting bus ends conflicting greens this long after their minimum
AMBER = 3.0          # amber for vehicles; pedestrians go straight from green to red

MIN_GREEN = {g: MIN_BY_KIND[k] for g, k in KIND.items()}
MAX_GREEN = {g: MAX_BY_KIND[k] for g, k in KIND.items()}


class SmartController:
    """Same interface as the other controllers (see shared.py)."""

    NAME = "Smart"

    def __init__(self, now, intergreen=INTERGREEN):
        self.intergreen = intergreen
        self.state = {g: RED for g in intergreen}
        self.green_started = {g: now for g in intergreen}
        self.green_ended = {g: now for g in intergreen}  # start as if everything just turned red
        self.amber_until = {}
        self.last_report = {g: float("-inf") for g in intergreen}
        self.waiting_since = {}
        self.reports = {}
        self.committed = []

    def startup_messages(self):
        return [(g, RED) for g in sorted(self.state)]

    def detector(self, group, now):
        if group not in self.state:
            return
        self.last_report[group] = now
        if self.state[group] != GREEN:
            self.waiting_since.setdefault(group, now)
            self.reports[group] = self.reports.get(group, 0) + 1

    def tick(self, now):
        changes = []
        for g, until in list(self.amber_until.items()):
            if now >= until:
                del self.amber_until[g]
                self._set(g, RED, changes)
        claimed = self._claim(now)
        for g in [x for x in self.state if self.state[x] == GREEN]:
            blocker = self._ending_for(g, claimed, now)
            if blocker is not None:
                self._end_green(g, now, changes)
                self._commit(blocker)
        for c in claimed:
            if not any(self._conflicts(c, x) and self.state[x] != RED for x in self.state):
                self._commit(c)
        for c in list(claimed):
            if self.state[c] == RED and self._may_start(c, now):
                self._start_green(c, now, changes)
        return changes

    # -- deciding -----------------------------------------------------------------------

    def priority(self, group, now):
        wait = now - self.waiting_since[group]
        mode = MODE[KIND[group]]
        waiting = self.reports.get(group, 0) / REPORTS_PER_ROAD_USER[mode]
        value = WEIGHT[mode] * (1 + BETA * waiting) * (wait + WAIT_OFFSET)
        return value + 1e6 + wait if wait >= MAX_WAIT else value

    def _claim(self, now):
        claimed = [c for c in self.committed if c in self.waiting_since]
        candidates = sorted((g for g in self.waiting_since if g not in claimed),
                            key=lambda g: (-self.priority(g, now), g))
        for g in candidates:
            if all(not self._conflicts(g, c) for c in claimed):
                claimed.append(g)
        return claimed

    def _commit(self, group):
        if group not in self.committed and all(not self._conflicts(group, c) for c in self.committed):
            self.committed.append(group)

    def _ending_for(self, green, claimed, now):
        """The claimed group this green should make room for now, or None."""
        on = now - self.green_started[green]
        if on < MIN_GREEN[green] - 1e-9:
            return None
        gap_out = now - self.last_report[green] >= GAP[MODE[KIND[green]]]
        maxed = on >= MAX_GREEN[green] - 1e-9
        for c in claimed:
            if not self._conflicts(green, c):
                continue
            urgent = now - self.waiting_since[c] >= MAX_WAIT
            bus = MODE[KIND[c]] == "bus" and on >= MIN_GREEN[green] + BUS_CUT - 1e-9
            if gap_out or maxed or urgent or bus:
                return c
        return None

    def _conflicts(self, a, b):
        return b in self.intergreen[a]

    def _may_start(self, group, now):
        for other in self.intergreen[group]:
            if self.state[other] != RED:
                return False
            if now - self.green_ended[other] < self.intergreen[other][group] - 1e-9:
                return False
        return True

    # -- switching ----------------------------------------------------------------------

    def _set(self, group, state, changes):
        self.state[group] = state
        changes.append((group, state))

    def _start_green(self, group, now, changes):
        self.waiting_since.pop(group, None)
        self.reports.pop(group, None)
        if group in self.committed:
            self.committed.remove(group)
        self.green_started[group] = now
        self.last_report[group] = max(self.last_report[group], now)
        self._set(group, GREEN, changes)

    def _end_green(self, group, now, changes):
        self.green_ended[group] = now
        if group in PEDESTRIANS:
            self._set(group, RED, changes)
        else:
            self.amber_until[group] = now + AMBER
            self._set(group, AMBER_STATE, changes)

    def describe(self):
        greens = [g for g in sorted(self.state) if self.state[g] == GREEN]
        return "green: %s   next: %s" % (format_groups(greens) or "-", format_groups(self.committed) or "-")


def main(argv=None):
    parser = argument_parser("The best external controller we could make for SimplifiedSimulator.", 1338)
    args = parser.parse_args(argv)
    validate(parser, args)
    return run_server(args, SmartController, "smart",
                      './gradlew run --args="--duel external,external"  (this is the right side)')


if __name__ == "__main__":
    sys.exit(main())
