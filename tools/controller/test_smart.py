"""Tests for smart.py. Run with: python -m unittest discover -s tools/controller"""

import random
import unittest

import smart as s
from shared import AMBER_STATE, GREEN, INTERGREEN, PEDESTRIANS, RED

DT = 0.1


def run(ctrl, start, end, arrivals=()):
    pending = sorted(arrivals)
    out = []
    now = start
    while now <= end + 1e-9:
        while pending and pending[0][0] <= now:
            ctrl.detector(pending.pop(0)[1], now)
        out.extend((now, g, st) for g, st in ctrl.tick(now))
        now = round(now + DT, 6)
    return out


def first(changes, group, state):
    return next((t for t, g, st in changes if g == group and st == state), None)


def steady(group, start, end, every):
    """A detector report for `group` every `every` seconds."""
    out = []
    t = start
    while t < end:
        out.append((round(t, 6), group))
        t += every
    return out


class SmartControllerTest(unittest.TestCase):

    def test_starts_all_red_and_waits_the_full_clearance(self):
        ctrl = s.SmartController(0.0)
        self.assertTrue(all(st == RED for g, st in ctrl.startup_messages()))
        changes = run(ctrl, 0.0, 20.0, [(0.0, 2)])
        self.assertAlmostEqual(max(INTERGREEN[o][2] for o in INTERGREEN[2]), first(changes, 2, GREEN),
                               delta=DT + 1e-6)

    def test_green_rests_while_nobody_conflicting_waits(self):
        ctrl = s.SmartController(0.0)
        run(ctrl, 0.0, 600.0, [(0.0, 2)])
        self.assertEqual(GREEN, ctrl.state[2])

    def test_compatible_requests_start_together(self):
        changes = run(s.SmartController(0.0), 0.0, 30.0, [(0.0, 2), (0.1, 3), (0.2, 4)])
        self.assertTrue(all(first(changes, g, GREEN) is not None for g in (2, 3, 4)))

    def test_conflicting_request_ends_the_green_once_traffic_stops(self):
        ctrl = s.SmartController(0.0)
        traffic = steady(2, 0.0, 30.0, 1.0)
        changes = run(ctrl, 0.0, 80.0, traffic + [(15.0, 5)])
        ended = first(changes, 2, AMBER_STATE)
        self.assertIsNotNone(ended)
        self.assertGreaterEqual(ended, 30.0 - 1.0, "held while traffic kept coming")
        self.assertLess(ended, 30.0 + s.GAP["car"] + 2 * DT, "ended one gap after the last car")
        self.assertIsNotNone(first(changes, 5, GREEN))

    def test_endless_traffic_still_yields_at_the_maximum_green(self):
        ctrl = s.SmartController(0.0)
        changes = run(ctrl, 0.0, 200.0, steady(2, 0.0, 200.0, 0.5) + [(20.0, 5)])
        green, ended = first(changes, 2, GREEN), first(changes, 2, AMBER_STATE)
        self.assertIsNotNone(ended)
        self.assertLessEqual(ended - green, s.MAX_GREEN[2] + 2 * DT)

    def test_more_demand_goes_first(self):
        # 09 is busy and conflicts with 02 and 05, which also conflict with each other.
        # While 09 runs, one car waits for 05 (asked first) and ten for 02.
        arrivals = steady(9, 0.0, 40.0, 1.0) + [(15.0, 5), (15.0, 5)] + [(16.0 + 0.5 * i, 2) for i in range(20)]
        changes = run(s.SmartController(0.0), 0.0, 90.0, arrivals)
        green_02, green_05 = first(changes, 2, GREEN), first(changes, 5, GREEN)
        self.assertIsNotNone(green_02)
        self.assertTrue(green_05 is None or green_05 > green_02, "the busier 02 goes before 05")

    def test_a_long_wait_cuts_a_busy_green_to_its_minimum(self):
        ctrl = s.SmartController(0.0)
        # 02 is busy for minutes; 05 gets a request and must still be served in time
        changes = run(ctrl, 0.0, 300.0, steady(2, 0.0, 300.0, 0.5) + [(10.0, 5)])
        green_05 = first(changes, 5, GREEN)
        self.assertIsNotNone(green_05)
        self.assertLess(green_05 - 10.0, s.MAX_WAIT + 15.0)

    def test_a_waiting_bus_cuts_conflicting_greens_short(self):
        ctrl = s.SmartController(0.0)
        changes = run(ctrl, 0.0, 120.0, steady(9, 0.0, 120.0, 0.5) + [(30.0, 41)])
        green_09 = first(changes, 9, GREEN)
        ended_09 = first(changes, 9, AMBER_STATE)
        self.assertIsNotNone(ended_09)
        self.assertLessEqual(ended_09 - max(green_09, 30.0), s.MIN_GREEN[9] + s.BUS_CUT + 2 * DT)
        self.assertIsNotNone(first(changes, 41, GREEN))

    def test_pedestrians_go_straight_from_green_to_red(self):
        changes = run(s.SmartController(0.0), 0.0, 60.0, [(0.0, 36), (20.0, 1)])
        self.assertEqual([GREEN, RED], [st for t, g, st in changes if g == 36])

    def test_unknown_groups_are_ignored(self):
        ctrl = s.SmartController(0.0)
        ctrl.detector(99, 0.0)
        self.assertEqual([], ctrl.tick(5.0))

    def test_every_group_has_settings(self):
        for table in (s.MIN_GREEN, s.MAX_GREEN, s.KIND):
            self.assertEqual(set(INTERGREEN), set(table))
        self.assertTrue(PEDESTRIANS <= {g for g, k in s.KIND.items() if k == "foot"})


class SmartSafetyTest(unittest.TestCase):
    """Two hours of random traffic: lights never conflict, intergreens hold, nobody starves."""

    def test_safe_and_fair_under_random_traffic(self):
        rng = random.Random(5)
        groups = sorted(INTERGREEN)
        arrivals = sorted((rng.uniform(0, 7200), rng.choice(groups)) for _ in range(9000))
        ctrl = s.SmartController(0.0)
        green_ended = {g: 0.0 for g in groups}
        waiting_since = {}
        longest = 0.0
        now = 0.0
        while now <= 7200:
            while arrivals and arrivals[0][0] <= now:
                g = arrivals.pop(0)[1]
                ctrl.detector(g, now)
                if ctrl.state[g] != GREEN:
                    waiting_since.setdefault(g, now)
            for g, st in ctrl.tick(now):
                if st == GREEN:
                    for other in INTERGREEN[g]:
                        self.assertGreaterEqual(now - green_ended[other], INTERGREEN[other][g] - 1e-6,
                                                "%02d turned green too soon after %02d" % (g, other))
                    longest = max(longest, now - waiting_since.pop(g, now))
                elif st == AMBER_STATE or g in PEDESTRIANS:
                    green_ended[g] = now
            for a in groups:
                for b in INTERGREEN[a]:
                    self.assertFalse(ctrl.state[a] != RED and ctrl.state[b] != RED,
                                     "%02d and %02d lit together at %.1f s" % (a, b, now))
            now = round(now + DT, 6)
        self.assertLess(longest, s.MAX_WAIT + 60.0)


if __name__ == "__main__":
    unittest.main()
