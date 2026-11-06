"""What every external controller for SimplifiedSimulator needs: the crossing's conflict
table, the protocol, and the TCP server the simulator connects to.

A controller is any object with a NAME (up to 20 plain characters, shown in the simulator)
and these methods:

    startup_messages()     -> [(group, state)] to send when the simulator connects
    detector(group, now)   a road user reached a detector of `group`
    tick(now)              -> [(group, state)] light changes to send now
    describe()             -> one line for the log

`now` is in simulated seconds (wall clock times --speed). Only the standard library is used.
"""

import argparse
import json
import socket
import sys
import time

RED, AMBER_STATE, GREEN = 0, 1, 2
STATE_NAMES = {RED: "red", AMBER_STATE: "amber", GREEN: "green"}

PEDESTRIANS = {32, 34, 36}
BUSES = {41, 42}

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


def format_groups(groups):
    return " ".join("%02d" % g for g in groups)


def conflicts(a, b, intergreen=INTERGREEN):
    return b in intergreen.get(a, {})


def check_plan(phases, intergreen=INTERGREEN):
    """Raises ValueError when the phases are unsafe or leave a group without green."""
    groups = set(intergreen)
    for a, others in intergreen.items():
        for b in others:
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


def encode_name(name):
    """Tells the simulator who we are; older simulators ignore it."""
    return json.dumps({"controllername": {"name": name}}).encode("utf-8") + b"\0"


def parse_clock(message):
    """Returns the simulated time of a lockstep {"clock":{"time":T}} message, or None."""
    try:
        value = json.loads(message)["clock"]["time"]
    except (ValueError, KeyError, TypeError):
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return float(value)


def parse_sensor_free(message):
    """Returns the group number of a {"sensorfree":{"id":N}} message (a loop became free), or None."""
    try:
        group = json.loads(message)["sensorfree"]["id"]
    except (ValueError, KeyError, TypeError):
        return None
    return group if isinstance(group, int) and not isinstance(group, bool) else None


def encode_features(features):
    """Asks the simulator for optional messages, for example ["sensorfree"]."""
    return json.dumps({"features": list(features)}).encode("utf-8") + b"\0"


def encode_clockdone(time_):
    """Tells the simulator in lockstep that every light change for time_ has been sent."""
    return json.dumps({"clockdone": {"time": time_}}).encode("utf-8") + b"\0"


# --------------------------------------------------------------------------------------
# Server: the simulator connects to us
# --------------------------------------------------------------------------------------

# A connection that closes within this many seconds was not the simulator, but another program
# on this PC knocking on the port (Razer's app engine checks port 1337 about once a minute).
SHORTEST_SESSION = 1.0


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


def serve_session(conn, clock, make_controller, verbose, tick=0.05):
    """Runs one session with a connected simulator until it disconnects.

    `make_controller(now)` builds the controller. Normally it runs on `clock`. When the
    simulator sends lockstep clock messages (its test mode), the controller is built afresh
    on the simulator's own time at the first one, so test runs repeat exactly, and it answers
    every clock message once its light changes for that moment are sent.
    """
    try:
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)  # tiny, time-critical messages
    except OSError:
        pass  # not a TCP socket (tests use a socket pair)
    conn.settimeout(tick)
    framer = Framer()
    controller = make_controller(clock())
    features = getattr(controller, "FEATURES", [])
    conn.sendall(encode_name(controller.NAME)
                 + (encode_features(features) if features else b"")
                 + b"".join(encode_light(g, s) for g, s in controller.startup_messages()))
    lockstep = False
    sim_time = 0.0

    def now():
        return sim_time if lockstep else clock()

    while True:
        try:
            data = conn.recv(4096)
            if not data:
                return
        except socket.timeout:
            data = b""
        out = []
        for message in framer.feed(data):
            moment = parse_clock(message)
            if moment is not None:
                if not lockstep:
                    # all lights are still red, as for a new controller: start one on exact time
                    lockstep = True
                    controller = make_controller(moment)
                sim_time = moment
                out.append(_encode_changes(controller, controller.tick(now()), verbose))
                out.append(encode_clockdone(moment))
                continue
            group = parse_sensor(message)
            if group is not None:
                controller.detector(group, now())
                continue
            freed = parse_sensor_free(message)
            if freed is not None and hasattr(controller, "detector_free"):
                controller.detector_free(freed, now())
                continue
            log("ignoring unexpected message: %.80s" % message)
        if not lockstep:
            out.append(_encode_changes(controller, controller.tick(now()), verbose))
        if any(out):
            conn.sendall(b"".join(out))


def _encode_changes(controller, changes, verbose):
    if changes:
        if verbose:
            log(", ".join("%02d %s" % (g, STATE_NAMES[s]) for g, s in changes))
        elif any(s == GREEN for g, s in changes):
            log(controller.describe())
    return b"".join(encode_light(g, s) for g, s in changes)


def argument_parser(description, default_port):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--host", default="localhost", help="address to listen on (default: localhost)")
    parser.add_argument("--port", type=int, default=default_port,
                        help="port to listen on (default: %d)" % default_port)
    parser.add_argument("--speed", type=float, default=1.0,
                        help="simulation speed the simulator runs at, so the timings match (default: 1)")
    parser.add_argument("--tick", type=float, default=0.05,
                        help="seconds between updates; lower it for fast test runs (default: 0.05)")
    parser.add_argument("--verbose", action="store_true", help="print every light change")
    parser.add_argument("--once", action="store_true",
                        help="stop when the simulator disconnects, instead of waiting for it to come back")
    return parser


def validate(parser, args):
    if not 1 <= args.port <= 65535:
        parser.error("--port must be between 1 and 65535")
    if not 0 < args.speed <= 64:
        parser.error("--speed must be above 0 and at most 64")
    if not 0.001 <= args.tick <= 1:
        parser.error("--tick must be between 0.001 and 1")


def run_server(args, make_controller, mode, simulator_hint):
    """Listens for the simulator and runs a fresh controller for every connection."""
    try:
        server = open_server(args.host, args.port)
    except OSError as e:
        print("Cannot listen on %s:%d (%s)." % (args.host, args.port, e.strerror or e), file=sys.stderr)
        print("Is another controller already running? Pick another port with --port, and start the "
              "simulator with the same --port.", file=sys.stderr)
        return 1

    def clock():
        return time.monotonic() * args.speed

    log("Controller ready on %s:%d (%s). Start the simulator with:" % (args.host, args.port, mode))
    log("    " + simulator_hint)
    try:
        with server:
            while True:
                try:
                    conn, address = server.accept()
                except socket.timeout:
                    continue
                log("Simulator connected from %s:%d" % address)
                started = time.monotonic()
                with conn:
                    try:
                        serve_session(conn, clock, make_controller, args.verbose, args.tick)
                    except OSError:
                        pass  # the simulator went away mid-message; same as a normal disconnect
                if time.monotonic() - started < SHORTEST_SESSION:
                    log("That connection closed straight away, so it was not the simulator. Still waiting.")
                    continue
                if args.once:
                    log("Simulator disconnected. Stopped.")
                    return 0
                log("Simulator disconnected. Waiting for it to connect again (press R in the simulator).")
    except KeyboardInterrupt:
        log("Stopped.")
    return 0
