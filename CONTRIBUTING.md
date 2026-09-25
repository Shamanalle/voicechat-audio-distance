# Contributing to VoiceChat Audio Distance

## Setup

- **JDK 25** (it builds every module; 1.20 and 1.21 are compiled with `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test      # fast: audio core, config, translations
./gradlew build             # every target, jars in build/libs/
./gradlew :fabric-1.21:runClient   # or :fabric-1.20 / :fabric-26
```

## Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (Java 17, no Minecraft classes) | SVC plugin, OpenAL curve, config & presets, wall DSP (`VoiceFilter`), occlusion model, speaker registry, **translations and icon** |
| `shared/client-all/` | all three Fabric modules | settings screen (`SettingsScreen`), `Canvas`, slider, tick logic, multi-ray tracer |
| `shared/client-1.20-1.21/` | `fabric-1.20`, `fabric-1.21` | client entrypoint, screen/canvas adapters, world access, key mapping |
| `fabric-1.20/`, `fabric-1.21/` | own module | `Compat` (the only difference between 1.20 and 1.21), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | own module | 26.x adapters (render-state API, SDL input), metadata |

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test.
- The screen draws only through `Canvas`. Minecraft's drawing API differs between versions: `drawString` changed its return type in 1.21.6, and 26.x uses a render-state API.
- The 1.21 module is compiled against 1.21.8 but runs on 1.21 – 1.21.11. Before using a new Minecraft method there, check that its signature is the same on every 1.21.x release (e.g. with the Yarn mappings of each version). Otherwise go through reflection, as `KeyMappings` does.
- Never touch the world from audio threads; the client tick fills `SpeakerRegistry` and the plugin only reads it.

## Translations

Language files are in `common/src/main/resources/assets/vc-audio-distance/lang/`. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if languages have different keys, or if placeholders differ.

## Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft and SVC version).
4. Add a line to `CHANGELOG.md` under an *Unreleased* section.
