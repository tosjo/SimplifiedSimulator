#!/usr/bin/env python3
"""A ready-to-run external traffic light controller for SimplifiedSimulator.

Start the controller first, then the simulator:

    python tools/controller/controller.py
    ./gradlew run --args="--controller external"

(If the simulator is already running: press C until it says "External controller",
then R to connect.)

The controller cycles through PHASES below. A phase is a set of signal groups that may be
green together. Groups only get green when someone is waiting (the simulator reports each
road user that reaches a detector), green is held while traffic keeps coming, and phases
nobody is waiting for are skipped. With --fixed every group gets green every cycle instead.

Everything you might want to change is in the settings block below. The controller checks
your PHASES against the conflict table when it starts, so it refuses to run a plan that
would give conflicting directions green together.

Only the Python standard library is used (Python 3.8 or newer).
"""

import sys

from shared import (AMBER_STATE, GREEN, INTERGREEN, PEDESTRIANS, RED, STATE_NAMES,  # noqa: F401 - re-exported
                    Framer, argument_parser, check_plan, encode_clockdone, encode_light, encode_name,
                    format_groups, log, open_server, parse_clock, parse_sensor, run_server, serve_session,
                    validate)

# --------------------------------------------------------------------------------------
# Settings
# --------------------------------------------------------------------------------------

# Signal groups: 01-12 motor traffic, 22-26 cyclists, 32-36 pedestrians, 41-42 buses.
# These phases are the simulator's own module structure for this crossing.
PHASES = [
    [2, 3, 41, 26, 34, 36, 4],
    [1, 6, 7, 4, 5, 10],
    [8, 9, 42, 22, 32, 1, 10],
    [10, 12, 11, 1, 7, 4],
    [24, 26, 22, 34, 36, 32],
]

MIN_GREEN = 6.0     # seconds every green lasts at least
MAX_GREEN = 40.0    # seconds after which a phase ends even if traffic keeps coming
GAP = 3.0           # end the phase when nobody arrived for this many seconds
FIXED_GREEN = 15.0  # with --fixed: how long each phase lasts
AMBER = 3.0         # amber time; pedestrian lights go straight from green to red

# The conflict table (INTERGREEN) and the protocol live in shared.py.

# --------------------------------------------------------------------------------------
# Control logic (no networking, so it can be tested with a fake clock)
# --------------------------------------------------------------------------------------

class Controller:
    """Decides the light states. Feed it detector reports and call tick() regularly.

    It starts as if every light had just turned red, so it is safe whatever the simulator
    showed before it connected.
    """

    NAME = "Python controller"

    def __init__(self, now, phases=PHASES, intergreen=INTERGREEN, fixed=False):
        check_plan(phases, intergreen)
        self.phases = [list(p) for p in phases]
        self.intergreen = intergreen
        self.fixed = fixed
        self.state = {g: RED for g in intergreen}
        self.green_started = {g: now for g in intergreen}
        self.green_ended = {g: now for g in intergreen}
        self.amber_until = {}
        self.waiting = set()
        self.last_arrival = {g: float("-inf") for g in intergreen}
        self.phase = 0
        self.phase_started = now

    def startup_messages(self):
        """Light states to send right after connecting: everything red."""
        return [(g, RED) for g in sorted(self.state)]

    def detector(self, group, now):
        """A road user reached a detector of `group`. Unknown groups are ignored."""
        if group not in self.state:
            return
        self.last_arrival[group] = now
        if self.state[group] != GREEN:
            self.waiting.add(group)

    def tick(self, now):
        """Advances the controller to `now` and returns the light changes as (group, state)."""
        changes = []
        for g, until in list(self.amber_until.items()):
            if now >= until:
                del self.amber_until[g]
                self._set(g, RED, changes)
        nxt = self._next_phase()
        if nxt is not None and self._phase_may_end(now):
            for g in self.phases[self.phase]:
                if self.state[g] == GREEN and g not in self.phases[nxt]:
                    self._end_green(g, now, changes)
            self.phase = nxt
            self.phase_started = now
        for g in self.phases[self.phase]:
            if self.state[g] == RED and (self.fixed or g in self.waiting) and self._may_start(g, now):
                self.waiting.discard(g)
                self.green_started[g] = now
                self._set(g, GREEN, changes)
        return changes

    def _set(self, group, state, changes):
        self.state[group] = state
        changes.append((group, state))

    def _end_green(self, group, now, changes):
        self.green_ended[group] = now
        if group in PEDESTRIANS:
            self._set(group, RED, changes)
        else:
            self.amber_until[group] = now + AMBER
            self._set(group, AMBER_STATE, changes)

    def _may_start(self, group, now):
        for other in self.intergreen[group]:
            if self.state[other] != RED:
                return False
            if now - self.green_ended[other] < self.intergreen[other][group] - 1e-9:
                return False
        return True

    def _next_phase(self):
        """The next phase to serve, or None to stay in the current one."""
        count = len(self.phases)
        if self.fixed:
            return (self.phase + 1) % count if count > 1 else None
        for step in range(1, count + 1):
            candidate = (self.phase + step) % count
            if candidate != self.phase and any(g in self.waiting for g in self.phases[candidate]):
                return candidate
        return None

    def _phase_may_end(self, now):
        elapsed = now - self.phase_started
        greens = [g for g in self.phases[self.phase] if self.state[g] == GREEN]
        if any(now - self.green_started[g] < MIN_GREEN for g in greens):
            return False
        if self.fixed:
            return elapsed >= FIXED_GREEN
        if elapsed >= MAX_GREEN:
            return True
        if any(g in self.waiting for g in self.phases[self.phase]):
            return False  # someone in this phase is still waiting for their green
        return all(now - self.last_arrival[g] >= GAP for g in greens)

    def describe(self):
        greens = [g for g in self.phases[self.phase] if self.state[g] == GREEN]
        return "phase %d of %d, green: %s" % (self.phase + 1, len(self.phases), format_groups(greens) or "-")


# --------------------------------------------------------------------------------------
# Running it
# --------------------------------------------------------------------------------------

def serve(conn, clock, fixed, verbose, tick=0.05):
    """Runs one session with a connected simulator until it disconnects."""
    serve_session(conn, clock, lambda now: Controller(now, fixed=fixed), verbose, tick)


def main(argv=None):
    parser = argument_parser("External traffic light controller for SimplifiedSimulator.", 1337)
    parser.add_argument("--fixed", action="store_true",
                        help="give every group green every cycle instead of only when someone waits")
    args = parser.parse_args(argv)
    validate(parser, args)
    try:
        check_plan(PHASES, INTERGREEN)
    except ValueError as e:
        print("The plan in the settings is not safe to run: %s" % e, file=sys.stderr)
        return 2
    return run_server(args, lambda now: Controller(now, fixed=args.fixed),
                      "fixed cycle" if args.fixed else "on demand", './gradlew run --args="--controller external"')


if __name__ == "__main__":
    sys.exit(main())
