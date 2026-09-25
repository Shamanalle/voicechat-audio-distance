# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-09-25

### Added
- **Multi-Version Dual-Target Support**:
  - **Minecraft 1.21.x (`1.21` – `1.21.11`)**: Target file `voicechat-audio-distance-addon-1.1.0+mc1.21.x.jar` (Java 21).
  - **Minecraft 26.x (`26.1` – `26.3`)**: Target file `voicechat-audio-distance-addon-1.1.0+mc26.3.jar` (Java 25, unobfuscated Loom, SDL3 input, and `GuiGraphicsExtractor` rendering).
- **Sound Occlusion & Acoustic Muffling**: Physical sound absorption through solid obstacles (walls, doors, floors, caves). Voices are muffled via real-time digital low-pass filtering when behind barriers.
- **DSP Low-Pass Filter Engine (`OcclusionFilter`)**:
  - High-performance 1-pole IIR filter: `y[n] = y[n-1] + α · (x[n] - y[n-1])`.
  - Dynamic exponential frequency sweep from 18,000 Hz down to 500 Hz depending on barrier density and thickness.
  - Per-stream state tracking maintaining continuous audio between 20ms frames, eliminating pops and clicks.
  - Smooth parameter interpolation ensuring natural acoustic transitions when walking around corners.
  - Zero-allocation in-place processing for zero garbage collection overhead.
- **3D Voxel Raycasting Line-of-Sight (`RaycastOcclusion`)**:
  - Uses Minecraft's high-speed internal voxel traversal (`BlockGetter.traverseBlocks`).
  - Differentiated physical absorption by material (wool/carpet = soundproofing 0.45, thick stone = 0.35, doors/wood = 0.25, glass/iron bars = 0.15 - 0.18, foliage = 0.10, fluids/water = 0.20).
  - High-performance 40ms caching per entity keeping CPU usage under 0.01%.
  - Fully defensive exception handling protecting audio threads from dimension changes and world unloads.
- **GUI Controls & Presets**:
  - In-screen toggle button: `Wall Occlusion: ON / OFF`.
  - In-screen slider: `Muffling: 0% – 100%`.
  - Presets updated with tailored occlusion profiles (`Realistic`: 65% ON, `Stealth`: 85% ON, `Vanilla`/`Audible`: OFF).
- **Unit Test Suite Expansion**:
  - Added `OcclusionFilterTest` (11 new tests) validating frequency response (bass pass vs treble drop), frame continuity, full-scale square wave overflow safety, and configuration presets.

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
