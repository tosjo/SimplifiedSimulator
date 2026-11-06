# Controller Duel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `--duel <a>,<b>` opens a split window with the same traffic under two signal controllers, cameras locked together, and a live scoreboard.

**Architecture:** Two `Simulation`s built from one config and seed advance in lockstep through a shared `StepClock`. Each simulation is drawn by its own `CrossingRenderer` into half of the window. Side B has its own view port, root node and post-processor, and its camera copies side A's every frame. Scoring and CSV merging are plain-Java classes (`DuelScore`, `DuelExport`) drawn by a `DuelHud` AppState.

**Tech Stack:** Java 17, jMonkeyEngine 3.9 (LWJGL 3), JUnit 5, Gradle 8.14.

**Spec:** `docs/superpowers/specs/2026-11-06-controller-duel-design.md`

## Global Constraints

- Java 17 (`options.release.set(17)`); `-Xlint:all` stays warning-free; no new dependencies.
- The single-window behaviour and all existing tests stay unchanged.
- Commits are conventional (`feat:`/`fix:`/`docs:`/`refactor:`/`test:`) with no co-authored-by line. Release commit: `feat: add controller duel - v1.2.0`.
- Run `./gradlew test` before every commit, and `./gradlew build` at the end.
- Values without data show as `-`, never `NaN`.

## Review Focus

1. Two externals on one port, or `--port 65535` with two externals. Expect a clear error at start-up, not a bind clash. Test owner: Task 1.
2. The window is resized during a duel. Expect neither half to be stretched (both cameras go through `Camera.resize` with fixed aspect, and the rig's own aspect respects the view port). Test owner: Task 6, `CameraRigTest.aspectUsesTheViewPortWidth`.
3. One side has no completed trips while the other does. Expect "warming up…" and `-` in the cells, never a 100% or infinite lead. Test owner: Task 4.
4. Speed at 64× with both sides. Expect the step cap to hold for both, so the frame stays responsive and both sides are equal. Test owner: Task 3, `capLimitsStepsPerFrame`.
5. An external side dies mid-duel. Expect its status line to change, while the other side keeps running and closing still works. Covered by the existing `ExternalController` behaviour. `destroy()` closes each side in its own try (Task 7).

---

### Task 1: `--duel` option

**Files:**
- Modify: `src/main/java/simplifiedsimulator/SimulatorConfig.java`
- Test: `src/test/java/simplifiedsimulator/SimulatorConfigTest.java`

**Interfaces:**
- Produces:
  - `List<String> SimulatorConfig.duel()`: empty when there is no duel, otherwise two lower-case names
  - `boolean isDuel()`
  - `int portFor(int side)`: side 0 or 1, per the spec's port rule
  - `String controllerFor(int side)`
- New record component `duel` (last position). `defaults()` passes `List.of()`.

- [ ] Step 1: Add the tests:

```java
@Test
void parsesADuel() {
    SimulatorConfig c = SimulatorConfig.parse("--duel", "Actuated,FIXED");
    assertEquals(List.of("actuated", "fixed"), c.duel());
    assertTrue(c.isDuel());
    assertEquals(RenderQuality.MEDIUM, c.quality());
}

@Test
void explicitQualityWinsInADuel() {
    assertEquals(RenderQuality.HIGH, SimulatorConfig.parse("--duel", "actuated,fixed", "--quality", "high").quality());
    assertEquals(RenderQuality.HIGH, SimulatorConfig.parse("--quality", "high", "--duel", "actuated,fixed").quality());
}

@Test
void twoExternalControllersGetNeighbouringPorts() {
    SimulatorConfig c = SimulatorConfig.parse("--duel", "external,external", "--port", "4000");
    assertEquals(4000, c.portFor(0));
    assertEquals(4001, c.portFor(1));
    SimulatorConfig one = SimulatorConfig.parse("--duel", "actuated,external", "--port", "4000");
    assertEquals(4000, one.portFor(1));
}

@Test
void rejectsInvalidDuels() {
    for (String[] args : List.of(
            new String[] {"--duel", "actuated"},
            new String[] {"--duel", "actuated,fixed,external"},
            new String[] {"--duel", "actuated,smart"},
            new String[] {"--duel", "actuated,fixed", "--headless", "--duration", "60"},
            new String[] {"--duel", "actuated,fixed", "--controller", "fixed"},
            new String[] {"--duel", "external,external", "--port", "65535"})) {
        assertThrows(IllegalArgumentException.class, () -> SimulatorConfig.parse(args), String.join(" ", args));
    }
}
```

- [ ] Step 2: Run `./gradlew test --tests '*SimulatorConfigTest'` and expect a compile FAIL.
- [ ] Step 3: Implement:
  - add the `duel` component and validate it in the compact constructor (size 2, known names, not headless, and `port + 1 <= 65535` when both are external)
  - in `parse`, add `--duel` (split on `,`, trim, lower-case) and track `qualityGiven` and `controllerGiven`
  - when `duel` is set and `!qualityGiven`, use `MEDIUM`; when `controllerGiven` is set together with `duel`, throw
  - add `isDuel()`, `controllerFor(side)` and `portFor(side)`, where `portFor(1)` is `port + 1` only if both sides are external
  - add `--duel <a>,<b>` to `USAGE` under Simulation
  - fix the existing callers of the canonical constructor, `defaults()` and `parse`
- [ ] Step 4: Run `./gradlew test` and expect PASS.
- [ ] Step 5: Commit `feat: parse the --duel option`.

### Task 2: `Simulation.arrivals()` and same-traffic test

**Files:**
- Modify: `src/main/java/simplifiedsimulator/sim/Simulation.java` (the spawn callback)
- Test: `src/test/java/simplifiedsimulator/sim/SimulationTest.java`

**Interfaces:** Produces `long Simulation.arrivals()`: the number of road users generated so far, including those still waiting to enter.

- [ ] Step 1: Add the test:

```java
@Test
void sameSeedGivesTheSameArrivalsWhateverTheController() {
    Simulation a = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(3));
    Simulation b = new Simulation(NETWORK, SimulationOptions.defaults().withSeed(3));
    a.setController(SignalControllers.create("actuated", "localhost", 1));
    b.setController(SignalControllers.create("fixed", "localhost", 1));
    a.run(900);
    b.run(900);
    assertTrue(a.arrivals() > 100);
    assertEquals(a.arrivals(), b.arrivals());
}
```

- [ ] Step 2: Run it and expect FAIL (no `arrivals()`).
- [ ] Step 3: Add `private long arrivals;`, increment it in the `generator.generate` callback, and add the public getter.
- [ ] Step 4: Run `./gradlew test` and expect PASS.
- [ ] Step 5: Commit `feat: count arrivals in the simulation`.

### Task 3: `StepClock`, shared by both windows

**Files:**
- Create: `src/main/java/simplifiedsimulator/StepClock.java`
- Modify: `src/main/java/simplifiedsimulator/SimplifiedSimulator.java` (replace `speed`/`paused`/`accumulator` and the `MIN/MAX` constants)
- Test: `src/test/java/simplifiedsimulator/StepClockTest.java`

**Interfaces:**

```java
final class StepClock {
    static final double MIN_SPEED = 0.25, MAX_SPEED = 64, MAX_STEPS_PER_FRAME = 400;
    StepClock(double timeStep, double speed);
    /** Adds a frame of tpf seconds and returns how many simulation steps to take now. */
    int advance(float tpf);
    float alpha();                 // fraction of a step left over, 0..1
    double speed(); void setSpeed(double v);  // clamped to [MIN_SPEED, MAX_SPEED]
    boolean paused(); void setPaused(boolean p); void togglePause();
}
```

- [ ] Step 1: Write the tests:
  - `advance(0.1f)` at speed 1 with dt 0.1 → 1 step
  - at speed 4 → 4 steps
  - paused → 0 steps
  - `capLimitsStepsPerFrame`: speed 64 and `advance(30f)` → 400 steps, and the next `advance(0)` → 0
  - `setSpeed(1000)` → 64 and `setSpeed(0.01)` → 0.25
  - `alpha()` stays in [0, 1)
- [ ] Step 2: Run it and expect FAIL.
- [ ] Step 3: Implement it, moving the loop from `SimplifiedSimulator.simpleUpdate`. The cap keeps today's behaviour: the accumulator is clamped to `MAX_STEPS_PER_FRAME * dt`.
- [ ] Step 4: Refactor `SimplifiedSimulator` to use it, keeping the behaviour identical. Run `./gradlew test` and expect PASS.
- [ ] Step 5: Commit `refactor: move the simulation step clock into StepClock`.

### Task 4: `DuelScore`

**Files:**
- Create: `src/main/java/simplifiedsimulator/ui/DuelScore.java`
- Test: `src/test/java/simplifiedsimulator/ui/DuelScoreTest.java`

**Interfaces:**

```java
public final class DuelScore {
    public record Side(String name, Summary summary, int queueNow, double throughputPerHour) {}
    public enum Better { A, B, NONE }
    public record Row(String label, String a, String b, Better better) {}
    static final double NECK_AND_NECK = 0.02;
    public static List<Row> rows(Side a, Side b);
    public static String headline(Side a, Side b);
    /** "0 red-light runs, 0 conflicting greens, 0 overlaps"; unsafe(s) true when any is above 0. */
    public static String safety(Summary s);
    public static boolean unsafe(Summary s);
}
```

Rows, in order:
- "Vehicle delay" (`motorDelay`, "%.0f s", lower is better)
- "Stops per vehicle" (`motorStops`, "%.2f", lower)
- "Bus delay", "Cyclist delay", "Pedestrian delay" ("%.0f s", lower)
- "Vehicles through" (`motorCompleted`, "%d", higher)
- "Queue now" (`queueNow`, "%d", lower)

A NaN value becomes "-", and that row's `better` is `NONE`. Equal values give `NONE`.

`headline`:
- if either side's `motorCompleted == 0` or its delay is NaN → "warming up…"
- else, with d = |dA − dB| / max(dA, dB): if d < 0.02 → "neck and neck"
- otherwise "<winner> leads: N% less vehicle delay", with N = round(100·d)

- [ ] Step 1: Write the tests:
  - leader on delay (A 40 s, B 80 s → A, "50% less")
  - leader on "Vehicles through" (higher wins)
  - NaN → "-" and `NONE`
  - one side with 0 completed → "warming up…"
  - 1% difference → "neck and neck"
  - `unsafe` for each safety counter
- [ ] Step 2: Run it and expect FAIL.
- [ ] Step 3: Implement it.
- [ ] Step 4: Run it and expect PASS.
- [ ] Step 5: Commit `feat: add duel scoring`.

### Task 5: `DuelExport`

**Files:**
- Create: `src/main/java/simplifiedsimulator/ui/DuelExport.java`
- Test: `src/test/java/simplifiedsimulator/ui/DuelExportTest.java`

**Interfaces:** `public static String merge(String nameA, String csvA, String nameB, String csvB)`:
- returns the header `side,controller,<original header>`
- then each data row prefixed with `A,"<nameA>",` or `B,"<nameB>",`
- blank lines are skipped
- the inputs come from `Statistics.writeMovementsCsv`

- [ ] Steps 1-5: TDD as above. The tests check: a single header, the row count = rowsA + rowsB, the prefixes, and that empty input gives just the header. Commit `feat: merge duel statistics into one CSV`.

### Task 6: Renderer targets, rig aspect and picking flag

**Files:**
- Create: `src/main/java/simplifiedsimulator/render/RenderTarget.java`
- Modify: `src/main/java/simplifiedsimulator/render/CrossingRenderer.java`, `src/main/java/simplifiedsimulator/render/CameraRig.java`
- Test: `src/test/java/simplifiedsimulator/render/CameraRigTest.java`

**Interfaces:**

```java
/** Where a CrossingRenderer draws: the parent node, the view port, and whether it owns the camera rig. */
public record RenderTarget(Node parent, ViewPort viewPort, boolean cameraRig) {}
// CrossingRenderer
public CrossingRenderer(Network network, RenderQuality quality);                       // main root + view port + rig (unchanged)
public CrossingRenderer(Network network, RenderQuality quality, RenderTarget target);  // target == null means the default
// CameraRig
static float aspect(Camera cam);   // width·(right−left) / height·(top−bottom)
public void setPicking(boolean enabled);
```

- `CrossingRenderer.initialize`:
  - uses `target.parent()` instead of `getRootNode()` and `target.viewPort()` for the filters and cleanup
  - creates the rig only when `target.cameraRig()` is set; `cameraRig()` may then return null
  - `agents.update` uses `target.viewPort().getCamera().getLocation()`
- `CameraRig.initialize` uses `aspect(cam)`.
- `click()` returns early when picking is disabled.

- [ ] Step 1: Test `aspectUsesTheViewPortWidth`: `new Camera(1600, 900)`, then `setViewPort(0, 0.5f, 0, 1)` → 800/900; full view port → 1600/900.
- [ ] Step 2: Run it and expect FAIL.
- [ ] Step 3: Implement it.
- [ ] Step 4: Run `./gradlew test` and expect PASS. Check the single view still renders: take a `--screenshot` and look at it.
- [ ] Step 5: Commit `refactor: let the crossing renderer draw into any view port`.

### Task 7: Duel window, views, HUD and start-up

**Files:**
- Create:
  - `src/main/java/simplifiedsimulator/DuelSimulator.java` (`SimpleApplication`: keys, clock, both simulations, export, destroy)
  - `src/main/java/simplifiedsimulator/render/DuelViews.java` (AppState: side B logical/geometric update and camera copy)
  - `src/main/java/simplifiedsimulator/ui/DuelHud.java` (AppState: status, scoreboard, side labels, divider, flash, help)
- Modify:
  - `src/main/java/simplifiedsimulator/Main.java`: `createSimulation(config, controller, port, warmUp)`; when `config.isDuel()`, build both sides with warm-up only if neither is external, then start `DuelSimulator`; the screenshot path runs both sides for `--duration`
  - `src/main/java/simplifiedsimulator/ui/HudText.java`: `HELP_DUEL`

**Interfaces:**
- Consumes:
  - `SimulatorConfig.duel/portFor/controllerFor` (Task 1)
  - `StepClock` (Task 3)
  - `DuelScore`, `DuelExport` (Tasks 4-5)
  - `RenderTarget`, `CameraRig.setPicking` (Task 6)
- `DuelViews(Node rootB, Camera camA, Camera camB)`:
  - `update(tpf)` → `rootB.updateLogicalState(tpf)`
  - `render(rm)` → `rootB.updateGeometricState()`; camB copies camA's location, rotation and frustum (`setFrustum(near, far, left, right, top, bottom)` plus `setParallelProjection(false)`)
- `DuelHud(Supplier<Simulation> a, Supplier<Simulation> b, Supplier<AppStatus> status)`:
  - `flash(String)`, `toggleHelp()`
  - refreshes every 0.5 s using `DuelScore`

Window set-up in `DuelSimulator.simpleInitApp`:
1. `cam.setViewPort(0, 0.5f, 0, 1)`.
2. `camB = cam.clone()`, then `camB.setViewPort(0.5f, 1, 0, 1)`.
3. `vpB = renderManager.createMainView("duel B", camB)`, `vpB.setClearFlags(true, true, true)`, `vpB.attachScene(rootB)`.
4. Attach `rendererA` with the default target and `rendererB` with `new RenderTarget(rootB, vpB, false)`.
5. On frame 2, apply the camera preset, call `setPicking(false)` and refresh the frustum aspect for the half view.
6. Attach `DuelViews`, then `DuelHud`.

Per frame: `n = clock.advance(tpf)`, then `for i < n: a.step(); b.step();`, then `rendererA.update(a, clock.alpha())` and `rendererB.update(b, clock.alpha())`.

- [ ] Step 1: Implement the classes. These are graphics classes; their logic is covered by Tasks 1-6.
- [ ] Step 2: Run `./gradlew test` and expect PASS.
- [ ] Step 3: Visual check: `./gradlew run --args="--duel actuated,fixed --start 13:00 --screenshot <scratch>/duel.png --duration 300"`. Look at the image: two halves, the same viewpoint, different traffic, the scoreboard at the top centre, and nothing stretched.
- [ ] Step 4: Run a live window briefly with `--duel actuated,external` against `tools/controller/controller.py`. Check that the status shows "connected" on side B.
- [ ] Step 5: Commit `feat: add the controller duel window`.

### Task 8: Docs and release

**Files:** `README.md` (a Running/Options row and a short "Controller duel" section), `CHANGELOG.md` (1.2.0), `build.gradle.kts` (version 1.2.0).

- [ ] Steps:
  - update the docs
  - run `./gradlew build` and `python -m unittest discover -s tools/controller` and expect both to pass
  - commit `feat: add controller duel - v1.2.0`
  - push and open a PR
