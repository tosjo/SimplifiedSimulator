#!/usr/bin/env python3
"""Our original 2014 traffic light controller, ported faithfully from C# to Python.

    python tools/controller/original2014.py            # listens on port 1337
    ./gradlew run --args="--duel external,external"     # left side

This is the logic of CrossroadController (Controller.cs, LightQueue.cs, Trafficlight.cs,
H ten Hoor, 28-05-2014), second by second, including its quirks. Only two things differ:

- It uses today's signal group numbers and today's conflict table (shared.INTERGREEN). The
  2014 crossing had other groups and its own conflict matrix, so the original program would
  mostly show that mismatch.
- Pedestrian lights get red where the original showed orange, because today's simulator has
  no amber for pedestrians. The timing is unchanged.

How it works, as in 2014. Once a second the controller does this:
1. Green lights: each one counts its seconds. At 12 it turns orange. At 7 it also turns
   orange if no road user arrived during the last second.
2. Red lights: each one counts its seconds and is unlocked at 2. When a red light reaches
   2 and no light at all is green or orange, the next set of lights turns green: up to three
   from the front of the request queue, the second and third only if they do not conflict
   with the ones already taken. Buses have their own queue, which always goes first.
3. Orange lights turn red at 4.

A light locks while it changes, and a locked light ignores a new state, exactly as before.
The clearance between conflicting directions is therefore only a few seconds. Today's
crossing needs up to 27 s for pedestrians, and the simulator counts what goes wrong.
"""

import sys
from collections import deque

from shared import (AMBER_STATE, BUSES, GREEN, INTERGREEN, PEDESTRIANS, RED, argument_parser, format_groups,
                    run_server, validate)

GREENLIGHT_MINIMAL_DURATION = 7
GREENLIGHT_MAXIMAL_DURATION = 12
ORANGELIGHT_MINIMAL_DURATION = 4
REDLIGHT_MINIMAL_DURATION = 2
TICK = 1.0  # the 2014 timer ran once a second

# The 2014 light list went cars, bikes, pedestrians, buses; the order matters for the quirks
ORDER = [g for g in sorted(INTERGREEN) if g < 20] + [22, 24, 26] + [32, 34, 36] + [41, 42]


class Light:
    def __init__(self, group):
        self.id = group
        self.state = RED
        self.time = 0
        self.locked = False
        self.active = False  # "VehicleWaiting"

    def set_state(self, state):
        if not self.locked:
            self.state = state

    def reset_time(self):
        if not self.locked:
            self.time = 0


class Original2014Controller:
    """The 2014 controller on today's crossing. Same interface as the other controllers."""

    def __init__(self, now, intergreen=INTERGREEN):
        self.intergreen = intergreen
        self.lights = [Light(g) for g in ORDER if g in intergreen]
        self.by_id = {light.id: light for light in self.lights}
        self.queue = deque()
        self.bus_queue = deque()
        self.next_tick = now + TICK
        self.changes = []
        self.sent = {light.id: RED for light in self.lights}

    def startup_messages(self):
        return [(light.id, RED) for light in sorted(self.lights, key=lambda x: x.id)]

    # Controller.HandleSensorState
    def detector(self, group, now):
        light = self.by_id.get(group)
        if light is None:
            return
        light.active = True
        if light.state != GREEN:
            self._enqueue(light)

    # LightQueue.Add: the duplicate check only looks at the normal queue
    def _enqueue(self, light):
        if light in self.queue:
            return
        if light.id in BUSES:
            self.bus_queue.append(light)
        else:
            self.queue.append(light)

    def tick(self, now):
        self.changes = []
        while now >= self.next_tick - 1e-9:
            self._green_tick()
            self._red_tick()
            self._orange_tick()
            self.next_tick += TICK
        return self.changes

    def _send(self, light):
        state = light.state
        if light.id in PEDESTRIANS and state == AMBER_STATE:
            state = RED
        if self.sent.get(light.id) != state:  # a pedestrian's orange and red are both red
            self.sent[light.id] = state
            self.changes.append((light.id, state))

    def _green_tick(self):
        greens = {light.id for light in self.lights if light.state == GREEN}
        for light in self.lights:
            if light.id not in greens:
                continue
            light.time += 1
            if light.time == GREENLIGHT_MAXIMAL_DURATION or (
                    light.time == GREENLIGHT_MINIMAL_DURATION and not light.active):
                self._turn(light, AMBER_STATE)
            else:
                light.active = False

    def _red_tick(self):
        reds = {light.id for light in self.lights if light.state == RED}
        # the snapshot is taken first, so lights that turn green during this loop are still
        # counted as red, just like the original
        for light in self.lights:
            if light.id not in reds:
                continue
            light.time += 1
            if light.time == REDLIGHT_MINIMAL_DURATION:
                light.locked = False
                light.reset_time()
                if not any(x.state in (GREEN, AMBER_STATE) for x in self.lights):
                    self._new_lights_to_green()
            else:
                light.active = False

    def _orange_tick(self):
        oranges = {light.id for light in self.lights if light.state == AMBER_STATE}
        for light in self.lights:
            if light.id not in oranges:
                continue
            light.time += 1
            if light.time == ORANGELIGHT_MINIMAL_DURATION:
                self._turn(light, RED)
            else:
                light.active = False

    def _turn(self, light, state):
        light.locked = False
        light.set_state(state)
        light.reset_time()
        light.locked = True
        self._send(light)

    # Controller.SetNewLightsToGreen + LightQueue.GetNextLight
    def _new_lights_to_green(self):
        for light in self._next_lights():
            light.active = True
            before = light.state
            light.set_state(GREEN)  # ignored while the light is still locked, and its request is gone
            light.locked = True
            if light.state != before:
                self._send(light)

    def _next_lights(self):
        queue = self.bus_queue if self.bus_queue else self.queue
        chosen = []
        if not queue:
            return chosen
        chosen.append(queue.popleft())
        for _ in range(2):
            if not queue:
                break
            candidate = queue[0]
            if any(candidate.id in self.intergreen.get(c.id, {}) for c in chosen):
                break
            chosen.append(queue.popleft())
        return chosen

    def describe(self):
        greens = [light.id for light in self.lights if light.state == GREEN]
        return "green: %s   queue: %s   bus queue: %s" % (
            format_groups(greens) or "-", format_groups(x.id for x in self.queue) or "-",
            format_groups(x.id for x in self.bus_queue) or "-")


def main(argv=None):
    parser = argument_parser("Our 2014 traffic light controller, ported to Python.", 1337)
    args = parser.parse_args(argv)
    validate(parser, args)
    return run_server(args, Original2014Controller, "2014 logic",
                      './gradlew run --args="--duel external,external"  (this is the left side)')


if __name__ == "__main__":
    sys.exit(main())
