# Changelog

All notable changes to this project are documented here. The project follows
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

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
