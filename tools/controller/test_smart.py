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

    def tearDown(self):
        s.REST_IN_GREEN = True

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

    def test_without_rest_in_green_a_lone_green_gaps_out(self):
        s.REST_IN_GREEN = False
        changes = run(s.SmartController(0.0), 0.0, 60.0, [(0.0, 2)])
        green, ended = first(changes, 2, GREEN), first(changes, 2, AMBER_STATE)
        self.assertAlmostEqual(green + s.MIN_GREEN[2], ended, delta=2 * DT)

    def test_a_module_starts_its_waiting_groups_together(self):
        # 02, 03 and 04 are all in the first module
        changes = run(s.SmartController(0.0), 0.0, 30.0, [(0.0, 2), (0.1, 3), (0.2, 4)])
        self.assertTrue(all(first(changes, g, GREEN) is not None for g in (2, 3, 4)))

    def test_conflicting_request_ends_the_green_once_traffic_stops(self):
        traffic = steady(2, 0.0, 25.0, 1.0)
        changes = run(s.SmartController(0.0), 0.0, 80.0, traffic + [(15.0, 5)])
        ended = first(changes, 2, AMBER_STATE)
        self.assertIsNotNone(ended)
        self.assertGreaterEqual(ended, 25.0 - 1.0, "held while traffic kept coming")
        self.assertLess(ended, 25.0 + s.GAP[2] + 2 * DT, "ended one gap after the last car")
        self.assertIsNotNone(first(changes, 5, GREEN))

    def test_a_queue_counted_at_red_gets_enough_green_to_clear(self):
        # twenty reports for the two-lane 02 while it waits; then nobody reports during the green
        arrivals = [(0.1 * i, 2) for i in range(20)] + [(5.0, 5)]
        changes = run(s.SmartController(0.0), 0.0, 80.0, arrivals)
        green, ended = first(changes, 2, GREEN), first(changes, 2, AMBER_STATE)
        expected = s.STARTUP + s.HEADWAY * 20 / s.LANES.get(2, 1)
        self.assertGreaterEqual(ended - green, min(expected, s.MAX_GREEN[2]) - 2 * DT)

    def test_asks_the_simulator_for_free_reports(self):
        self.assertIn("sensorfree", s.SmartController.FEATURES)

    def test_an_occupied_loop_holds_the_green_without_new_reports(self):
        # a queue stands still over 02's loop: one report, no free report, someone conflicting waits
        ctrl = s.SmartController(0.0)
        ctrl.detector_free(2, -1.0)  # the simulator reports free loops
        changes = run(ctrl, 0.0, 120.0, [(0.0, 2), (12.0, 5)])
        green, ended = first(changes, 2, GREEN), first(changes, 2, AMBER_STATE)
        self.assertAlmostEqual(s.MAX_GREEN[2], ended - green, delta=2 * DT)

    def test_a_loop_freed_ends_the_green_one_gap_later(self):
        ctrl = s.SmartController(0.0)
        ctrl.detector_free(2, -1.0)
        changes = []
        now = 0.0
        while now <= 80:
            if abs(now - 0.0) < 1e-9:
                ctrl.detector(2, now)
            if abs(now - 12.0) < 1e-9:
                ctrl.detector(5, now)
            if abs(now - 20.0) < 1e-9:
                ctrl.detector_free(2, now)
            changes.extend((now, g, st) for g, st in ctrl.tick(now))
            now = round(now + DT, 6)
        self.assertAlmostEqual(20.0 + s.GAP[2], first(changes, 2, AMBER_STATE), delta=2 * DT)

    def test_someone_left_on_an_occupied_loop_at_red_is_requested(self):
        ctrl = s.SmartController(0.0)
        ctrl.detector_free(24, -1.0)
        # a cyclist reaches the loop during 24's green and is still on it when the green ends
        green = max(INTERGREEN[o][24] for o in INTERGREEN[24])
        arrivals = [(0.0, 24), (green + 1.0, 7)]
        changes = []
        pending = sorted(arrivals)
        now = 0.0
        while now <= 200:
            while pending and pending[0][0] <= now:
                ctrl.detector(pending.pop(0)[1], now)
            if abs(now - (green + 0.5)) < 1e-9:
                ctrl.detector_free(24, now)  # the first cyclist leaves
                ctrl.detector(24, now)  # the next one arrives and stays
            changes.extend((now, g, st) for g, st in ctrl.tick(now))
            now = round(now + DT, 6)
        self.assertGreaterEqual(len([t for t, g, st in changes if g == 24 and st == GREEN]), 2)

    def test_a_cyclist_left_on_the_loop_when_green_ends_still_gets_a_turn(self):
        # cyclists keep reporting during 24's green, so it runs to its maximum; the last one is
        # still on the loop when it ends and reports nothing more. 07 conflicts with 24.
        # 24 turns green after the longest clearance into it and ends at its maximum, 0.2 s
        # after the last report
        green = max(INTERGREEN[o][24] for o in INTERGREEN[24])
        arrivals = [(0.0, 24)] + steady(24, green + 0.8, green + s.MAX_GREEN[24] - 0.1, 1.0) + [(15.0, 7)]
        changes = run(s.SmartController(0.0), 0.0, 200.0, arrivals)
        greens_24 = [t for t, g, st in changes if g == 24 and st == GREEN]
        self.assertGreaterEqual(len(greens_24), 2, "24 got green again for the cyclist left waiting")

    def test_no_new_request_when_nobody_reported_near_the_end(self):
        arrivals = [(0.0, 24), (2.0, 24), (5.0, 7)]
        changes = run(s.SmartController(0.0), 0.0, 200.0, arrivals)
        self.assertEqual(1, len([t for t, g, st in changes if g == 24 and st == GREEN]))

    def test_variable_minimum_never_exceeds_the_maximum(self):
        arrivals = [(0.01 * i, 3) for i in range(200)] + [(5.0, 5)]
        changes = run(s.SmartController(0.0), 0.0, 120.0, arrivals)
        self.assertLessEqual(first(changes, 3, AMBER_STATE) - first(changes, 3, GREEN), s.MAX_GREEN[3] + 2 * DT)

    def test_endless_traffic_still_yields_at_the_maximum_green(self):
        changes = run(s.SmartController(0.0), 0.0, 200.0, steady(2, 0.0, 200.0, 0.5) + [(20.0, 5)])
        green, ended = first(changes, 2, GREEN), first(changes, 2, AMBER_STATE)
        self.assertIsNotNone(ended)
        self.assertLessEqual(ended - green, s.MAX_GREEN[2] + 2 * DT)

    def test_modules_nobody_waits_for_are_skipped(self):
        # requests in modules 1 and 3 only: nothing of module 2 (01 06 07 04 05) may light up
        changes = run(s.SmartController(0.0), 0.0, 120.0, [(0.0, 2), (0.0, 8), (40.0, 2), (40.0, 8)])
        lit = {g for t, g, st in changes if st == GREEN}
        self.assertEqual({2, 8}, lit)

    def test_every_module_gets_a_turn_under_endless_traffic(self):
        arrivals = steady(2, 0.0, 400.0, 0.5) + steady(8, 0.0, 400.0, 0.5) + [(10.0, 11), (10.0, 6)]
        changes = run(s.SmartController(0.0), 0.0, 400.0, arrivals)
        self.assertLess(first(changes, 11, GREEN) - 10.0, 150.0)
        self.assertLess(first(changes, 6, GREEN) - 10.0, 150.0)

    def test_a_waiting_bus_cuts_conflicting_greens_short(self):
        changes = run(s.SmartController(0.0), 0.0, 120.0, steady(9, 0.0, 120.0, 0.5) + [(30.0, 41)])
        green_09 = first(changes, 9, GREEN)
        ended_09 = first(changes, 9, AMBER_STATE)
        self.assertIsNotNone(ended_09)
        self.assertLessEqual(ended_09 - max(green_09, 30.0), s.MIN_GREEN[9] + s.BUS_CUT + 2 * DT)
        self.assertIsNotNone(first(changes, 41, GREEN))

    def test_pressure_order_serves_the_longest_waiting_module_first(self):
        before, s.ORDER = s.ORDER, "pressure"
        try:
            # a busy 02 (module 1) holds up 11 (module 4, waiting since 12 s) and 6 (module 2, since
            # 30 s); after module 1 the cycle would serve module 2 first, the pressure module 4
            arrivals = steady(2, 0.0, 60.0, 0.5) + [(12.0, 11), (30.0, 6)]
            changes = run(s.SmartController(0.0), 0.0, 120.0, arrivals)
            self.assertLess(first(changes, 11, GREEN), first(changes, 6, GREEN))
        finally:
            s.ORDER = before

    def test_frequent_buses_cannot_starve_the_other_directions(self):
        # buses on 41 and 42 keep asking; 05 (module 2, no bus) must still get its turn
        arrivals = steady(41, 0.0, 600.0, 7.0) + steady(42, 3.0, 600.0, 7.0) + [(10.0, 5)]
        changes = run(s.SmartController(0.0), 0.0, 600.0, arrivals)
        green_05 = first(changes, 5, GREEN)
        self.assertIsNotNone(green_05)
        self.assertLess(green_05 - 10.0, s.FAIR_WAIT + 60.0)

    def test_a_group_that_fits_beside_gets_an_alternative_green(self):
        # 02 runs; 08 is in another module but does not conflict with 02
        changes = run(s.SmartController(0.0), 0.0, 60.0, steady(2, 0.0, 60.0, 1.0) + [(20.0, 8)])
        self.assertIsNotNone(first(changes, 8, GREEN))
        self.assertIsNone(first(changes, 2, AMBER_STATE), "02 keeps its green meanwhile")

    def test_pedestrians_go_straight_from_green_to_red(self):
        changes = run(s.SmartController(0.0), 0.0, 60.0, [(0.0, 36), (20.0, 1)])
        self.assertEqual([GREEN, RED], [st for t, g, st in changes if g == 36])

    def test_unknown_groups_are_ignored(self):
        ctrl = s.SmartController(0.0)
        ctrl.detector(99, 0.0)
        self.assertEqual([], ctrl.tick(5.0))

    def test_every_group_has_settings_and_a_module(self):
        for table in (s.MIN_GREEN, s.MAX_GREEN, s.GAP, s.KIND):
            self.assertEqual(set(INTERGREEN), set(table))
        self.assertEqual(set(INTERGREEN), {g for _, members in s.MODULES for g in members})

    def test_modules_are_conflict_free(self):
        for primary, members in s.MODULES:
            self.assertTrue(set(primary) <= set(members))
            for a in members:
                for b in members:
                    self.assertNotIn(b, INTERGREEN[a], "%02d and %02d share a module" % (a, b))


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
        self.assertLess(longest, 240.0)


if __name__ == "__main__":
    unittest.main()
