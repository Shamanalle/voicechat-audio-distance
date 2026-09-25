# Changelog

All notable changes to this project are documented in this file. Each version is described in English first, then in Russian.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

*Все заметные изменения проекта описываются в этом файле. Каждая версия описана сначала на английском, затем на русском.*

## [Unreleased]

### English

#### Changed
- **Readable settings files.** The client and server files now list their keys in a fixed order, grouped under headings, with a comment on every key in English and Russian: what it does, its range and its default. Numbers are written short (`0.6` instead of `0.6000`).
- **Simpler server settings**, in three sections:
  1. *Walls, for every player:* `walls_strength` (0 turns walls off) and `material.*`.
  2. *Players without the addon:* `server_walls`, `server_walls_max_streams`.
  3. *Players with the addon:* `profile_mode`, the new `profile_preset` (`vanilla`, `realistic`, `clear`, `stealth` or `custom`), and the custom `profile.*` curve.

  Walls no longer depend on the chosen profile, so everyone hears the same walls.
- Server files from 1.2.0 and client files from earlier versions are rewritten in the new format on the first start, keeping their values.

### Русский

#### Изменено
- **Понятные файлы настроек.** В файлах клиента и сервера ключи теперь идут в постоянном порядке, сгруппированы по разделам, и у каждого есть комментарий на английском и русском: что он делает, диапазон и значение по умолчанию. Числа записываются коротко (`0.6` вместо `0.6000`).
- **Более простые настройки сервера**, в трёх разделах:
  1. *Стены, для всех игроков:* `walls_strength` (0 выключает стены) и `material.*`.
  2. *Игроки без аддона:* `server_walls`, `server_walls_max_streams`.
  3. *Игроки с аддоном:* `profile_mode`, новый `profile_preset` (`vanilla`, `realistic`, `clear`, `stealth` или `custom`) и своя кривая `profile.*`.

  Стены больше не зависят от выбранного профиля, поэтому все слышат одинаковые стены.
- Файлы сервера из 1.2.0 и файлы клиента из прошлых версий при первом запуске переписываются в новый формат с сохранением значений.

## [1.2.0] - 2026-09-25

### English

#### Added
- **Optional server side.** The same jar now also runs on a Fabric server, and each side works without the other.
  - *Server walls:* players with plain Simple Voice Chat hear voices muffled through walls. For each listener behind a wall, the speaker's frame is decoded once, filtered and re-encoded. Group, spectator and plugin audio, listeners who have the addon, and anything above `server_walls_max_streams` (default 24) pass through untouched. Any error falls back to the original packet.
  - *Server sound profile:* `off`, `suggest` (chat notice and an *Apply server profile* button) or `enforce` (used while connected; the player's own settings are kept and return when they leave).
  - *Exact whisper range:* the server sends its real voice and whisper distances for the whisper curve.
  - `config/vc-audio-distance-server.properties`, re-read automatically when edited, with changes sent to connected players.
  - Players who have the addon muffle walls locally, so the server skips them.
- **Plugin for Paper, Purpur, Spigot and Bukkit** (1.20.1 and newer): `voicechat-audio-distance-bukkit-1.2.0.jar`. The same server side as on Fabric, with the same settings, stored in `plugins/VoicechatAudioDistance/`. It speaks the same protocol, so the Fabric client works with it just as with a Fabric server.
- **New settings screen** with four tabs:
  - *Distance* — live curve, whisper curve, hover readout in blocks / % / dB, dots for the people you hear right now;
  - *Walls* — how a voice sounds behind glass, wood, stone, wool and leaves;
  - *Materials* — per-material absorption;
  - *Monitor* — live distance, loudness and wall loss of every voice you hear, plus the server status.
- The active tab and the matching preset are highlighted, long texts are shortened to fit, and the layout works down to the smallest GUI size (320×240).
- Per-material wall weights (stone, wood, wool, glass, doors, leaves, bars and fences, liquids), saved in the config.
- The *Voice distance & walls…* button in Simple Voice Chat's settings now also exists on 26.x.
- Sound Physics Remastered detection: our wall muffling stands down so voices are not muffled twice.
- 61 unit tests: filter, occlusion model, config round-trips and migration, speaker registry, server walls with a fake Simple Voice Chat API, client–server protocol and its wire format, ray geometry, server settings, translation consistency.

#### Changed
- **Audible wall muffling.** The old 1-pole filter changed a voice by less than 1 dB behind a stone wall. It is replaced by a 4th-order TPT state-variable low-pass plus broadband transmission loss:
  - about −8 dB and ~2.5 kHz for one stone wall at the default strength;
  - about −18 dB and ~600 Hz for three.
- Filter parameters glide with a ~90 ms time constant, and entering or leaving the bypass is crossfaded, so walls no longer cause clicks.
- Rays only count blocks whose real collision shape they cross (slabs, open doors, fences, carpets), and 5 parallel rays give soft edges at corners and doorways.
- Walls are measured on the game thread each tick; audio threads only read the result, so the world is never touched off-thread.
- Whisper detection uses Simple Voice Chat's own whisper flag instead of guessing from the distance.
- The plugin, config, audio processing, occlusion model and server logic live in `common`, shared by every version. The client code and the settings screen are shared too, with a small adapter per version.
- `Esc` on the settings screen now saves, like vanilla option screens; *Cancel* restores everything.
- The config is written atomically, validated on load and migrated from 1.1.x automatically.
- Versions are defined once in `gradle.properties`.
- The server remembers that a player has the addon until they leave the game, not only until their voice connection drops. Otherwise, after reconnecting voice, such a player would be muffled twice: by the server and by their own client.
- Release notes on GitHub are generated from this file, and a workflow keeps the notes of already published releases in sync with it.

#### Fixed
- **Simple Voice Chat dependency.** Simple Voice Chat versions look like `1.21.8-2.6.24` (`2.6.24+26.3` on 26.x), so the old `>=2.4.0` / `[2.6.0,)` constraints never matched on 1.20 and 1.21 and the game refused to start. Each branch now uses the correct format.
- **The 1.21.x jar on other 1.21 releases:**
  - `GuiGraphics.drawString` changed its return type in 1.21.6, which crashed the screen on 1.21–1.21.5;
  - the `KeyMapping` constructor changed in 1.21.9 and 1.21.11, which crashed start-up;
  - `Camera.getPosition` was removed in 1.21.11, which silently disabled walls;
  - `Entity.position` was removed in 1.21.9.

  Every Minecraft member the jar uses is checked against the mappings of 1.21 through 1.21.11.
- 26.x: 15 missing translation keys showed up as raw keys. Glass, leaves and doors were never counted as walls.
- The filter bypass never re-engaged after the first wall, so voices stayed slightly filtered.
- Occlusion caches were never cleared; they are now reset when the world or server changes.
- The release workflow used JDK 21, which cannot build the 26.x module.
- `fabric.mod.json` and `mods.toml` disagreed on the supported 26.x versions.
- The Forge / NeoForge files were described as full versions; they are Fabric jars that only load the distance curves (see *Notes*).

#### Notes
- The Forge / NeoForge jars are a lite build: distance curves configured through `config/vc-audio-distance.properties`. The settings screen, walls, monitor and server side are Fabric-only.
- The Paper / Purpur / Spigot / Bukkit plugin is the server side only: on those servers the client features come from the Fabric addon on the player's side.

### Русский

#### Добавлено
- **Необязательная серверная часть.** Тот же JAR теперь работает и на сервере Fabric, и каждая сторона работает без другой.
  - *Стены на сервере:* игроки с обычным Simple Voice Chat слышат голоса приглушёнными за стенами. Для каждого слушателя за стеной кадр говорящего один раз декодируется, фильтруется и кодируется заново. Групповой звук, звук для наблюдателей и других плагинов, слушатели с аддоном и всё сверх `server_walls_max_streams` (по умолчанию 24) проходят без изменений. При любой ошибке уходит исходный пакет.
  - *Профиль звука сервера:* `off`, `suggest` (сообщение в чате и кнопка «Применить профиль сервера») или `enforce` (действует, пока игрок подключён; его собственные настройки сохраняются и возвращаются при выходе).
  - *Точная дальность шёпота:* сервер передаёт настоящие дальности голоса и шёпота для кривой шёпота.
  - `config/vc-audio-distance-server.properties` перечитывается автоматически при изменении, а изменения отправляются подключённым игрокам.
  - Игроки с аддоном глушат стены у себя, поэтому сервер их пропускает.
- **Плагин для Paper, Purpur, Spigot и Bukkit** (1.20.1 и новее): `voicechat-audio-distance-bukkit-1.2.0.jar`. Та же серверная часть, что на Fabric, с теми же настройками, которые хранятся в `plugins/VoicechatAudioDistance/`. Протокол тот же, поэтому клиент для Fabric работает с ним так же, как с сервером Fabric.
- **Новый экран настроек** из четырёх вкладок:
  - *Дистанция* — живой график, кривая шёпота, значение при наведении в блоках / % / дБ, точки людей, которых вы слышите прямо сейчас;
  - *Стены* — как звучит голос за стеклом, деревом, камнем, шерстью и листвой;
  - *Материалы* — поглощение звука каждым материалом;
  - *Монитор* — дистанция, громкость и потери на стенах для каждого, кого вы слышите, плюс статус сервера.
- Активная вкладка и совпавший пресет подсвечиваются, длинные подписи сокращаются, а раскладка работает вплоть до минимального размера интерфейса 320×240.
- Настройка поглощения по материалам (камень, дерево, шерсть, стекло, двери, листва, решётки и заборы, жидкости), сохраняется в конфиге.
- Кнопка «Дальность голоса и стены…» в настройках Simple Voice Chat теперь есть и на 26.x.
- Определение Sound Physics Remastered: наше приглушение стенами отключается, чтобы голос не глушился дважды.
- 61 юнит-тест: фильтр, модель приглушения, сохранение и миграция конфига, реестр говорящих, стены на сервере с поддельным API Simple Voice Chat, протокол клиент–сервер и его формат передачи, геометрия лучей, настройки сервера, согласованность переводов.

#### Изменено
- **Приглушение стенами стало слышно.** Старый однополюсный фильтр менял голос за каменной стеной меньше чем на 1 дБ. Теперь вместо него фильтр 4-го порядка (TPT SVF) плюс общее ослабление:
  - около −8 дБ и ~2,5 кГц за одной каменной стеной при силе по умолчанию;
  - около −18 дБ и ~600 Гц за тремя.
- Параметры фильтра меняются плавно (постоянная времени ~90 мс), а включение и выключение фильтра идёт с перекрёстным затуханием, поэтому стены больше не вызывают щелчков.
- Луч учитывает только блоки, реальную форму которых пересекает (полублоки, открытые двери, заборы, ковры), а 5 параллельных лучей дают мягкие края у углов и дверных проёмов.
- Стены измеряются в игровом потоке каждый тик; аудиопотоки только читают результат и никогда не обращаются к миру.
- Шёпот определяется по флагу самого Simple Voice Chat, а не угадывается по дистанции.
- Плагин, конфиг, обработка звука, модель приглушения и серверная логика находятся в `common` и общие для всех версий. Клиентский код и экран настроек тоже общие, на каждую версию остался небольшой адаптер.
- `Esc` на экране настроек сохраняет изменения, как в ванильных настройках; «Отмена» возвращает всё как было.
- Конфиг записывается атомарно, проверяется при загрузке и автоматически переносится с 1.1.x.
- Версии зависимостей задаются в одном месте — `gradle.properties`.
- Сервер помнит, что у игрока есть аддон, пока тот не выйдет из игры, а не только пока не оборвётся голосовое соединение. Иначе после переподключения голоса такого игрока глушили бы дважды: сервер и его собственный клиент.
- Описания релизов на GitHub берутся из этого файла, а отдельный workflow синхронизирует с ним описания уже опубликованных релизов.

#### Исправлено
- **Зависимость от Simple Voice Chat.** Версии Simple Voice Chat имеют вид `1.21.8-2.6.24` (`2.6.24+26.3` на 26.x), поэтому старые условия `>=2.4.0` / `[2.6.0,)` не выполнялись на 1.20 и 1.21, и игра не запускалась. Теперь в каждой ветке правильный формат.
- **JAR для 1.21.x на других версиях 1.21:**
  - `GuiGraphics.drawString` сменил тип возврата в 1.21.6 — экран падал на 1.21–1.21.5;
  - конструктор `KeyMapping` изменился в 1.21.9 и 1.21.11 — игра падала при запуске;
  - `Camera.getPosition` удалён в 1.21.11 — стены тихо выключались;
  - `Entity.position` удалён в 1.21.9.

  Каждый используемый метод Minecraft сверен с маппингами версий 1.21–1.21.11.
- 26.x: 15 недостающих ключей перевода показывались как сырые ключи. Стекло, листва и двери не считались стенами.
- Фильтр после первой стены так и не выключался, и голоса оставались слегка приглушёнными.
- Кэши приглушения никогда не очищались; теперь они сбрасываются при смене мира или сервера.
- Релизный workflow использовал JDK 21, которым модуль 26.x не собирается.
- `fabric.mod.json` и `mods.toml` расходились в списке поддерживаемых версий 26.x.
- Файлы для Forge / NeoForge описывались как полные версии, хотя это JAR для Fabric, в которых работают только кривые громкости (см. *Примечания*).

#### Примечания
- JAR для Forge / NeoForge — облегчённая версия: кривые громкости с настройкой через `config/vc-audio-distance.properties`. Экран настроек, стены, монитор и серверная часть есть только в версии для Fabric.
- Плагин для Paper / Purpur / Spigot / Bukkit — это только серверная часть: на таких серверах клиентские функции даёт аддон для Fabric у самого игрока.

## [1.1.0] - 2026-09-25

### English

#### Added
- **Multi-loader and multi-version release files** (see 1.2.0: the Forge / NeoForge files are Fabric jars that only load the distance curves):
  - **Fabric / Quilt 1.20.1**: `voicechat-audio-distance-fabric-1.1.0+mc1.20.1.jar` (Minecraft 1.20.1, Java 17).
  - **Forge 1.20.1**: `voicechat-audio-distance-forge-1.1.0+mc1.20.1.jar` (includes `META-INF/mods.toml` and `@ForgeVoicechatPlugin`).
  - **Fabric / Quilt 1.21.x**: `voicechat-audio-distance-fabric-1.1.0+mc1.21.x.jar`.
  - **NeoForge 1.21.x**: `voicechat-audio-distance-neoforge-1.1.0+mc1.21.x.jar` (includes `META-INF/neoforge.mods.toml`).
  - **Forge 1.21.x**: `voicechat-audio-distance-forge-1.1.0+mc1.21.x.jar`.
  - **Fabric 26.x**: `voicechat-audio-distance-fabric-1.1.0+mc26.x.jar` (Java 25, unobfuscated Loom, SDL3 input).
  - **NeoForge 26.x** and **Forge 26.x** files.
  - Release file names follow `[project]-[loader]-[version]+mc[target].jar`.
- **Wall occlusion:** voices behind solid blocks are low-pass filtered in real time.
- **Filter (`OcclusionFilter`):** a 1-pole IIR low-pass filter swept from 18,000 Hz down to 500 Hz, with per-stream state between 20 ms frames.
- **Line-of-sight raycasting (`RaycastOcclusion`):** Minecraft's voxel traversal (`BlockGetter.traverseBlocks`) with per-material weights (wool/carpet 0.45, stone 0.35, doors/wood 0.25, glass/iron bars 0.15–0.18, leaves 0.10, liquids 0.20) and a 40 ms cache per entity.
- **Settings screen:** a *Wall Occlusion: ON / OFF* toggle and a *Muffling: 0% – 100%* slider; presets with occlusion profiles (*Realistic* 65% on, *Stealth* 85% on, *Vanilla* / *Audible* off).
- **Tests:** `OcclusionFilterTest` (11 tests) for frequency response, frame continuity, overflow safety and presets.

#### Known issues (fixed in 1.2.0)
- The 1.20.1 and 1.21.x files do not load: they require Simple Voice Chat `>=2.4.0`, but Simple Voice Chat versions look like `1.21.8-2.6.24`, which never matches.
- The 1.21.x file crashes the settings screen on 1.21–1.21.5 and the game start-up on 1.21.9 and newer, and walls do not work on 1.21.11.
- 26.x: 15 texts show up as raw translation keys; glass, leaves and doors are not counted as walls.
- The wall filter barely changes a voice (less than 1 dB behind a stone wall) and does not switch off after the first wall.
- The Forge and NeoForge files only load the distance curves.

### Русский

#### Добавлено
- **Файлы для нескольких загрузчиков и версий** (см. 1.2.0: файлы для Forge / NeoForge — это JAR для Fabric, в которых работают только кривые громкости):
  - **Fabric / Quilt 1.20.1**: `voicechat-audio-distance-fabric-1.1.0+mc1.20.1.jar` (Minecraft 1.20.1, Java 17).
  - **Forge 1.20.1**: `voicechat-audio-distance-forge-1.1.0+mc1.20.1.jar` (содержит `META-INF/mods.toml` и `@ForgeVoicechatPlugin`).
  - **Fabric / Quilt 1.21.x**: `voicechat-audio-distance-fabric-1.1.0+mc1.21.x.jar`.
  - **NeoForge 1.21.x**: `voicechat-audio-distance-neoforge-1.1.0+mc1.21.x.jar` (содержит `META-INF/neoforge.mods.toml`).
  - **Forge 1.21.x**: `voicechat-audio-distance-forge-1.1.0+mc1.21.x.jar`.
  - **Fabric 26.x**: `voicechat-audio-distance-fabric-1.1.0+mc26.x.jar` (Java 25, Loom без обфускации, ввод через SDL3).
  - Файлы **NeoForge 26.x** и **Forge 26.x**.
  - Имена файлов релиза имеют вид `[проект]-[загрузчик]-[версия]+mc[цель].jar`.
- **Приглушение стенами:** голоса за твёрдыми блоками пропускаются через фильтр нижних частот в реальном времени.
- **Фильтр (`OcclusionFilter`):** однополюсный IIR-фильтр нижних частот с частотой среза от 18 000 до 500 Гц и состоянием потока между кадрами по 20 мс.
- **Трассировка прямой видимости (`RaycastOcclusion`):** обход вокселей Minecraft (`BlockGetter.traverseBlocks`) с весами материалов (шерсть/ковры 0,45, камень 0,35, двери/дерево 0,25, стекло/железные решётки 0,15–0,18, листва 0,10, жидкости 0,20) и кэшем на 40 мс для каждой сущности.
- **Экран настроек:** переключатель «Стены: ВКЛ / ВЫКЛ» и ползунок «Глубина: 0% – 100%»; пресеты с профилями приглушения (*Мягкий* — 65%, *Стелс* — 85%, *Ваниль* / *Чёткий* — выключено).
- **Тесты:** `OcclusionFilterTest` (11 тестов) — частотная характеристика, непрерывность кадров, защита от переполнения и пресеты.

#### Известные проблемы (исправлены в 1.2.0)
- Файлы для 1.20.1 и 1.21.x не загружаются: они требуют Simple Voice Chat `>=2.4.0`, а версии Simple Voice Chat имеют вид `1.21.8-2.6.24` и этому условию не соответствуют.
- Файл для 1.21.x роняет экран настроек на 1.21–1.21.5 и запуск игры на 1.21.9 и новее, а на 1.21.11 не работают стены.
- 26.x: 15 текстов показываются как сырые ключи перевода; стекло, листва и двери не считаются стенами.
- Фильтр стен почти не меняет голос (меньше 1 дБ за каменной стеной) и не выключается после первой стены.
- Файлы для Forge и NeoForge загружают только кривые громкости.

## [1.0.0] - 2026-09-25

### English

#### Added
- **Distance curves:** *Linear (Vanilla)*, *Realistic Inverse (1/r)* and *Exponential (Stealth)*.
- **Volume floor (`AL_MIN_GAIN`):** voices stay softly audible at the edge of the range.
- **Whisper falloff slider** (`0.50x – 2.00x`).
- **Live audibility graph** with a hover readout (distance in blocks and volume in %).
- **One-click presets:** *Vanilla*, *Realistic*, *Audible* and *Stealth*.
- **Integrations:** a key binding in the controls menu (unbound by default), a Mod Menu configuration screen, and a button in Simple Voice Chat's settings screen.
- **Translations:** English (`en_us`) and Russian (`ru_ru`).
- **Tests:** JUnit 5 tests for the attenuation formulas, clamped bounds and presets.
- **CI/CD:** GitHub Actions workflows for builds and releases.

#### Changed
- OpenAL uses the clamped distance models `AL_LINEAR_DISTANCE_CLAMPED`, `AL_INVERSE_DISTANCE_CLAMPED` and `AL_EXPONENT_DISTANCE_CLAMPED`, so voices never get louder than 100% up close.
- Config fields are `volatile`, as they are read by the audio threads and written by the settings screen.
- The config file location comes from `FabricLoader.getInstance().getConfigDir()`.
- The screen layout adapts to small resolutions and large GUI scales.

#### Fixed
- `AL_MIN_GAIN` bypassed player muting and volume; it is now scaled by the source's own `AL_GAIN`.
- The exponential formula of the graph did not match OpenAL.
- Whisper detection now scales with the server's voice distance.
- The graph readout overlapped the screen title.
- The settings screen had a black background when opened from the main menu.

#### Known issues (fixed in 1.2.0)
- The mod does not load: it requires Simple Voice Chat `>=2.4.0`, but Simple Voice Chat versions look like `1.21.8-2.6.24`, which never matches.

### Русский

#### Добавлено
- **Кривые громкости:** *Линейная (Ваниль)*, *Реалистичная (1/r)* и *Экспоненциальная (Стелс)*.
- **Порог громкости (`AL_MIN_GAIN`):** голоса остаются тихо слышны на краю дальности.
- **Ползунок спада шёпота** (`0.50x – 2.00x`).
- **Живой график слышимости** со значением при наведении (дистанция в блоках и громкость в %).
- **Пресеты в один клик:** *Ваниль*, *Мягкий*, *Чёткий* и *Стелс*.
- **Интеграции:** клавиша в меню управления (по умолчанию не назначена), экран настроек в Mod Menu и кнопка в настройках Simple Voice Chat.
- **Переводы:** английский (`en_us`) и русский (`ru_ru`).
- **Тесты:** тесты JUnit 5 для формул затухания, ограничений диапазонов и пресетов.
- **CI/CD:** workflow GitHub Actions для сборки и релизов.

#### Изменено
- OpenAL использует модели с ограничением `AL_LINEAR_DISTANCE_CLAMPED`, `AL_INVERSE_DISTANCE_CLAMPED` и `AL_EXPONENT_DISTANCE_CLAMPED`, поэтому вблизи голос никогда не становится громче 100%.
- Поля конфига объявлены `volatile`: их читают аудиопотоки и меняет экран настроек.
- Путь к файлу конфига берётся из `FabricLoader.getInstance().getConfigDir()`.
- Раскладка экрана подстраивается под маленькое разрешение и крупный масштаб интерфейса.

#### Исправлено
- `AL_MIN_GAIN` обходил мьют и громкость игрока; теперь он масштабируется по `AL_GAIN` самого источника.
- Экспоненциальная формула на графике не совпадала с OpenAL.
- Определение шёпота теперь зависит от дальности голоса на сервере.
- Подсказка графика перекрывала заголовок экрана.
- Экран настроек открывался с чёрным фоном из главного меню.

#### Известные проблемы (исправлены в 1.2.0)
- Мод не загружается: он требует Simple Voice Chat `>=2.4.0`, а версии Simple Voice Chat имеют вид `1.21.8-2.6.24` и этому условию не соответствуют.
