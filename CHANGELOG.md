# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-25

### Added
- **Dynamic 3D Sound Attenuation Models**: Choose between `Linear (Vanilla)`, `Realistic Inverse (1/r)`, and `Exponential (Stealth)`.
- **Hardware Volume Floor (`AL_MIN_GAIN`)**: OpenAL hardware clamping ensuring voices remain softly audible at boundary distance without PCM distortion.
- **Whisper Decay Multiplier Slider**: Configurable in-GUI slider (`0.50x – 2.00x`) to fine-tune how whisper audio carries across distances.
- **Real-Time Acoustic Audibility Graph**: Interactive visualizer showing exact mathematical volume curve with hover inspector (distance in blocks and volume %).
- **Instant Audio Presets**: 1-click profiles for `Vanilla`, `Realistic`, `Audible`, and `Stealth`.
- **Integrations**:
  - Keybinding in Minecraft controls menu (`GLFW_KEY_UNKNOWN` default, user-assignable).
  - Mod Menu integration (`ModMenuApi` configuration screen).
  - Simple Voice Chat settings screen button injection with responsive vertical placement.
- **Bilingual Localization**: Complete English (`en_us`) and Russian (`ru_ru`) translations.
- **Automated Test Suite**: JUnit 5 unit tests for acoustic attenuation equations, clamped bounds, and preset validity.
- **CI/CD Pipeline**: GitHub Actions workflows for continuous integration testing and automated release publishing.

### Changed
- Switched OpenAL distance models to standard `AL_LINEAR_DISTANCE_CLAMPED`, `AL_INVERSE_DISTANCE_CLAMPED`, and `AL_EXPONENT_DISTANCE_CLAMPED` to guarantee hearing safety and prevent volume runaway at close proximity.
- Configuration fields in `DistanceConfig` are now declared `volatile` for thread safety between rendering and OpenAL audio threads.
- Configuration file location is resolved dynamically through `FabricLoader.getInstance().getConfigDir()`.
- Responsive layout calculations (`startY = Math.max(8, ...)`) to ensure full visibility on small resolutions and high GUI scale.

### Fixed
- Fixed an issue where `AL_MIN_GAIN` bypassed player muting and master volume settings. Minimum gain is now scaled relative to source `AL_GAIN`.
- Fixed discrepancy in the Exponential formula between GUI curve preview and OpenAL audio processing.
- Fixed whisper detection heuristic to scale dynamically with the server's configured maximum voice chat distance.
- Fixed curve hover inspector badge overlapping the screen title.
- Fixed black background rendering when opening settings from the Minecraft main menu.
