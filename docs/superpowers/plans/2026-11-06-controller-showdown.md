# Controller showdown: the 2014 controller against the best one we can make

Date: 2026-11-06. Status: awaiting approval.

## Goal

A controller duel with our original 2014 controller on the left and the best external
controller I can make on the right, on the same traffic. The point is to show how good the
2014 logic really is.

## Decisions taken with the owner

- **Left:** a faithful Python port of the 2014 C# controller (`CrossroadController.zip`).
  It keeps the 2014 logic exactly, but uses today's group numbers and today's conflict
  table. The original `.exe` was built for the 2014 crossing, so running it unchanged would
  mostly show that mismatch.
- **Right:** a new Python external controller, tuned until it beats the built-in actuated
  controller.

## What the 2014 controller does (from `Controller.cs` and `LightQueue.cs`)

- **Requests:** a detector report for a light that is not green adds that light to a
  request queue, once. A separate bus queue always goes first. When a light belongs to a
  "similar" pair (for example 5 and 6 sharing a lane), the report also marks its partner as
  active.
- **Starting greens:** once a second, and only when no light is green or amber, it takes
  up to 3 lights from the front of the queue. The second and third are taken only if they
  do not conflict with the ones already taken. All of them turn green together.
- **Timings, per light, counted in whole seconds:**
  - green ends at 12 s, or at 7 s if no new detector report arrived during the last second
  - amber lasts 4 s
  - then red
  - a new set of greens can start about 2 s after the last light turned red
- **Group numbers:** 2014 had other numbers (cars 1-15, bikes 21-25, pedestrians 31-42,
  buses 51-53). The port uses today's (01-12, 22/24/26, 32/34/36, 41/42) and today's
  conflict table. It keeps the 2014 timings, including the short clearance, because
  that is part of what is being judged. Pedestrian lights go straight from green to red,
  as the simulator expects.
- **Faithfulness:**
  - kept: the quirks (one group set at a time, everything red before the next set, the
    queue order)
  - dropped: the GUI, and the "similar pairs", which have no equivalent in today's layout
    (each group is one light now)

## The new controller ("smart")

It uses only what the protocol offers: detector reports per group.

- **Demand estimate per group:** detector reports counted since the group last turned red,
  plus how long it has been waiting.
- **Choosing who goes next:** green is built from urgency, not from a fixed cycle.
  - Urgency is demand × waiting time, with buses weighted extra.
  - The most urgent waiting group goes first, then every compatible waiting group, most
    urgent first, using the conflict table.
  - Groups that are already green and compatible stay green across the change.
- **Ending green:** a group's green is held while detector reports keep arriving (a gap
  timer), up to a maximum that grows with its estimated queue. It ends early when a much
  more urgent group conflicts with it.
- **Fairness:** any group waiting longer than 90 s gets the next turn.
- **Safety:** the same as the existing controller — intergreens from the simulator's
  matrix, amber 3 s for vehicles, and a plan check before it starts.
- **Shared code:** the server, protocol and safety rules move into a small
  `common.py`; `controller.py` keeps working unchanged.

## Fast testing

The Python controllers run on wall-clock time, so tests run in real time at a matched
speed. A small Java test driver (in `src/test`, run through a Gradle task `showdown`) does
this:
- runs the real `Simulation` with `ExternalController` against a given port at, for
  example, 16×, with the Python controller started with `--speed 16`
- prints delay, stops, delay per mode, throughput and the safety counters

An hour of morning rush at 16× takes about 4 minutes. Baselines (actuated, fixed) come
from the existing batch runner.

## Steps

1. **Shared module:** move the server, framing, plan check and intergreen table into
   `tools/controller/common.py`. `controller.py` imports them; its 27 tests stay green.
2. **2014 port:** `tools/controller/original2014.py`, default port 1337, with tests for
   the queue order, the maximum of 3 compatible lights, the all-red gate, the 7/12/4 s
   timings, the bus queue first and duplicate requests ignored.
3. **Smart controller:** `tools/controller/smart.py`, default port 1338, with tests for
   safety over 2 hours of random traffic, no starvation (90 s cap), urgency ordering, and
   compatible groups carried over.
4. **Test driver:** the Gradle `showdown` task. Run actuated (batch), the 2014 port and
   smart on the same seed and hour.
5. **Tuning:** a few rounds on the smart controller's gap, its maximum green and its
   urgency weights. Stop when it beats actuated on vehicle delay without making
   cyclists, pedestrians or buses clearly worse, or report honestly if it does not.
6. **Docs and the duel:** README section, CHANGELOG, version 1.3.0. Then open the duel for
   the owner:
   - `original2014.py` on 1337 and `smart.py` on 1338
   - `--duel external,external`, at 1× so the 2014 one-second ticks behave as designed

## Risks

- **Python timing at high test speeds:** at 16× a 50 ms loop is 0.8 s of simulated time,
  which is too coarse. The test runs use a 10 ms loop (`--tick` option).
- **The 2014 port's short clearance can cause overlaps:** the simulator counts these. That
  is a real finding about the 2014 logic, not a bug in the port, and it will be reported.
- **Beating actuated is not guaranteed:** actuated sees the same detectors, but it runs
  inside the simulator with no network delay. If the smart controller does not win, I will
  say so with the numbers.
