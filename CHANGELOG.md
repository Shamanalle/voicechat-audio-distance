# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-09-25

### Added
- **New settings screen** with four tabs: *Distance* (live curve, whisper curve, hover readout in blocks / % / dB, dots for the people you hear right now), *Walls* (preview of how a voice sounds behind glass, wood, stone, wool and leaves), *Materials* (per-material absorption) and *Monitor* (live distance, loudness and wall loss of every voice you hear).
- Active tab and matching preset are highlighted; long texts are shortened to fit; the layout works down to the smallest GUI size (320×240).
- Per-material wall weights (stone, wood, wool, glass, doors, leaves, bars & fences, liquids), saved in the config.
- The "Voice distance & walls…" button in Simple Voice Chat's settings now also exists on 26.x.
- Sound Physics Remastered detection: our wall muffling stands down so voices are not muffled twice.
- Tests for the filter, the occlusion model, config round-trips/migration, the speaker registry and translation consistency (40 tests).

### Changed
- **Audible wall muffling.** The old 1-pole filter changed a voice by less than 1 dB behind a stone wall. It is replaced by a 4th-order TPT state-variable low-pass plus broadband transmission loss: about −8 dB and ~2.5 kHz for one stone wall at default strength, about −18 dB and ~600 Hz for three.
- Parameters glide with a ~90 ms time constant, and entering or leaving the bypass is crossfaded, so walls no longer cause clicks.
- Rays only count blocks whose real collision shape they cross (slabs, open doors, fences, carpets), and 5 parallel rays give soft edges at corners and doorways.
- Occlusion is computed on the client thread each tick; audio threads only read the result, so the world is never touched off-thread.
- Whisper detection uses Simple Voice Chat's own whisper flag instead of guessing from the distance.
- The plugin, config, DSP and occlusion model now live in `common`, shared by every version. The client code is shared across all versions except a small per-version adapter.
- Esc on the settings screen now saves, like vanilla option screens; *Cancel* restores everything.
- Config is written atomically, validated on load and migrated from 1.1.x automatically.
- Versions are defined once in `gradle.properties`.

### Fixed
- **Simple Voice Chat dependency.** SVC versions look like `1.21.8-2.6.24` (and `2.6.24+26.3` on 26.x), so the old `>=2.4.0` / `[2.6.0,)` constraints could not match on 1.20/1.21. Constraints now use the correct format per branch.
- **1.21.x jar on other 1.21 releases:**
  - `GuiGraphics.drawString` changed its return type in 1.21.6, which crashed the screen on 1.21–1.21.5.
  - The `KeyMapping` constructor changed in 1.21.9 and 1.21.11, which crashed start-up.
  - `Camera.getPosition` was removed in 1.21.11, which silently disabled walls.
  - `Entity.position` was removed in 1.21.9.

  Every Minecraft member the jar uses has been checked against the mappings of 1.21 through 1.21.11.
- 26.x: 15 missing translation keys showed up as raw keys. Glass, leaves and doors were never counted as walls.
- The filter bypass never re-engaged after the first wall, so the voice stayed slightly filtered.
- Occlusion caches were never cleared; they are now reset when the world or server changes.
- The release workflow used JDK 21, which cannot build the 26.x module.
- `fabric.mod.json` and `mods.toml` disagreed on the supported 26.x versions.

### Notes
- Forge / NeoForge jars are a lite build: distance curves configured through `config/vc-audio-distance.properties`. The settings screen, walls and monitor are Fabric-only.

## [1.1.0] - 2026-09-25

### Added
- **Multi-Loader & Multi-Version Standardized Release Architecture**:
  - **Fabric / Quilt 1.20.1**: `voicechat-audio-distance-fabric-1.1.0+mc1.20.1.jar` (Minecraft 1.20.1, Java 17, `HalfTransparentBlock` legacy compatibility, 1.20.1 screen rendering).
  - **Forge 1.20.1**: `voicechat-audio-distance-forge-1.1.0+mc1.20.1.jar` (Minecraft 1.20.1, Java 17, includes `META-INF/mods.toml` and `@ForgeVoicechatPlugin`).
  - **Fabric / Quilt 1.21.x**: `voicechat-audio-distance-fabric-1.1.0+mc1.21.x.jar` (Minecraft 1.21 – 1.21.11).
  - **NeoForge 1.21.x**: `voicechat-audio-distance-neoforge-1.1.0+mc1.21.x.jar` (includes `META-INF/neoforge.mods.toml` and `@ForgeVoicechatPlugin`, Minecraft 1.21 – 1.21.11).
  - **Forge 1.21.x**: `voicechat-audio-distance-forge-1.1.0+mc1.21.x.jar` (includes `META-INF/mods.toml` and `@ForgeVoicechatPlugin`, Minecraft 1.21 – 1.21.11).
  - **Fabric 26.x**: `voicechat-audio-distance-fabric-1.1.0+mc26.x.jar` (Minecraft 26.1 – 26.3+, Java 25, unobfuscated Loom, SDL3 input).
  - **NeoForge 26.x**: `voicechat-audio-distance-neoforge-1.1.0+mc26.x.jar` (includes `META-INF/neoforge.mods.toml` and `@ForgeVoicechatPlugin`).
  - **Forge 26.x**: `voicechat-audio-distance-forge-1.1.0+mc26.x.jar` (includes `META-INF/mods.toml` and `@ForgeVoicechatPlugin`).
  - Standardized all release artifact names to `[project]-[loader]-[version]+mc[target].jar`.
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
