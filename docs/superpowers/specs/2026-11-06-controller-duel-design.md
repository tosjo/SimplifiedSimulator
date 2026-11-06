# Controller duel — design

Date: 2026-11-06. Status: approved in conversation. The owner delegated the remaining design
decisions ("I'll trust you on how to go forward").

## Goal

The owner is the only user. They want a split-screen view that makes the difference between
two signal controllers *visible*: the same traffic at the same crossing under two
controllers, side by side, with a live scoreboard. Success means the owner can watch it and
see, at a glance, which controller is winning and by how much.

## Decisions taken with the owner

| Topic | Decision |
| --- | --- |
| Audience | Just the owner, so no presenter mode and no unattended loop |
| Look | Two live 3D crossings, side by side, cameras locked together |
| Contenders | Any two of `actuated`, `fixed`, `external` (two externals each get their own port) |
| Format | Open-ended with a live score; shared keys apply to both sides |
| Approach | Two complete renderers, one per half of the window; medium quality by default |

Assumptions the owner did not contradict: following a single road user is off in a duel, and
`C` (switch controller) is off.

## 1. Start-up and timing

- New option `--duel <a>,<b>`, for example `--duel actuated,external`. Validation:
  - exactly two names from `SignalControllers.NAMES`
  - not allowed together with `--headless`, `--csv` or `--controller`
  - with two externals, `--port` + 1 must still be a valid port
- `--screenshot` **is** allowed with `--duel`, unlike in the draft shown to the owner: both
  sides simulate `--duration` seconds, then one split frame is saved. This is how the split
  view gets verified without a person at the screen.
- Ports:
  - side A uses `--port` (default 1337)
  - side B uses `--port` if A is not external, otherwise `--port` + 1
  - a side that cannot connect says so in its status line; `R` retries every external side
- Same traffic: both simulations are built from the same config and seed. Arrivals come from
  the generator's own random stream (`root.split()` before the driver stream), so they do not
  depend on the controller. A new counter, `Simulation.arrivals()`, makes this testable.
- Warm-up:
  - both sides built-in: both get the usual `Main.WARM_UP`
  - either side external: neither warms up
- One clock: `StepClock` holds speed, pause and the step accumulator, and is shared by the
  single and duel windows. Each frame the duel advances both simulations by the same number of
  fixed steps, so neither side can ever get ahead.
- Shared keys: `+`/`-` (speed), Space/`P` (pause), `[`/`]` (traffic), `R` (reconnect), `X`
  (export), `H` (help), `F12` (screenshot). `C`, `T` and `G` are not mapped in a duel.

## 2. Rendering and camera

- `CrossingRenderer` gets a second constructor taking a `RenderTarget`: the scene parent node,
  the view port, and whether to install the `CameraRig`. The existing constructor keeps its
  behaviour (root node, main view port, rig installed).
- Duel window layout:
  - side A draws into the main view port, narrowed to the left half with `cam.setViewPort(0, 0.5f, 0, 1)`
  - side B gets a clone of that camera with view port `(0.5f, 1, 0, 1)`, a view made with
    `renderManager.createMainView`, and its own root node
  - each side has its own `FilterPostProcessor`; jME's post-processor and `Camera.resize`
    both respect partial view ports (checked in the 3.9 bytecode)
- `DuelViews`, an AppState, keeps side B in step:
  - `update`: calls `rootB.updateLogicalState`
  - `render`: calls `rootB.updateGeometricState` and copies camera A's location, rotation
    and frustum onto camera B
  - `render` runs after all updates and before drawing, so both halves always show the same
    viewpoint, including the presets on keys 1-6
- `CameraRig` currently sets the aspect ratio from the full window. It will use the camera's
  own view port width instead, which changes nothing in the single view and is correct for a
  half-width camera.
- Picking (click to follow) is disabled in a duel: `CameraRig` gets a flag to turn it off.
- Both renderers get the same date, and both simulations share the clock, so the time of day
  (sun, shadows, street lights) is identical on both sides.
- Quality: when `--duel` is given without `--quality`, the parser picks `MEDIUM`; an explicit
  `--quality` wins.
- Textures go through jME's asset cache and are shared. The meshes are built twice.

## 3. Scoreboard (`DuelHud`)

- `DuelScore` is plain Java with no jME, and unit-tested. It turns two `Summary` objects
  (plus current queue and throughput) into:
  - Rows, each with a label, value A, value B, the better side (or none) and a formatted
    text. The rows are:
    - average vehicle delay
    - stops per vehicle
    - bus delay
    - cyclist delay
    - pedestrian delay
    - vehicles through
    - queue now: entry queue plus the movement queues
  - Lower is better for every row except "vehicles through".
  - A headline, which is one of:
    - "warming up…" until both sides have completed motor trips
    - "<name> leads: N% less vehicle delay"
    - "neck and neck", when the difference is under 2%
  - A safety line per side: red-light runs, conflicting greens and overlaps, flagged red
    above 0.
  - Values without data are shown as "-", never NaN.
- `DuelHud` draws, using the existing `Panel` and `HudText` colours:
  - the scoreboard at the top centre, refreshed every 0.5 s
  - a status panel at the top left (clock, speed, fps, traffic %)
  - a controller label and status line at the top of each half
  - a 2 px divider between the halves
  - flash messages and the help panel, reusing `HudState`'s pattern
- `X` writes one CSV: each side's `writeMovementsCsv` output, with `side` and `controller`
  columns added in front. The merging is a pure, tested function.

## 4. Errors and testing

Errors:
- Invalid option combinations: an `IllegalArgumentException` with a clear message, printed
  by `Main` with the usage text (existing path, exit code 2).
- An external side that is not reachable: it keeps showing its status ("not connected to
  localhost:1338"), its lights stay red, and `R` retries. Nothing crashes. As in the single
  view, the simulator never stops an external controller from showing conflicting greens; it
  counts them, and the scoreboard flags them.
- Closing the window closes both controllers, each in its own try, so one failure cannot skip
  the other.

Tests (JUnit 5, existing style):
- `SimulatorConfigTest`:
  - `--duel` is parsed, and both names are lower-cased
  - one name, three names or an unknown name is rejected
  - `--duel` with `--headless` or `--controller` is rejected
  - the default quality becomes medium, and an explicit `--quality` is kept
  - two externals on port 65535 are rejected
  - each side gets the right port
- `StepClockTest`: steps per frame, the cap, pause, speed limits.
- `DuelScoreTest`:
  - the leader on lower-is-better and on higher-is-better rows
  - "warming up" when there is no data
  - the neck-and-neck threshold
  - percentages, and "-" for NaN
  - safety flags
- `DuelExportTest`: the merged CSV has one header and side columns.
- `SimulationTest` (or a new test): two simulations with the same seed and different
  controllers have the same `arrivals()` after 15 minutes.
- Visual check (manual, not in CI): `--duel actuated,fixed --screenshot … --duration 300`
  renders a split frame. Inspect it.
- `./gradlew build` must pass, and the single view must look unchanged in a screenshot.

## Out of scope

Timed rounds or a winner screen, the ghost overlay, following road users in a duel, more than
two sides, a headless duel (the batch runner already compares controllers), and the emergency
vehicle (a separate spec).
