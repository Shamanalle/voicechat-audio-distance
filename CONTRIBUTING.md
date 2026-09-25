# Участие в разработке / Contributing

*[English version below](#-english)*

## 🇷🇺 По-русски

### Подготовка

- **JDK 25** — им собираются все модули (1.20 и 1.21 компилируются с `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test             # быстро: звук, конфиг, переводы
./gradlew build                    # все версии, JAR в build/libs/
./gradlew :fabric-1.21:runClient   # или :fabric-1.20 / :fabric-26
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все JAR (Java 17, без классов Minecraft) | плагин SVC, кривая OpenAL, конфиг и пресеты, фильтр стен (`VoiceFilter`), модель приглушения, реестр говорящих, **переводы и иконка** |
| `shared/mc-all/` | во все три модуля Fabric | экран настроек (`SettingsScreen`), `Canvas`, слайдер, логика тика, трассировка несколькими лучами |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.21` | точка входа клиента, адаптеры экрана и отрисовки, доступ к миру, регистрация клавиши |
| `fabric-1.20/`, `fabric-1.21/` | свой модуль | `Compat` (единственное отличие 1.20 от 1.21), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | свой модуль | адаптеры для 26.x (новый API отрисовки, ввод SDL), метаданные |

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом.
- Экран рисует только через `Canvas`: API отрисовки Minecraft различается между версиями (в 1.21.6 `drawString` поменял тип возврата, в 26.x — новый API).
- Модуль 1.21 собирается против 1.21.8, но работает на 1.21–1.21.11. Прежде чем использовать там новый метод Minecraft, проверьте, что его сигнатура одинакова во всех 1.21.x (например, по маппингам Yarn каждой версии). Если нет — используйте рефлексию, как в `KeyMappings`.
- Из аудиопотоков мир не трогаем: тик клиента заполняет `SpeakerRegistry`, плагин только читает.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/vc-audio-distance/lang/`. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft и SVC).
4. Добавьте строку в `CHANGELOG.md` в раздел *Unreleased* на обоих языках.

---

## 🇬🇧 English

### Setup

- **JDK 25** (it builds every module; 1.20 and 1.21 are compiled with `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test      # fast: audio core, config, translations
./gradlew build             # every target, jars in build/libs/
./gradlew :fabric-1.21:runClient   # or :fabric-1.20 / :fabric-26
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (Java 17, no Minecraft classes) | SVC plugin, OpenAL curve, config & presets, wall DSP (`VoiceFilter`), occlusion model, speaker registry, **translations and icon** |
| `shared/mc-all/` | all three Fabric modules | settings screen (`SettingsScreen`), `Canvas`, slider, tick logic, multi-ray tracer |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.21` | client entrypoint, screen/canvas adapters, world access, key mapping |
| `fabric-1.20/`, `fabric-1.21/` | own module | `Compat` (the only difference between 1.20 and 1.21), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | own module | 26.x adapters (render-state API, SDL input), metadata |

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test.
- The screen draws only through `Canvas`. Minecraft's drawing API differs between versions: `drawString` changed its return type in 1.21.6, and 26.x uses a render-state API.
- The 1.21 module is compiled against 1.21.8 but runs on 1.21 – 1.21.11. Before using a new Minecraft method there, check that its signature is the same on every 1.21.x release (e.g. with the Yarn mappings of each version). Otherwise go through reflection, as `KeyMappings` does.
- Never touch the world from audio threads; the client tick fills `SpeakerRegistry` and the plugin only reads it.

### Translations

Language files are in `common/src/main/resources/assets/vc-audio-distance/lang/`. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if languages have different keys, or if placeholders differ.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft and SVC version).
4. Add a line to `CHANGELOG.md` under an *Unreleased* section, in both languages.
