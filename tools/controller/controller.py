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

import argparse
import json
import socket
import sys
import time

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
PEDESTRIANS = {32, 34, 36}

# Seconds from the end of the first group's green until the second may turn green.
# A key means the two groups conflict. Exported from the simulator's ConflictMatrix.
INTERGREEN = {
    1: {26: 7.3, 36: 7.3, 41: 5.7},
    2: {5: 3.3, 6: 5.0, 9: 3.0, 10: 6.7, 11: 5.4, 12: 5.1, 24: 7.5},
    3: {5: 4.9, 6: 6.2, 8: 7.9, 9: 7.1, 11: 7.4, 12: 5.9, 22: 10.2, 32: 3.0, 42: 7.4},
    4: {22: 4.3, 32: 3.4},
    5: {2: 5.8, 3: 5.2, 8: 3.0, 9: 4.7, 12: 3.0, 22: 3.0, 26: 8.1, 32: 3.0, 36: 7.6, 41: 6.5, 42: 3.0},
    6: {2: 7.6, 3: 6.3, 8: 4.5, 9: 5.5, 11: 7.1, 12: 6.5, 22: 3.0, 24: 10.0, 32: 3.0, 34: 3.0, 42: 3.0},
    7: {22: 8.7, 24: 6.6, 32: 9.0, 34: 6.1, 42: 5.6},
    8: {3: 3.5, 5: 6.2, 6: 5.9, 11: 3.4, 12: 4.9, 24: 3.3, 34: 3.0},
    9: {2: 8.9, 3: 8.0, 5: 8.6, 6: 7.0, 11: 5.2, 12: 6.4, 24: 3.0, 26: 11.5, 34: 3.0, 36: 3.0, 41: 9.7},
    10: {2: 5.2, 24: 8.0, 26: 4.6, 34: 6.8, 36: 4.4, 41: 4.5},
    11: {2: 3.8, 3: 5.4, 6: 3.9, 8: 5.9, 9: 5.3, 22: 8.0, 26: 3.2, 32: 7.8, 36: 3.0, 41: 3.2, 42: 5.3},
    12: {2: 5.6, 3: 6.6, 5: 8.2, 6: 7.6, 8: 7.6, 9: 6.5, 26: 3.3, 36: 3.0, 41: 4.6},
    22: {3: 5.3, 4: 9.1, 5: 8.2, 6: 7.5, 7: 7.3, 11: 5.2},
    24: {2: 6.5, 6: 5.5, 7: 11.2, 8: 10.4, 9: 8.8, 10: 8.0, 41: 7.4, 42: 10.4},
    26: {1: 7.4, 5: 4.9, 9: 4.1, 10: 8.8, 11: 7.9, 12: 7.1},
    32: {3: 12.6, 4: 17.2, 5: 14.3, 6: 11.8, 7: 14.8, 11: 12.6},
    34: {6: 16.0, 7: 26.7, 8: 22.8, 9: 17.4, 10: 20.2, 42: 24.8},
    36: {1: 13.9, 5: 11.3, 9: 10.5, 10: 16.4, 11: 13.9, 12: 11.0},
    41: {1: 5.8, 5: 4.0, 9: 3.3, 10: 8.3, 11: 7.5, 12: 7.1, 24: 10.2},
    42: {3: 5.9, 5: 9.7, 6: 9.4, 7: 5.8, 11: 5.8, 24: 6.5, 34: 5.5},
}

# --------------------------------------------------------------------------------------
# Control logic (no networking, so it can be tested with a fake clock)
# --------------------------------------------------------------------------------------

RED, AMBER_STATE, GREEN = 0, 1, 2
STATE_NAMES = {RED: "red", AMBER_STATE: "amber", GREEN: "green"}


def check_plan(phases, intergreen):
    """Raises ValueError when the plan is unsafe or incomplete."""
    groups = set(intergreen)
    for a, conflicts in intergreen.items():
        for b in conflicts:
            if a not in intergreen.get(b, {}):
                raise ValueError("conflict table is one-sided: %02d conflicts with %02d but not back" % (a, b))
    if not phases:
        raise ValueError("PHASES is empty")
    for number, phase in enumerate(phases, 1):
        unknown = [g for g in phase if g not in groups]
        if unknown:
            raise ValueError("phase %d has unknown groups: %s" % (number, format_groups(unknown)))
        for i, a in enumerate(phase):
            for b in phase[i + 1:]:
                if b in intergreen[a]:
                    raise ValueError("phase %d is unsafe: %02d and %02d conflict" % (number, a, b))
    missing = groups - {g for phase in phases for g in phase}
    if missing:
        raise ValueError("these groups never get green: %s" % format_groups(sorted(missing)))


def format_groups(groups):
    return " ".join("%02d" % g for g in groups)


class Controller:
    """Decides the light states. Feed it detector reports and call tick() regularly.

    It starts as if every light had just turned red, so it is safe whatever the simulator
    showed before it connected.
    """

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
# Protocol: JSON messages, each followed by a NUL byte
# --------------------------------------------------------------------------------------

class Framer:
    """Splits the incoming byte stream into messages at the NUL terminators."""

    def __init__(self):
        self.buffer = b""

    def feed(self, data):
        self.buffer += data
        *messages, self.buffer = self.buffer.split(b"\0")
        return [m.decode("utf-8", errors="replace") for m in messages if m.strip()]


def parse_sensor(message):
    """Returns the group number of a {"sensorstate":{"id":N}} message, or None."""
    try:
        parsed = json.loads(message)
        group = parsed["sensorstate"]["id"]
    except (ValueError, KeyError, TypeError):
        return None
    return group if isinstance(group, int) and not isinstance(group, bool) else None


def encode_light(group, state):
    return json.dumps({"lightstate": {"id": group, "state": state}}).encode("utf-8") + b"\0"


# --------------------------------------------------------------------------------------
# Server: the simulator connects to us
# --------------------------------------------------------------------------------------

def log(message):
    print(time.strftime("%H:%M:%S"), message, flush=True)


def open_server(host, port):
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    if sys.platform != "win32":
        # lets a restarted controller reuse the port at once (on Windows this would allow two)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((host, port))
    server.listen(1)
    server.settimeout(0.5)  # wake up regularly so Ctrl+C works on Windows
    return server


def serve(conn, clock, fixed, verbose):
    """Runs one session with a connected simulator until it disconnects."""
    conn.settimeout(0.05)
    controller = Controller(clock(), fixed=fixed)
    framer = Framer()
    conn.sendall(b"".join(encode_light(g, s) for g, s in controller.startup_messages()))
    while True:
        try:
            data = conn.recv(4096)
            if not data:
                return
        except socket.timeout:
            data = b""
        for message in framer.feed(data):
            group = parse_sensor(message)
            if group is None:
                log("ignoring unexpected message: %.80s" % message)
                continue
            controller.detector(group, clock())
        changes = controller.tick(clock())
        if changes:
            conn.sendall(b"".join(encode_light(g, s) for g, s in changes))
            if verbose:
                log(", ".join("%02d %s" % (g, STATE_NAMES[s]) for g, s in changes))
            elif any(s == GREEN for g, s in changes):
                log(controller.describe())


def main(argv=None):
    parser = argparse.ArgumentParser(description="External traffic light controller for SimplifiedSimulator.")
    parser.add_argument("--host", default="localhost", help="address to listen on (default: localhost)")
    parser.add_argument("--port", type=int, default=1337, help="port to listen on (default: 1337)")
    parser.add_argument("--speed", type=float, default=1.0,
                        help="simulation speed the simulator runs at, so the timings match (default: 1)")
    parser.add_argument("--fixed", action="store_true",
                        help="give every group green every cycle instead of only when someone waits")
    parser.add_argument("--verbose", action="store_true", help="print every light change")
    args = parser.parse_args(argv)
    if not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    if not 0 < args.speed <= 64:
        parser.error("--speed must be above 0 and at most 64")

    try:
        check_plan(PHASES, INTERGREEN)
    except ValueError as e:
        print("The plan in the settings is not safe to run: %s" % e, file=sys.stderr)
        return 2
    try:
        server = open_server(args.host, args.port)
    except OSError as e:
        print("Cannot listen on %s:%d (%s)." % (args.host, args.port, e.strerror or e), file=sys.stderr)
        print("Is another controller already running? Pick another port with --port, and start the "
              "simulator with the same --port.", file=sys.stderr)
        return 1

    def clock():
        return time.monotonic() * args.speed

    mode = "fixed cycle" if args.fixed else "on demand"
    log("Controller ready on %s:%d (%s). Start the simulator with:" % (args.host, args.port, mode))
    log('    ./gradlew run --args="--controller external"')
    try:
        with server:
            while True:
                try:
                    conn, address = server.accept()
                except socket.timeout:
                    continue
                log("Simulator connected from %s:%d" % address)
                with conn:
                    try:
                        serve(conn, clock, args.fixed, args.verbose)
                    except OSError:
                        pass  # the simulator went away mid-message; same as a normal disconnect
                log("Simulator disconnected. Waiting for it to connect again (press R in the simulator).")
    except KeyboardInterrupt:
        log("Stopped.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
