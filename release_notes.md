## 🧱 Full Multi-Loader, Multi-Version & Acoustic Sound Occlusion Release (v1.1.0)

### 📦 Download Files / Файлы для загрузки

Все файлы имеют единый унифицированный формат: `voicechat-audio-distance-[loader]-[версия]+mc[версия_игры].jar`

| Загрузчик (Loader) | Версия игры | Файл аддона (Download File) | Требования |
|---|---|---|---|
| **Fabric / Quilt** | **Minecraft 1.20.1** | `voicechat-audio-distance-fabric-1.1.0+mc1.20.1.jar` | Fabric Loader >=0.14.21, Java 17+, Simple Voice Chat >=2.4.0 |
| **Forge** | **Minecraft 1.20.1** | `voicechat-audio-distance-forge-1.1.0+mc1.20.1.jar` | Minecraft Forge >=47.0, Java 17+, Simple Voice Chat >=2.4.0 |
| **Fabric / Quilt** | **Minecraft 1.21.x** (`1.21` – `1.21.11`) | `voicechat-audio-distance-fabric-1.1.0+mc1.21.x.jar` | Fabric Loader >=0.16.0, Java 21+, Simple Voice Chat >=2.4.0 |
| **NeoForge** | **Minecraft 1.21.x** (`1.21` – `1.21.11`) | `voicechat-audio-distance-neoforge-1.1.0+mc1.21.x.jar` | NeoForge >=21.0, Java 21+, Simple Voice Chat >=2.4.0 |
| **Forge** | **Minecraft 1.21.x** (`1.21` – `1.21.11`) | `voicechat-audio-distance-forge-1.1.0+mc1.21.x.jar` | Minecraft Forge >=51.0, Java 21+, Simple Voice Chat >=2.4.0 |
| **Fabric** | **Minecraft 26.x** (`26.1` – `26.3+`) | `voicechat-audio-distance-fabric-1.1.0+mc26.x.jar` | Fabric Loader >=0.19.3, Java 25+, Simple Voice Chat >=2.6.0 |
| **NeoForge** | **Minecraft 26.x** (`26.1` – `26.3+`) | `voicechat-audio-distance-neoforge-1.1.0+mc26.x.jar` | NeoForge >=26.0, Java 25+, Simple Voice Chat >=2.6.0 |
| **Forge** | **Minecraft 26.x** (`26.1` – `26.3+`) | `voicechat-audio-distance-forge-1.1.0+mc26.x.jar` | Minecraft Forge >=55.0, Java 25+, Simple Voice Chat >=2.6.0 |

---

### 🌟 New Features & Enhancements

- **Complete Multi-Loader Coverage (Fabric, NeoForge, Forge) & Backports**:
  - Unified naming schema across all loaders and game versions.
  - Native support for **1.20.1** on **Fabric** and **Forge** (Java 17, `mods.toml`, `fabric.mod.json`, `@ForgeVoicechatPlugin`, legacy screen and block API mappings).
  - Native support for **NeoForge** and **Forge** across both **1.21.x** (`1.21` – `1.21.11`) and **26.x** (`26.1` – `26.3+`) via `neoforge.mods.toml`, `mods.toml`, and `@ForgeVoicechatPlugin` annotation.
  - Native support for **Fabric / Quilt** on Minecraft 1.20.1 (Java 17), 1.21.x (Java 21) and Minecraft 26.x (Java 25).
- **Physical Sound Occlusion & Acoustic Muffling**: Physical sound absorption through solid obstacles (walls, doors, floors, caves). Voices are muffled via real-time digital low-pass filtering when behind barriers.
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
  - In-screen toggle button: `Wall Occlusion: ON / OFF` (`Стены: ВКЛ / ВЫКЛ`).
  - In-screen slider: `Muffling: 0% – 100%` (`Глубина: 0% – 100%`).
  - Presets updated with tailored occlusion profiles (`Realistic`: 65% ON, `Stealth`: 85% ON, `Vanilla`/`Audible`: OFF).
- **Automated Verification**:
  - Suite of 28 JUnit 5 tests covering audio physics equations, DSP frequency response, continuity, and preset configurations passing at 100%.
