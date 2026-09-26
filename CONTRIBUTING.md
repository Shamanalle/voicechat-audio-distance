# Contributing / Участие в разработке

**[English](#english)** · **[Русский](#русский)**

---

## English

### Setup

- **JDK 25** — it builds every module (1.20 and 1.21 are compiled with `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voice-physics.git
cd voice-physics
./gradlew :common:test             # fast: audio, server, config, translations
./gradlew build                    # every target, jars in build/libs/
./gradlew :fabric-1.21:runClient   # or :fabric-1.20 / :fabric-26; runServer for the server side
./gradlew :bukkit:build            # Paper / Purpur / Spigot / Bukkit plugin only
```

### Where code lives

| Directory | Compiled into | Contains |
|---|---|---|
| `common/` | every jar (Java 17, no Minecraft classes) | Simple Voice Chat plugin, OpenAL curve, config and presets, wall filter (`VoiceFilter`), occlusion model, speaker registry, server walls (`ServerWalls`), server settings, client–server protocol (`LinkProtocol`, `ServerLink`), **translations and icon** |
| `shared/mc-all/` | every Fabric module | settings screen (`SettingsScreen`), `Canvas`, slider, client tick logic, multi-ray tracer |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.20.4`, `fabric-1.20.6`, `fabric-1.21` | client and common entrypoints, screen and canvas adapters, block acoustics, client world access, server wall measuring, key mapping |
| `fabric-1.20/`, `fabric-1.20.4/`, `fabric-1.20.6/`, `fabric-1.21/` | own module (1.20 – 1.20.1, 1.20.2 – 1.20.4, 1.20.5 – 1.20.6, 1.21.x) | `Compat` (screens draw their own background since 1.20.2), networking (channels up to 1.20.4, payloads since 1.20.5), `PlayerLanguage` (since 1.20.2), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | own module | 26.x adapters (render-state API, key and screen differences between 26.1 and 26.3, payload names), block acoustics, server wall measuring, metadata. Checked on every 26.x release by *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) |
| `bukkit/` | own jar (Java 17) | Paper / Purpur / Spigot / Bukkit plugin: server side only. Plugin messaging on the same channels as Fabric, block acoustics through the Bukkit API, `plugin.yml` |

Rules of thumb:
- Logic that does not need Minecraft goes into `common` and gets a unit test. `ServerWallsTest` shows how to drive Simple Voice Chat events with fake objects.
- The screen draws only through `Canvas`. Minecraft's drawing API differs between versions: `drawString` changed its return type in 1.21.6, and 26.x uses a render-state API.
- The 1.21 module is compiled against 1.21.8 but runs on 1.21–1.21.11. Before using a new Minecraft method there, check that its signature is the same on every 1.21.x release (for example with the Yarn mappings of each version). Otherwise use one that is, or reflection as in `KeyMappings`. Examples that are **not** stable: `Entity.level()`, `Entity.position()`, `Camera.getPosition()`, `ServerLevel.getEntity(UUID)`.
- Never touch the world from audio threads. On the client the tick fills `SpeakerRegistry`; on the server the tick measures walls for `ServerWalls`. The audio side only reads.
- The server side must never break voice chat: every failure in `ServerWalls` falls back to the original packet.
- The Bukkit plugin uses only the Bukkit API (no Paper-only or NMS classes), so it runs on Paper, Purpur, Spigot and Bukkit. Ray geometry that has no Bukkit equivalent lives in `common` (`VoxelRay`, `RayBundle`) and is unit-tested.
- Client and server exchange `LinkProtocol` messages as one Minecraft string (VarInt byte length, then UTF-8). Fabric writes that with its own buffers; Bukkit uses `LinkProtocol.encode` / `decode`.

### Translations

Language files are in `common/src/main/resources/assets/vc-audio-distance/lang/`. Add every new key to **all** files. `LangConsistencyTest` fails if a key used in code is missing, if the languages have different keys, or if their placeholders differ.

### Documentation

The README is two files: `README.md` in English and `README.ru.md` in Russian. They have the same sections and are changed together. The rest of the documentation (CHANGELOG, this file, issue and pull request templates) is written in English first, followed by a complete Russian translation. The README is a reference for players and server owners: what the addon does, how to install it, commands and every setting, without internals.

GitHub release notes are generated from `CHANGELOG.md` by `.github/scripts/release-notes.sh`. When the changelog changes on `main`, the *Sync release notes* workflow updates the notes of already published releases.

The repository description and topics live in `.github/about.json`. The *Repository about* workflow validates the file in pull requests and applies it when it changes on `main`. Applying needs the `REPO_ADMIN_TOKEN` secret, because the built-in workflow token cannot change repository settings. It is set up once:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → this repository. *Permissions → Repository permissions → Administration*: *Read and write*. Nothing else is needed.
3. In this repository: *Settings → Secrets and variables → Actions → New repository secret*, name `REPO_ADMIN_TOKEN`, value: the token.
4. Run *Actions → Repository about → Run workflow* once, or change `.github/about.json`. When the token expires, generate a new one and update the secret.

### Releases

1. Set `mod_version` in `gradle.properties` and add its section to `CHANGELOG.md` (English, then Russian).
2. Merge into `main` and wait for the green build.
3. *Actions → Publish Release → Run workflow* on `main`, or push the tag `vX.Y.Z`. The workflow builds every jar, creates the tag and the release, and takes the notes from `CHANGELOG.md`.
4. The same run then uploads the files to Modrinth and CurseForge (see below). To upload an existing release again: *Actions → Publish to Modrinth & CurseForge → Run workflow* with its tag.

### Modrinth and CurseForge

The workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) takes the files of a GitHub release and uploads them:
- **Modrinth:** the mod (one file per Minecraft version, marked for Fabric, Quilt, Forge and NeoForge) and the Paper / Purpur / Spigot plugin.
- **CurseForge:** the mod only. A Bukkit plugin needs a separate project in CurseForge's Bukkit section.

A store is skipped with a warning while its project id or token is missing. Setup, once:
1. Create the projects by hand: Modrinth → *Create a project* (type *Mod*); CurseForge → *Minecraft → Mods → Create project*. Both sites review new projects; the first upload can go into the project while it waits. The Modrinth page is `docs/modrinth.md` and is uploaded by the *Modrinth page* workflow; the summary and categories are in `docs/store-description.md`.
2. Put the project ids into `.github/publish.json`: on Modrinth the *Project ID* from the project's menu, on CurseForge the *Project ID* number on the project page.
3. Tokens: Modrinth → *Settings → Personal access tokens*, scopes *Create versions*, *Read projects* and *Write versions* (the last one lets *Sync release notes* update the changelog of Modrinth versions when `CHANGELOG.md` changes); CurseForge → *Account → API tokens*. Add them as repository secrets `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` (*Settings → Secrets and variables → Actions*). Tokens never go into files or chats.

### Pull requests

1. Branch off `main` (`feature/…`, `fix/…`).
2. Run `./gradlew :common:test` and `./gradlew build`.
3. Describe what changed and how you tested it in game (Minecraft, loader and Simple Voice Chat versions; client, server or both).
4. Add an entry to `CHANGELOG.md` under an *Unreleased* section, in English and in Russian.
   The changelog becomes the release notes on Modrinth and CurseForge. Keep it like other mods' changelogs: short plain bullets under Added / Changed / Fixed, one change per line, about what players and server owners notice. No internals (rays, filters, formulas, class names) and no advertising tone.

---

## Русский

### Подготовка

- **JDK 25** — им собираются все модули (1.20 и 1.21 компилируются с `--release 17` / `21`).
- Git.

```bash
git clone https://github.com/Shamanalle/voice-physics.git
cd voice-physics
./gradlew :common:test             # быстро: звук, сервер, конфиг, переводы
./gradlew build                    # все версии, JAR в build/libs/
./gradlew :fabric-1.21:runClient   # или :fabric-1.20 / :fabric-26; runServer для серверной части
./gradlew :bukkit:build            # только плагин для Paper / Purpur / Spigot / Bukkit
```

### Где какой код

| Папка | Куда попадает | Что внутри |
|---|---|---|
| `common/` | во все JAR (Java 17, без классов Minecraft) | плагин Simple Voice Chat, кривая OpenAL, конфиг и пресеты, фильтр стен (`VoiceFilter`), модель приглушения, реестр говорящих, стены на сервере (`ServerWalls`), настройки сервера, протокол клиент–сервер (`LinkProtocol`, `ServerLink`), **переводы и иконка** |
| `shared/mc-all/` | во все модули Fabric | экран настроек (`SettingsScreen`), `Canvas`, слайдер, логика тика клиента, трассировка несколькими лучами |
| `shared/mc-1.20-1.21/` | `fabric-1.20`, `fabric-1.20.4`, `fabric-1.20.6`, `fabric-1.21` | точки входа клиента и общая, адаптеры экрана и отрисовки, акустика блоков, доступ к миру на клиенте, измерение стен на сервере, регистрация клавиши |
| `fabric-1.20/`, `fabric-1.20.4/`, `fabric-1.20.6/`, `fabric-1.21/` | свой модуль (1.20 – 1.20.1, 1.20.2 – 1.20.4, 1.20.5 – 1.20.6, 1.21.x) | `Compat` (с 1.20.2 экран сам рисует фон), сеть (каналы до 1.20.4, payload с 1.20.5), `PlayerLanguage` (с 1.20.2), `fabric.mod.json`, `mods.toml` |
| `fabric-26/` | свой модуль | адаптеры 26.x (API отрисовки через render state, различия клавиш и экранов между 26.1 и 26.3, имена payload), акустика блоков, измерение стен на сервере, метаданные. Проверяется на каждом релизе 26.x workflow *Minecraft 26.x compatibility* (`.github/workflows/compat-26.yml`) |
| `bukkit/` | свой JAR (Java 17) | плагин для Paper / Purpur / Spigot / Bukkit: только серверная часть. Сообщения плагина на тех же каналах, что у Fabric, акустика блоков через API Bukkit, `plugin.yml` |

Правила:
- Логика, которой не нужен Minecraft, живёт в `common` и покрывается юнит-тестом. `ServerWallsTest` показывает, как проверять события Simple Voice Chat на поддельных объектах.
- Экран рисует только через `Canvas`. API отрисовки Minecraft различается между версиями: в 1.21.6 `drawString` поменял тип возврата, а в 26.x используется API render state.
- Модуль 1.21 собирается против 1.21.8, но работает на 1.21–1.21.11. Прежде чем использовать там новый метод Minecraft, проверьте, что его сигнатура одинакова во всех 1.21.x (например, по маппингам Yarn каждой версии). Если нет — возьмите стабильный метод или рефлексию, как в `KeyMappings`. Примеры **нестабильных** методов: `Entity.level()`, `Entity.position()`, `Camera.getPosition()`, `ServerLevel.getEntity(UUID)`.
- Из аудиопотоков мир не трогаем. На клиенте тик заполняет `SpeakerRegistry`, на сервере тик измеряет стены для `ServerWalls`. Аудиочасть только читает.
- Серверная часть не должна ломать голосовой чат: при любой ошибке в `ServerWalls` уходит исходный пакет.
- Плагин для Bukkit использует только API Bukkit (без классов, которые есть только в Paper, и без NMS), поэтому работает на Paper, Purpur, Spigot и Bukkit. Геометрия лучей, которой нет в Bukkit, живёт в `common` (`VoxelRay`, `RayBundle`) и покрыта тестами.
- Клиент и сервер обмениваются сообщениями `LinkProtocol` в виде одной строки Minecraft (длина в байтах как VarInt, затем UTF-8). Fabric пишет её своими буферами, Bukkit — через `LinkProtocol.encode` / `decode`.

### Переводы

Файлы языков лежат в `common/src/main/resources/assets/vc-audio-distance/lang/`. Каждый новый ключ добавляйте во **все** файлы. `LangConsistencyTest` упадёт, если в коде есть ключ без перевода, если наборы ключей в языках различаются или если не совпадают плейсхолдеры.

### Документация

README — это два файла: `README.md` на английском и `README.ru.md` на русском. В них одинаковые разделы, и меняются они вместе. Остальная документация (CHANGELOG, этот файл, шаблоны issue и pull request) пишется сначала на английском, затем идёт полный перевод на русский. README — справочник для игроков и владельцев серверов: что умеет аддон, как его поставить, команды и все настройки, без внутреннего устройства.

Описания релизов на GitHub собираются из `CHANGELOG.md` скриптом `.github/scripts/release-notes.sh`. Когда журнал изменений меняется в `main`, workflow *Sync release notes* обновляет описания уже опубликованных релизов.

Описание и темы (topics) репозитория лежат в `.github/about.json`. Workflow *Repository about* проверяет этот файл в pull request и применяет его, когда файл меняется в `main`. Для применения нужен секрет `REPO_ADMIN_TOKEN`, потому что встроенный токен workflow не может менять настройки репозитория. Настраивается один раз:
1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. *Repository access*: *Only select repositories* → этот репозиторий. *Permissions → Repository permissions → Administration*: *Read and write*. Больше ничего не нужно.
3. В этом репозитории: *Settings → Secrets and variables → Actions → New repository secret*, имя `REPO_ADMIN_TOKEN`, значение — токен.
4. Один раз запустите *Actions → Repository about → Run workflow* или измените `.github/about.json`. Когда срок токена истечёт, создайте новый и обновите секрет.

### Релизы

1. Укажите `mod_version` в `gradle.properties` и добавьте раздел этой версии в `CHANGELOG.md` (сначала английский, затем русский).
2. Слейте изменения в `main` и дождитесь зелёной сборки.
3. *Actions → Publish Release → Run workflow* на `main` или запушьте тег `vX.Y.Z`. Workflow соберёт все JAR, создаст тег и релиз, а описание возьмёт из `CHANGELOG.md`.
4. Затем тот же запуск загрузит файлы на Modrinth и CurseForge (см. ниже). Чтобы загрузить уже вышедший релиз заново: *Actions → Publish to Modrinth & CurseForge → Run workflow* с его тегом.

### Modrinth и CurseForge

Workflow *Publish to Modrinth & CurseForge* (`.github/workflows/publish.yml`) берёт файлы релиза на GitHub и загружает их:
- **Modrinth:** мод (один файл на версию Minecraft, отмеченный для Fabric, Quilt, Forge и NeoForge) и плагин для Paper / Purpur / Spigot.
- **CurseForge:** только мод. Для плагина Bukkit нужен отдельный проект в разделе Bukkit на CurseForge.

Площадка пропускается с предупреждением, пока для неё нет ID проекта или токена. Настраивается один раз:
1. Создайте проекты вручную: Modrinth → *Create a project* (тип *Mod*); CurseForge → *Minecraft → Mods → Create project*. Оба сайта проверяют новые проекты; первую загрузку можно сделать, пока проект ждёт проверки. Страница Modrinth — `docs/modrinth.md`, её выкладывает workflow *Modrinth page*; краткое описание и категории — в `docs/store-description.md`.
2. Впишите ID проектов в `.github/publish.json`: на Modrinth — *Project ID* из меню проекта, на CurseForge — число *Project ID* на странице проекта.
3. Токены: Modrinth → *Settings → Personal access tokens*, права *Create versions*, *Read projects* и *Write versions* (последнее позволяет *Sync release notes* обновлять описание версий на Modrinth, когда меняется `CHANGELOG.md`); CurseForge → *Account → API tokens*. Добавьте их как секреты репозитория `MODRINTH_TOKEN` и `CURSEFORGE_TOKEN` (*Settings → Secrets and variables → Actions*). Токены никогда не пишутся в файлы и чаты.

### Pull request

1. Создайте ветку от `main` (`feature/…`, `fix/…`).
2. Запустите `./gradlew :common:test` и `./gradlew build`.
3. Опишите, что изменилось и как вы проверяли в игре (версии Minecraft, загрузчика и Simple Voice Chat; клиент, сервер или оба).
4. Добавьте запись в `CHANGELOG.md` в раздел *Unreleased* на английском и на русском.
   Журнал изменений становится описанием версии на Modrinth и CurseForge. Пишите его как у других модов: короткие простые пункты в разделах «Добавлено / Изменено / Исправлено», одно изменение на строку, о том, что заметят игроки и владельцы серверов. Без внутреннего устройства (лучи, фильтры, формулы, имена классов) и без рекламного тона.
