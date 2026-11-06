# Changelog

All notable changes to this project are documented here. The project follows
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

## [1.4.2] - 2026-11-07

### Fixed
- With `--once` a Python controller stopped when another program on the PC briefly knocked on
  its port (Razer's app engine checks port 1337 about once a minute), so the duel's left side
  was not connected. Connections that close within a second no longer count as the simulator.

## [1.4.1] - 2026-11-07

### Added
- One click for the showdown: `./gradlew duel` (and the IntelliJ run configuration
  **Controller Duel**) starts the 2014 and smart controllers and opens the duel at the
  morning rush in daylight; `-PduelArgs` passes other options.
- `--once` for the Python controllers: stop when the simulator disconnects.

## [1.4.0] - 2026-11-06

### Added
- `tools/controller/original2014.py`: our original 2014 C# controller, ported faithfully to
  Python with today's group numbers and conflicts.
- `tools/controller/smart.py`: the fastest controller we could make. It builds on the
  built-in vehicle-actuated logic and adds a variable minimum green, rest in green, the
  longest-waiting module going next, higher maximum greens and a sharper bus priority. Over
  three seeds of morning and evening rush it has about 25-30% less vehicle delay than the
  built-in controller; at 200% traffic it moves 20% more vehicles.
- Protocol, all optional and backwards compatible: controllers can announce their name
  (shown above the duel scoreboard columns) and ask for `sensorfree` messages when a loop
  becomes free.
- Lockstep test mode and `./gradlew showdown` to measure a controller deterministically,
  in about half a minute per simulated hour, including per-movement results.
- `tools/controller/shared.py` holds the conflict table, protocol and server for all Python
  controllers.

### Changed
- Simulator and Python controllers send their messages without TCP batching, so light changes
  take effect sooner.

### Fixed
- Buses no longer starve other directions in the smart controller when they come often (the
  built-in controller still has this weakness, which only shows with frequent buses).

## [1.3.0] - 2026-11-06

### Added
- Situations on the function keys, in the single view and in a duel: `F5` morning rush
  (07:30, 100% traffic), `F6` evening rush (17:00, 110%) and `F7` stress test (08:00, 200%).
  The clock and traffic level jump there, the road users on the map stay, and the statistics
  (and the duel score) start again.

## [1.2.1] - 2026-11-06

### Fixed
- In a duel window the two halves could show different spots: jME's post-processor draws a
  multisampled half-window view port in the wrong place. A duel now uses FXAA instead of
  multisampling.
- The duel scoreboard got as wide as the controller names, and with two external controllers
  the names no longer lined up with their columns. The columns are now headed left and right,
  identical controllers are called Left and Right, and the divider stops below the scoreboard.

### Added
- `--names <a>,<b>` labels the two sides of a duel.

## [1.2.0] - 2026-11-06

### Added
- Controller duel: `--duel <a>,<b>` shows two controllers side by side on the same
  traffic, with cameras locked together and a live scoreboard (vehicle delay, stops, bus,
  cyclist and pedestrian delay, vehicles through, queue, safety). Any two of `actuated`,
  `fixed` and `external`; two external controllers use `--port` and `--port` + 1.
- `X` in a duel saves both sides' statistics in one CSV with a `side` column.

### Changed
- The camera's aspect ratio follows its view port, and the renderer can draw into any
  view port (needed for the split screen; the single view is unchanged).

### Fixed
- With an external controller the clock started 3 minutes before `--start`, because the
  warm-up offset was applied although an external controller gets no warm-up.

## [1.1.0] - 2026-11-06

### Added
- Ready-to-run external traffic light controller in Python (`tools/controller/controller.py`,
  standard library only): gives green on demand, extends it while traffic keeps coming,
  skips empty phases and checks its plan against the conflict table before it starts.
  `--fixed` runs a fixed cycle, `--speed` matches a sped-up simulation.

## [1.0.1] - 2026-11-06

### Fixed
- Mouse wheel zoom is now an even step of about 15% per notch in both directions. Before,
  one notch in jumped from the overview almost to the ground while one notch out only
  doubled the distance, and a fast flick could give a negative distance.
- In free flight (`F`) one wheel notch now moves about 10 m instead of 72 m.

## [1.0.0] - 2026-11-06

### Added
- First release: 3D simulation of the Harlingerstraatweg crossing with vehicle-actuated,
  fixed-time and external (TCP) signal control, statistics and a batch runner.
