"""Tests for the 2014 controller port. Run with: python -m unittest discover -s tools/controller"""

import random
import unittest

import original2014 as o
from shared import AMBER_STATE, GREEN, INTERGREEN, PEDESTRIANS, RED


def run(ctrl, start, end, arrivals=(), dt=0.1):
    """Ticks from start to end; returns [(time, group, state)] for every change."""
    pending = sorted(arrivals)
    out = []
    now = start
    while now <= end + 1e-9:
        while pending and pending[0][0] <= now:
            ctrl.detector(pending.pop(0)[1], now)
        out.extend((now, g, s) for g, s in ctrl.tick(now))
        now = round(now + dt, 6)
    return out


def times(changes, group, state):
    return [t for t, g, s in changes if g == group and s == state]


class Original2014Test(unittest.TestCase):

    def test_starts_all_red_and_ticks_once_a_second(self):
        ctrl = o.Original2014Controller(0.0)
        self.assertTrue(all(s == RED for g, s in ctrl.startup_messages()))
        self.assertEqual(sorted(INTERGREEN), [g for g, s in ctrl.startup_messages()])
        self.assertEqual([], run(ctrl, 0.0, 30.0))

    def test_a_request_gets_green_within_a_few_seconds_when_all_is_red(self):
        changes = run(o.Original2014Controller(0.0), 0.0, 10.0, [(0.0, 2)])
        self.assertEqual(2.0, times(changes, 2, GREEN)[0])

    def test_up_to_three_compatible_lights_start_together(self):
        # 02, 03 and 04 do not conflict with each other
        changes = run(o.Original2014Controller(0.0), 0.0, 5.0, [(0.0, 2), (0.1, 3), (0.2, 4), (0.3, 1)])
        greens = {g for t, g, s in changes if s == GREEN}
        self.assertEqual({2, 3, 4}, greens, "at most three lights, and the fourth waits")

    def test_stops_taking_lights_at_the_first_conflict_in_the_queue(self):
        # 05 conflicts with 02, so 03 behind it has to wait as well
        changes = run(o.Original2014Controller(0.0), 0.0, 5.0, [(0.0, 2), (0.1, 5), (0.2, 3)])
        self.assertEqual({2}, {g for t, g, s in changes if s == GREEN})

    def test_next_set_waits_until_every_light_is_red(self):
        changes = run(o.Original2014Controller(0.0), 0.0, 40.0, [(0.0, 2), (0.1, 5)])
        red_02 = times(changes, 2, RED)[0]
        green_05 = times(changes, 5, GREEN)[0]
        self.assertGreater(green_05, red_02)
        self.assertLessEqual(green_05 - red_02, 2.0 + 1e-6, "about two seconds of all-red")

    def test_idle_green_is_short_and_busy_green_runs_to_twelve(self):
        idle = run(o.Original2014Controller(0.0), 0.0, 40.0, [(0.0, 2)])
        idle_green = times(idle, 2, AMBER_STATE)[0] - times(idle, 2, GREEN)[0]
        busy = run(o.Original2014Controller(0.0), 0.0, 40.0, [(0.0, 2)] + [(t / 2, 2) for t in range(1, 80)])
        busy_green = times(busy, 2, AMBER_STATE)[0] - times(busy, 2, GREEN)[0]
        self.assertLessEqual(idle_green, 7.0)
        self.assertGreater(busy_green, idle_green)
        self.assertLessEqual(busy_green, 12.0)

    def test_amber_lasts_three_seconds_as_in_the_original(self):
        changes = run(o.Original2014Controller(0.0), 0.0, 40.0, [(0.0, 2)])
        self.assertAlmostEqual(3.0, times(changes, 2, RED)[0] - times(changes, 2, AMBER_STATE)[0], delta=1e-6)

    def test_buses_go_before_everyone_else(self):
        ctrl = o.Original2014Controller(0.0)
        changes = run(ctrl, 0.0, 5.0, [(0.0, 2), (0.5, 41)])
        self.assertEqual({41}, {g for t, g, s in changes if s == GREEN})

    def test_duplicate_requests_are_ignored(self):
        ctrl = o.Original2014Controller(0.0)
        for _ in range(3):
            ctrl.detector(2, 0.0)
        self.assertEqual(1, len(ctrl.queue))

    def test_unknown_groups_are_ignored(self):
        ctrl = o.Original2014Controller(0.0)
        ctrl.detector(99, 0.0)
        self.assertEqual(0, len(ctrl.queue) + len(ctrl.bus_queue))

    def test_pedestrians_never_get_amber(self):
        changes = run(o.Original2014Controller(0.0), 0.0, 40.0, [(0.0, 36)])
        self.assertEqual([GREEN, RED], [s for t, g, s in changes if g == 36])

    def test_conflicting_lights_are_never_lit_together(self):
        """The 2014 logic only starts greens when all is red, and checks the lights it combines."""
        rng = random.Random(11)
        groups = sorted(INTERGREEN)
        arrivals = sorted((rng.uniform(0, 3600), rng.choice(groups)) for _ in range(3000))
        ctrl = o.Original2014Controller(0.0)
        state = {g: RED for g in groups}
        now = 0.0
        while now <= 3600:
            while arrivals and arrivals[0][0] <= now:
                ctrl.detector(arrivals.pop(0)[1], now)
            for g, s in ctrl.tick(now):
                state[g] = s
            lit = [g for g in groups if state[g] != RED]
            for i, a in enumerate(lit):
                for b in lit[i + 1:]:
                    self.assertNotIn(b, INTERGREEN[a], "%02d and %02d lit together at %.1f" % (a, b, now))
            now = round(now + 0.5, 6)

    def test_pedestrian_groups_are_the_shared_ones(self):
        self.assertTrue(PEDESTRIANS <= set(o.ORDER))


if __name__ == "__main__":
    unittest.main()
