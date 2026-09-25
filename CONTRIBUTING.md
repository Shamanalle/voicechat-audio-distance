# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25** — it builds every module (1.20 and 1.21 are compiled with `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test             # fast: audio, server, config, translations
./gradlew build                    # every target, jars in build/libs/
./gradlew :fabric-1.21:runClient   # or :fabric-1.20 / :fabric-26; runServer for the server side
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (Java 17, no Minecraft classes) | Simple Voice Chat plugin, OpenAL curve, config and presets, wall filter (`VoiceFilter`), occlusion model, speaker registry, server walls (`ServerWalls`), server settings, client–server protocol (`LinkProtocol`, `ServerLink`), **translations and icon** |
| `shared/mc-all/` | all three Fabric modules | settings screen (`SettingsScreen`), `Canvas`, slider, client tick logic, multi-ray tracer |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.21` | client and common entrypoints, screen and canvas adapters, block acoustics, client world access, server wall measuring, key mapping |
| `fabric-1.20/`, `fabric-1.21/` | own module | `Compat` (the only screen difference between 1.20 and 1.21), networking (channels on 1.20.1, payloads on 1.21), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | own module | 26.x adapters (render-state API, SDL input, payload names), block acoustics, server wall measuring, metadata |

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test. `ServerWallsTest` shows how to drive Simple Voice Chat events with fake objects.
- The screen draws only through `Canvas`. Minecraft's drawing API differs between versions: `drawString` changed its return type in 1.21.6, and 26.x uses a render-state API.
- The 1.21 module is compiled against 1.21.8 but runs on 1.21–1.21.11. Before using a new Minecraft method there, check that its signature is the same on every 1.21.x release (for example with the Yarn mappings of each version). Otherwise use one that is, or reflection as in `KeyMappings`. Examples that are **not** stable: `Entity.level()`, `Entity.position()`, `Camera.getPosition()`, `ServerLevel.getEntity(UUID)`.
- Never touch the world from audio threads. On the client the tick fills `SpeakerRegistry`; on the server the tick measures walls for `ServerWalls`. The audio side only reads.
- The server side must never break voice chat: every failure in `ServerWalls` falls back to the original packet.

### Translations

Language files are in `common/src/main/resources/assets/vc-audio-distance/lang/`. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ.

### Documentation

All documentation (README, CHANGELOG, this file, issue and pull request templates) is written in English first, followed by a complete Russian translation.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft, loader and Simple Voice Chat versions; client, server or both).
4. Add an entry to `CHANGELOG.md` under an *Unreleased* section, in English and in Russian.

---

## Русский

### Подготовка

- **JDK 25** — им собираются все модули (1.20 и 1.21 компилируются с `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test             # быстро: звук, сервер, конфиг, переводы
./gradlew build                    # все версии, JAR в build/libs/
./gradlew :fabric-1.21:runClient   # или :fabric-1.20 / :fabric-26; runServer для серверной части
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все JAR (Java 17, без классов Minecraft) | плагин Simple Voice Chat, кривая OpenAL, конфиг и пресеты, фильтр стен (`VoiceFilter`), модель приглушения, реестр говорящих, стены на сервере (`ServerWalls`), настройки сервера, протокол клиент–сервер (`LinkProtocol`, `ServerLink`), **переводы и иконка** |
| `shared/mc-all/` | во все три модуля Fabric | экран настроек (`SettingsScreen`), `Canvas`, слайдер, логика тика клиента, трассировка несколькими лучами |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.21` | точки входа клиента и общая, адаптеры экрана и отрисовки, акустика блоков, доступ к миру на клиенте, измерение стен на сервере, регистрация клавиши |
| `fabric-1.20/`, `fabric-1.21/` | свой модуль | `Compat` (единственное отличие экрана 1.20 от 1.21), сеть (каналы в 1.20.1, payload в 1.21), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | свой модуль | адаптеры 26.x (API отрисовки через render state, ввод SDL, имена payload), акустика блоков, измерение стен на сервере, метаданные |

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом. `ServerWallsTest` показывает, как проверять события Simple Voice Chat на поддельных объектах.
- Экран рисует только через `Canvas`. API отрисовки Minecraft различается между версиями: в 1.21.6 `drawString` поменял тип возврата, а в 26.x используется API render state.
- Модуль 1.21 собирается против 1.21.8, но работает на 1.21–1.21.11. Прежде чем использовать там новый метод Minecraft, проверьте, что его сигнатура одинакова во всех 1.21.x (например, по маппингам Yarn каждой версии). Если нет — возьмите стабильный метод или рефлексию, как в `KeyMappings`. Примеры **нестабильных** методов: `Entity.level()`, `Entity.position()`, `Camera.getPosition()`, `ServerLevel.getEntity(UUID)`.
- Из аудиопотоков мир не трогаем. На клиенте тик заполняет `SpeakerRegistry`, на сервере тик измеряет стены для `ServerWalls`. Аудиочасть только читает.
- Серверная часть не должна ломать голосовой чат: при любой ошибке в `ServerWalls` уходит исходный пакет.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/vc-audio-distance/lang/`. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры.

### Документация

Вся документация (README, CHANGELOG, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем идёт полный перевод на русский.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft, загрузчика и Simple Voice Chat; клиент, сервер или оба).
4. Добавьте запись в `CHANGELOG.md` в раздел *Unreleased* на английском и на русском.
