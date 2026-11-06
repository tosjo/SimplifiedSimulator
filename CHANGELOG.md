# Changelog

All notable changes to this project are documented here. The project follows
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

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
