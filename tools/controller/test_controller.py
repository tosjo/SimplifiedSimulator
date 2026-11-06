"""Tests for controller.py. Run with: python -m unittest discover -s tools/controller"""

import contextlib
import io
import json
import random
import socket
import threading
import time
import unittest

import controller as c

DT = 0.1


def run_until(ctrl, start, end, arrivals=()):
    """Ticks from start to end; arrivals is a list of (time, group). Returns all changes with times."""
    pending = sorted(arrivals)
    changes = []
    now = start
    while now <= end + 1e-9:
        while pending and pending[0][0] <= now:
            ctrl.detector(pending.pop(0)[1], now)
        changes.extend((now, g, s) for g, s in ctrl.tick(now))
        now = round(now + DT, 6)
    return changes


def first_green(changes, group):
    return next((t for t, g, s in changes if g == group and s == c.GREEN), None)


class PlanCheckTest(unittest.TestCase):

    def test_built_in_plan_is_accepted(self):
        c.check_plan(c.PHASES, c.INTERGREEN)

    def test_phase_with_conflicting_groups_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "phase 1 is unsafe: 02 and 05 conflict"):
            c.check_plan([[2, 5]] + c.PHASES, c.INTERGREEN)

    def test_unknown_group_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "unknown groups: 99"):
            c.check_plan(c.PHASES + [[99]], c.INTERGREEN)

    def test_group_without_any_phase_is_rejected(self):
        without_buses = [[g for g in p if g != 41] for p in c.PHASES]
        with self.assertRaisesRegex(ValueError, "never get green: 41"):
            c.check_plan(without_buses, c.INTERGREEN)

    def test_one_sided_conflict_table_is_rejected(self):
        table = {1: {2: 3.0}, 2: {}}
        with self.assertRaisesRegex(ValueError, "one-sided"):
            c.check_plan([[1], [2]], table)

    def test_empty_plan_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            c.check_plan([], c.INTERGREEN)

    def test_controller_refuses_an_unsafe_plan(self):
        with self.assertRaises(ValueError):
            c.Controller(0.0, phases=[[2, 5]] + c.PHASES)


class ControllerTest(unittest.TestCase):

    def test_starts_with_every_light_red(self):
        ctrl = c.Controller(0.0)
        self.assertEqual(sorted(c.INTERGREEN), [g for g, s in ctrl.startup_messages()])
        self.assertTrue(all(s == c.RED for g, s in ctrl.startup_messages()))

    def test_nothing_turns_green_while_nobody_waits(self):
        changes = run_until(c.Controller(0.0), 0.0, 120.0)
        self.assertEqual([], changes)

    def test_waits_the_intergreen_after_start_as_if_everything_just_turned_red(self):
        changes = run_until(c.Controller(0.0), 0.0, 20.0, [(0.0, 2)])
        longest_clearance_into_02 = max(c.INTERGREEN[o][2] for o in c.INTERGREEN[2])
        self.assertAlmostEqual(longest_clearance_into_02, first_green(changes, 2), delta=DT + 1e-6)

    def test_skips_phases_nobody_waits_for(self):
        ctrl = c.Controller(0.0)
        run_until(ctrl, 0.0, 30.0, [(0.0, 8)])
        self.assertEqual(2, ctrl.phase)  # phase 3 serves group 08
        self.assertEqual(c.GREEN, ctrl.state[8])

    def test_stays_green_when_nobody_else_waits(self):
        ctrl = c.Controller(0.0)
        run_until(ctrl, 0.0, 600.0, [(0.0, 2)])
        self.assertEqual(c.GREEN, ctrl.state[2])

    def test_green_is_held_while_traffic_keeps_coming_up_to_max_green(self):
        ctrl = c.Controller(0.0)
        steady_traffic = [(t / 2, 2) for t in range(0, 400)]  # a car every 0.5 s
        changes = run_until(ctrl, 0.0, 200.0, steady_traffic + [(15.0, 8)])
        green = first_green(changes, 2)
        ended = next(t for t, g, s in changes if g == 2 and s == c.AMBER_STATE)
        self.assertGreater(ended - green, c.MIN_GREEN)
        self.assertLessEqual(ended, c.MAX_GREEN + DT + 1e-6)

    def test_phase_ends_after_the_gap_once_traffic_stops(self):
        ctrl = c.Controller(0.0)
        changes = run_until(ctrl, 0.0, 60.0, [(0.0, 2), (0.0, 8), (12.0, 2)])
        ended = next(t for t, g, s in changes if g == 2 and s == c.AMBER_STATE)
        self.assertAlmostEqual(12.0 + c.GAP, ended, delta=DT + 1e-6)

    def test_motor_traffic_gets_amber_before_red(self):
        ctrl = c.Controller(0.0)
        changes = run_until(ctrl, 0.0, 60.0, [(0.0, 2), (0.0, 8)])
        states = [(t, s) for t, g, s in changes if g == 2]
        self.assertEqual([c.GREEN, c.AMBER_STATE, c.RED], [s for t, s in states])
        self.assertAlmostEqual(c.AMBER, states[2][0] - states[1][0], delta=DT + 1e-6)

    def test_pedestrian_light_goes_straight_from_green_to_red(self):
        ctrl = c.Controller(0.0)
        changes = run_until(ctrl, 0.0, 120.0, [(0.0, 36), (0.0, 8)])
        self.assertEqual([c.GREEN, c.RED], [s for t, g, s in changes if g == 36])

    def test_detector_of_an_unknown_group_is_ignored(self):
        ctrl = c.Controller(0.0)
        ctrl.detector(99, 0.0)
        self.assertEqual([], ctrl.tick(1.0))


class SafetyTest(unittest.TestCase):
    """Two hours of random traffic: lights must never conflict and everyone must get a turn."""

    def check(self, fixed):
        rng = random.Random(7)
        groups = sorted(c.INTERGREEN)
        arrivals = sorted((rng.uniform(0, 7200), rng.choice(groups)) for _ in range(6000))
        ctrl = c.Controller(0.0, fixed=fixed)
        green_ended = {g: 0.0 for g in groups}
        waiting_since = {}
        longest_wait = 0.0
        now = 0.0
        while now <= 7200:
            while arrivals and arrivals[0][0] <= now:
                g = arrivals.pop(0)[1]
                ctrl.detector(g, now)
                if ctrl.state[g] != c.GREEN:
                    waiting_since.setdefault(g, now)
            for g, s in ctrl.tick(now):
                if s == c.GREEN:
                    for other in c.INTERGREEN[g]:
                        self.assertGreaterEqual(now - green_ended[other], c.INTERGREEN[other][g] - 1e-6,
                                                "%02d turned green too soon after %02d" % (g, other))
                    longest_wait = max(longest_wait, now - waiting_since.pop(g, now))
                elif s == c.AMBER_STATE or g in c.PEDESTRIANS:  # pedestrians go green -> red
                    green_ended[g] = now
            for a in groups:
                for b in c.INTERGREEN[a]:
                    self.assertFalse(ctrl.state[a] != c.RED and ctrl.state[b] != c.RED,
                                     "%02d and %02d not red together at %.1f s" % (a, b, now))
            now = round(now + DT, 6)
        self.assertLess(longest_wait, 300.0)

    def test_on_demand_control_is_safe_and_serves_everyone(self):
        self.check(fixed=False)

    def test_fixed_cycle_is_safe_and_serves_everyone(self):
        self.check(fixed=True)


class ProtocolTest(unittest.TestCase):

    def test_framer_splits_messages_across_chunks(self):
        framer = c.Framer()
        self.assertEqual([], framer.feed(b'{"sensorstate":'))
        self.assertEqual(['{"sensorstate":{"id":2}}', '{"x":1}'], framer.feed(b'{"id":2}}\0{"x":1}\0{"y"'))
        self.assertEqual(['{"y":2}'], framer.feed(b":2}\0"))

    def test_framer_skips_empty_messages(self):
        self.assertEqual([], c.Framer().feed(b"\0\0  \0"))

    def test_parses_a_sensor_message(self):
        self.assertEqual(41, c.parse_sensor('{"sensorstate":{"id":41}}'))

    def test_rejects_malformed_sensor_messages(self):
        for bad in ["not json", "[]", '{"sensorstate":{}}', '{"sensorstate":{"id":"2"}}',
                    '{"sensorstate":{"id":true}}', '{"lightstate":{"id":2}}', '{"sensorstate":5}']:
            with self.subTest(bad=bad):
                self.assertIsNone(c.parse_sensor(bad))

    def test_parses_the_lockstep_clock(self):
        self.assertEqual(12.3, c.parse_clock('{"clock":{"time":12.3}}'))
        for bad in ['{"clock":{}}', '{"clock":{"time":"x"}}', '{"sensorstate":{"id":2}}', "nope",
                    '{"clock":{"time":true}}']:
            with self.subTest(bad=bad):
                self.assertIsNone(c.parse_clock(bad))

    def test_parses_a_free_loop(self):
        self.assertEqual(24, c.parse_sensor_free('{"sensorfree":{"id":24}}'))
        for bad in ['{"sensorfree":{}}', '{"sensorstate":{"id":2}}', '{"sensorfree":{"id":true}}', "nope"]:
            with self.subTest(bad=bad):
                self.assertIsNone(c.parse_sensor_free(bad))

    def test_encodes_the_features_message(self):
        self.assertEqual({"features": ["sensorfree"]}, json.loads(c.encode_features(["sensorfree"])[:-1]))

    def test_encodes_the_clock_reply(self):
        self.assertEqual({"clockdone": {"time": 12.3}}, json.loads(c.encode_clockdone(12.3)[:-1]))

    def test_encodes_a_name_message_with_terminator(self):
        raw = c.encode_name("Smart")
        self.assertTrue(raw.endswith(b"\0"))
        self.assertEqual({"controllername": {"name": "Smart"}}, json.loads(raw[:-1]))

    def test_every_controller_has_a_short_plain_name(self):
        import original2014
        import smart
        names = [c.Controller.NAME, original2014.Original2014Controller.NAME, smart.SmartController.NAME]
        self.assertEqual(len(names), len(set(names)), "names tell the controllers apart")
        for name in names:
            self.assertTrue(0 < len(name) <= 20 and name.isascii(), name)

    def test_encodes_a_light_message_with_terminator(self):
        raw = c.encode_light(2, c.GREEN)
        self.assertTrue(raw.endswith(b"\0"))
        self.assertEqual({"lightstate": {"id": 2, "state": 2}}, json.loads(raw[:-1]))


class ServerTest(unittest.TestCase):

    def test_session_sends_all_red_then_green_on_request(self):
        sim, ctrl_side = socket.socketpair()
        fast_clock = lambda: time.monotonic() * 50  # noqa: E731 - 50x so the test is quick
        with contextlib.redirect_stdout(io.StringIO()):
            session = threading.Thread(target=c.serve, args=(ctrl_side, fast_clock, False, False))
            session.start()
            try:
                sim.settimeout(5)
                framer = c.Framer()
                received = []
                sim.sendall(b'{"sensorstate":{"id":2}}\0garbage\0')
                while not any(m == {"lightstate": {"id": 2, "state": 2}} for m in received):
                    received.extend(json.loads(m) for m in framer.feed(sim.recv(4096)))
                self.assertEqual({"controllername": {"name": "Python controller"}}, received[0],
                                 "the controller says who it is first")
                startup = received[1:1 + len(c.INTERGREEN)]
                self.assertTrue(all(m["lightstate"]["state"] == c.RED for m in startup))
            finally:
                sim.close()
                session.join(5)
                ctrl_side.close()
        self.assertFalse(session.is_alive(), "the session should end when the simulator disconnects")

    def test_lockstep_session_runs_on_simulated_time_and_answers_every_clock(self):
        sim, ctrl_side = socket.socketpair()
        wall_clock = time.monotonic  # real time barely moves while 15 simulated seconds pass
        with contextlib.redirect_stdout(io.StringIO()):
            session = threading.Thread(target=c.serve, args=(ctrl_side, wall_clock, False, False))
            session.start()
            try:
                sim.settimeout(5)
                framer = c.Framer()
                received = []

                def step(t, sensors=()):
                    sim.sendall(b"".join(b'{"sensorstate":{"id":%d}}\0' % g for g in sensors)
                                + b'{"clock":{"time":%.1f}}\0' % t)
                    while not received or received[-1] != {"clockdone": {"time": round(t, 1)}}:
                        received.extend(json.loads(m) for m in framer.feed(sim.recv(4096)))

                step(0.0)  # like the simulator: a first clock on its own starts lockstep
                step(0.0, sensors=[2])
                for i in range(1, 151):
                    step(i / 10)
                greens = [i for i, m in enumerate(received) if m == {"lightstate": {"id": 2, "state": 2}}]
                self.assertEqual(1, len(greens))
                answered = [m["clockdone"]["time"] for m in received[:greens[0]] if "clockdone" in m]
                # green after the longest clearance into 02 (8.9 s), counted in simulated time
                self.assertAlmostEqual(max(c.INTERGREEN[o][2] for o in c.INTERGREEN[2]), answered[-1] + 0.1,
                                       delta=0.11)
            finally:
                sim.close()
                session.join(5)
                ctrl_side.close()

    def test_once_stops_after_the_simulator_disconnects(self):
        probe = c.open_server("localhost", 0)
        port = probe.getsockname()[1]
        probe.close()
        result = []
        with contextlib.redirect_stdout(io.StringIO()):
            runner = threading.Thread(target=lambda: result.append(c.main(["--port", str(port), "--once"])))
            runner.start()
            deadline = time.monotonic() + 5
            while True:
                try:
                    sim = socket.create_connection(("localhost", port), timeout=1)
                    break
                except OSError:
                    if time.monotonic() > deadline:
                        raise
                    time.sleep(0.05)
            sim.recv(4096)  # name and startup lights
            sim.close()
            runner.join(5)
        self.assertFalse(runner.is_alive(), "the controller stopped by itself")
        self.assertEqual([0], result)

    def test_reports_a_port_that_is_in_use(self):
        blocker = c.open_server("localhost", 0)
        port = blocker.getsockname()[1]
        try:
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                self.assertEqual(1, c.main(["--port", str(port)]))
            self.assertIn("Is another controller already running?", err.getvalue())
        finally:
            blocker.close()

    def test_rejects_invalid_arguments(self):
        for args in (["--speed", "0"], ["--speed", "100"], ["--port", "70000"]):
            with self.subTest(args=args), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    c.main(args)


if __name__ == "__main__":
    unittest.main()
