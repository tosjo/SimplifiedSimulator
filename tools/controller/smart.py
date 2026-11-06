#!/usr/bin/env python3
"""The fastest external controller we could make for the Harlingerstraatweg crossing.

    python tools/controller/smart.py                    # listens on port 1338
    ./gradlew run --args="--duel external,external"     # right side

It is built on the simulator's own vehicle-actuated controller (ActuatedController.java),
rewritten for what the protocol offers: a report whenever a road user reaches a detector.

- The crossing's MODULES take turns. A module is a set of groups that may be green
  together; modules nobody is waiting for are skipped.
- A group gets green when someone has reported since its last green, and keeps it while
  reports keep coming, gap-out after GAP seconds. It never goes below its minimum or above
  its maximum green.
- A group outside the current module may get green in between if nothing conflicting is lit
  and nobody in the module is waiting for it (an "alternative" green, at most ALT_MAX s).
- Buses come first. A waiting bus makes its module the next one and shortens conflicting
  greens to their minimum + BUS_CUT s, and a bus that is approaching gets BUS_EXTENSION s
  of extra green. Unlike the built-in controller, buses do not jump the queue while anyone
  has waited longer than FAIR_WAIT s, so frequent buses cannot starve the other directions.

On top of that come the improvements that made it faster in the measurements
(./gradlew showdown, morning rush). Each one can be switched off below.
"""

import sys

from shared import (AMBER_STATE, BUSES, GREEN, INTERGREEN, PEDESTRIANS, RED, argument_parser, format_groups,
                    run_server, validate)

# --------------------------------------------------------------------------------------
# The crossing
# --------------------------------------------------------------------------------------

# The simulator's module structure: (primary groups, all groups that may be green in it).
MODULES = [
    ([2, 3, 41], [2, 3, 41, 26, 34, 36, 4]),
    ([1, 6, 7, 4, 5], [1, 6, 7, 4, 5, 10]),
    ([8, 9, 42], [8, 9, 42, 22, 32, 1, 10]),
    ([10, 12, 11], [10, 12, 11, 1, 7, 4]),
    ([24, 26, 22, 34, 36, 32], [24, 26, 22, 34, 36, 32]),
]

KIND = {2: "main", 8: "main", 5: "side", 11: "side",
        1: "turn", 3: "turn", 4: "turn", 6: "turn", 7: "turn", 9: "turn", 10: "turn", 12: "turn",
        22: "bike", 24: "bike", 26: "bike", 32: "foot", 34: "foot", 36: "foot", 41: "bus", 42: "bus"}

# --------------------------------------------------------------------------------------
# Settings: the simulator's GroupTiming values, then the improvements
# --------------------------------------------------------------------------------------

MIN_BY_KIND = {"main": 6.0, "side": 6.0, "turn": 5.0, "bus": 4.0, "bike": 6.0, "foot": 7.0}
MAX_BY_KIND = {"main": 30.0, "side": 20.0, "turn": 15.0, "bus": 12.0, "bike": 10.0, "foot": 7.0}
GAP_BY_KIND = {"main": 2.5, "side": 2.5, "turn": 2.5, "bus": 3.0, "bike": 2.0, "foot": 0.0}

ALT_MAX = 12.0        # longest alternative green
BUS_EXTENSION = 10.0  # extra green for an approaching bus
BUS_APPROACH = 8.0    # a bus reported this recently is approaching
BUS_CUT = 4.0         # a waiting bus shortens conflicting greens to minimum + this
AMBER = 3.0           # amber for vehicles; pedestrians go straight from green to red
FAIR_WAIT = 90.0      # buses stop jumping the queue once anyone has waited this long

REST_IN_GREEN = True  # a green is not ended while nobody conflicting is waiting

# Variable minimum green: the protocol only reports road users arriving on a detector, so a
# queue standing still over the upstream loop looks like a gap. Instead, every report while
# the light is not green counts as a car waiting, and the green lasts at least long enough
# to clear that queue: STARTUP + HEADWAY s per car per lane (never above the maximum green).
VARIABLE_MIN = True
STARTUP = 2.0
HEADWAY = 2.0
LANES = {2: 2, 8: 2}
QUEUING = {"main", "side", "turn", "bus"}

MIN_GREEN = {g: MIN_BY_KIND[k] for g, k in KIND.items()}
MAX_GREEN = {g: MAX_BY_KIND[k] for g, k in KIND.items()}
GAP = {g: GAP_BY_KIND[k] for g, k in KIND.items()}


class SmartController:
    """Same interface as the other controllers (see shared.py)."""

    NAME = "Smart"

    def __init__(self, now, intergreen=INTERGREEN, modules=MODULES):
        self.intergreen = intergreen
        self.modules = modules
        self.state = {g: RED for g in intergreen}
        self.green_started = {g: now for g in intergreen}
        self.green_ended = {g: now for g in intergreen}  # start as if everything just turned red
        self.amber_until = {}
        self.last_report = {g: float("-inf") for g in intergreen}
        self.requests = set()
        self.requested_at = {}
        self.waiting_reports = {}
        self.min_this_green = {}
        self.served = set()
        self.alternative = set()
        self.current = -1
        self.last = len(modules) - 1

    def startup_messages(self):
        return [(g, RED) for g in sorted(self.state)]

    def detector(self, group, now):
        if group not in self.state:
            return
        self.last_report[group] = now
        if self.state[group] != GREEN:
            self.requests.add(group)
            self.requested_at.setdefault(group, now)
            self.waiting_reports[group] = self.waiting_reports.get(group, 0) + 1

    def tick(self, now):
        changes = []
        for g, until in list(self.amber_until.items()):
            if now >= until:
                del self.amber_until[g]
                self._set(g, RED, changes)

        if self.current >= 0 and self._module_finished(now):
            self.last = self.current
            self.current = -1
        if self.current < 0:
            self.current = self._next_module(now)
            self.served.clear()

        bus_waiting = self._waiting_bus()
        if self.current >= 0:
            for g in self.modules[self.current][1]:
                if g in self.requests and g not in self.served and self.state[g] == RED:
                    self._end_conflicting_alternatives(g, now, changes)
                    if self._may_start(g, now):
                        self._start_green(g, now, changes)
                        self.served.add(g)
                        self.alternative.discard(g)

        for g in [x for x in self.state if self.state[x] == GREEN]:
            if not self._keep_green(g, bus_waiting, now):
                self._end_green(g, now, changes)
                self.alternative.discard(g)

        self._start_alternatives(now, changes)
        return changes

    # -- the actuated logic -------------------------------------------------------------

    def _module_finished(self, now):
        primary, members = self.modules[self.current]
        for g in members:
            if self.state[g] == GREEN and g not in self.alternative:
                return False
            if g in self.requests and g not in self.served and g in primary:
                return False
        # members that are not primary only get a turn if they could start while the primaries ran
        return not any(g in self.requests and g not in self.served and self._may_start(g, now) for g in members)

    def _next_module(self, now):
        count = len(self.modules)
        order = [(self.last + k) % count for k in range(1, count + 1)]
        someone_overdue = any(now - t >= FAIR_WAIT for t in self.requested_at.values())
        for i in order if not someone_overdue else []:
            if any(g in BUSES and g in self.requests for g in self.modules[i][0]):
                return i
        for i in order:
            if any(g in self.requests for g in self.modules[i][0]):
                return i
        for i in order:
            if any(g in self.requests for g in self.modules[i][1]):
                return i
        return -1

    def _waiting_bus(self):
        members = self.modules[self.current][1] if self.current >= 0 else []
        for g in self.requests:
            if g in BUSES and self.state[g] == RED and g not in members:
                return g
        return None

    def _keep_green(self, g, bus_waiting, now):
        on = now - self.green_started[g]
        if on < self.min_this_green.get(g, MIN_GREEN[g]) - 1e-9:
            return True
        longest = min(MAX_GREEN[g], ALT_MAX) if g in self.alternative else MAX_GREEN[g]
        if g in BUSES and now - self.last_report[g] < BUS_APPROACH:
            longest += BUS_EXTENSION
        if bus_waiting is not None and self._conflicts(g, bus_waiting):
            longest = min(longest, MIN_GREEN[g] + BUS_CUT)
        if REST_IN_GREEN and not self._conflicting_request(g):
            return True  # nobody needs the space: let traffic keep flowing
        if on >= longest - 1e-9:
            return False
        if g in self.alternative and self._conflicts_with_waiting_member(g):
            return False
        return GAP[g] > 0 and now - self.last_report[g] < GAP[g]

    def _conflicting_request(self, g):
        return any(r in self.intergreen[g] for r in self.requests)

    def _conflicts_with_waiting_member(self, g):
        if self.current < 0:
            return False
        return any(m in self.requests and m not in self.served and self._conflicts(g, m)
                   for m in self.modules[self.current][1])

    def _end_conflicting_alternatives(self, waiting, now, changes):
        for g in list(self.alternative):
            if self._conflicts(g, waiting) and self.state[g] == GREEN and now - self.green_started[g] >= MIN_GREEN[g]:
                self._end_green(g, now, changes)
                self.alternative.discard(g)

    def _start_alternatives(self, now, changes):
        members = self.modules[self.current][1] if self.current >= 0 else []
        for g in sorted(self.state):
            if g not in self.requests or self.state[g] != RED or not self._may_start(g, now):
                continue
            if self._conflicts_with_waiting_member(g) or g in members:
                continue
            if any(self.state[o] != RED for o in self.intergreen[g]):
                continue
            self._start_green(g, now, changes)
            self.alternative.add(g)

    # -- lights -------------------------------------------------------------------------

    def _conflicts(self, a, b):
        return b in self.intergreen[a]

    def _may_start(self, group, now):
        for other in self.intergreen[group]:
            if self.state[other] != RED:
                return False
            if now - self.green_ended[other] < self.intergreen[other][group] - 1e-9:
                return False
        return True

    def _set(self, group, state, changes):
        self.state[group] = state
        changes.append((group, state))

    def _start_green(self, group, now, changes):
        self.requests.discard(group)
        self.requested_at.pop(group, None)
        waiting = self.waiting_reports.pop(group, 0)
        shortest = MIN_GREEN[group]
        if VARIABLE_MIN and KIND[group] in QUEUING:
            needed = STARTUP + HEADWAY * waiting / LANES.get(group, 1)
            shortest = max(shortest, min(needed, MAX_GREEN[group]))
        self.min_this_green[group] = shortest
        self.green_started[group] = now
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
        module = "module %d" % (self.current + 1) if self.current >= 0 else "resting"
        return "%s, green: %s" % (module, format_groups(greens) or "-")


def main(argv=None):
    parser = argument_parser("The fastest external controller we could make for SimplifiedSimulator.", 1338)
    args = parser.parse_args(argv)
    validate(parser, args)
    return run_server(args, SmartController, "smart",
                      './gradlew run --args="--duel external,external"  (this is the right side)')


if __name__ == "__main__":
    sys.exit(main())
