# Changelog

All notable changes to this project are documented in this file. Each version is described in English first, then in Russian.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

*Все заметные изменения проекта описываются в этом файле. Каждая версия описана сначала на английском, затем на русском.*

## [2.1.0] - 2026-09-26

### English

#### Added
- Echo now depends on the blocks around you: stone and ice echo more, wood less, wool and leaves hardly at all.
- Distinct echo off cliffs and canyon walls under the open sky.
- Echo from the speaker's surroundings (a voice from a cave echoes even if you are outside).
- The Effects tab shows the type of place you are in and its echo.
- "Round a corner" label in the HUD.

#### Changed
- Voices close to you have less echo than far ones.
- A voice behind a wall is heard from the nearest doorway, and its direction changes smoothly.
- Sharper corners muffle voices more.
- The edge volume no longer flattens the curve: voices fade smoothly down to it at the edge of the range.
- "Hear you" in the HUD counts your voice chat group (needs the addon on the server).
- Server wall strength can be set in 5% steps.

#### Fixed
- Players standing next to each other in tunnels and narrow corridors heard each other muffled.
- Voices flickered between muffled and clear near block edges and hill crests.
- Forests and open areas had room echo.

### Русский

#### Добавлено
- Эхо зависит от блоков вокруг: камень и лёд отражают сильнее, дерево слабее, шерсть и листва почти не отражают.
- Отчётливое эхо от скал и стен каньона под открытым небом.
- Эхо от окружения говорящего (голос из пещеры звучит с эхом, даже если вы снаружи).
- Вкладка «Эффекты» показывает тип места, где вы находитесь, и его эхо.
- Пометка «из-за угла» в HUD.

#### Изменено
- У близких голосов меньше эха, чем у дальних.
- Голос за стеной слышен со стороны ближайшего проёма, направление меняется плавно.
- Крутые углы глушат голос сильнее.
- Громкость на краю больше не делает кривую плоской: голос плавно затихает до неё к границе дистанции.
- «Вас слышат» в HUD учитывает вашу группу в голосовом чате (нужен аддон на сервере).
- Силу стен на сервере можно задавать с шагом 5%.

#### Исправлено
- Игроки рядом в тоннелях и узких коридорах слышали друг друга приглушённо.
- Голос то глох, то становился чистым у краёв блоков и на гребнях холмов.
- В лесу и на открытых местах было эхо как в помещении.

## [2.0.2] - 2026-09-26

### English

#### Added
- Support for Minecraft 1.20.2 – 1.20.6.
- `/vcd` replies and server messages in each player's game language on 1.20.2+.

#### Changed
- Files on the stores are named by Minecraft version ("Mod for 1.20 – 1.20.1" and so on).

### Русский

#### Добавлено
- Поддержка Minecraft 1.20.2 – 1.20.6.
- Ответы `/vcd` и сообщения сервера на языке игры каждого игрока на 1.20.2+.

#### Изменено
- Файлы в магазинах названы по версии Minecraft («Mod for 1.20 – 1.20.1» и т. д.).

## [2.0.1] - 2026-09-26

### English

#### Added
- Server options for Simple Voice Chat groups: dead players silent in groups, spectators heard only by spectators, isolated zones cut group voices (all off by default).
- `/vcd debug` shows the player's group; the HUD shows whether your group is open or isolated.
- Server wall strength in 10% steps.

#### Fixed
- The download link in the "addon required" message is now clickable.
- In open groups, nearby players now hear you at zone range, sneaking and megaphone distance.

### Русский

#### Добавлено
- Настройки сервера для групп Simple Voice Chat: мёртвых не слышно и в группе, наблюдателей слышат только наблюдатели, изолированные зоны отрезают голоса групп (по умолчанию всё выключено).
- `/vcd debug` показывает группу игрока; HUD показывает, открытая ваша группа или изолированная.
- Сила стен на сервере с шагом 10%.

#### Исправлено
- Ссылка на скачивание в сообщении «нужен аддон» теперь кликабельная.
- В открытых группах игроки рядом теперь слышат вас с учётом зон, корточек и мегафона.

## [2.0.0] - 2026-09-26

### English

First beta.

#### Added
- Servers can lock only some settings (curve, walls, materials or effects) and leave the rest to players.
- Servers can turn off the monitor, the radar and nearby players in the HUD (for PvP).

### Русский

Первая бета.

#### Добавлено
- Сервер может закрепить только часть настроек (кривую, стены, материалы или эффекты), а остальное оставить игрокам.
- Сервер может выключить монитор, радар и игроков рядом в HUD (для PvP).

## [1.8.0] - 2026-09-26

### English

#### Added
- Box zones drawn in game with `/vcd zone`.
- Zones can change voice range, be soundproof, set wall strength, a fixed echo and a message on entering.
- Rules: quieter when sneaking, dead players silent, spectators heard only by spectators, megaphone item.
- Option to require the addon: a message or a kick for players without it.
- Server tab in the settings screen for operators.
- `/vcd debug <player>`: who a player hears and who hears them.
- Server messages in seven languages, editable.

### Русский

#### Добавлено
- Зоны-боксы, которые рисуются в игре через `/vcd zone`.
- Зоны могут менять дальность голоса, быть звуконепроницаемыми, задавать силу стен, постоянное эхо и сообщение при входе.
- Правила: на корточках тише, мёртвых не слышно, наблюдателей слышат только наблюдатели, предмет-мегафон.
- Можно требовать аддон: сообщение или кик для игроков без него.
- Вкладка «Сервер» в настройках для операторов.
- `/vcd debug <игрок>`: кого слышит игрок и кто слышит его.
- Сообщения сервера на семи языках, их можно менять.

## [1.7.0] - 2026-09-26

### English

#### Added
- HUD size, background opacity and a compact mode.
- Color-blind friendly colors.
- Radar marks differ by shape as well as color.
- Profile codes to share your sound settings as one line of text.
- The monitor shows how much time the addon takes; it does less work on slow machines.

#### Changed
- Quiet voices (between words) have a hollow mark in the HUD.

### Русский

#### Добавлено
- Размер HUD, прозрачность фона и компактный режим.
- Цвета для дальтоников.
- Метки на радаре различаются не только цветом, но и формой.
- Коды профиля: настройки звука одной строкой, чтобы поделиться.
- Монитор показывает, сколько времени занимает аддон; на слабых компьютерах он работает реже.

#### Изменено
- Тихий голос (между словами) отмечен в HUD пустой меткой.

## [1.6.0] - 2026-09-26

### English

#### Added
- New materials: metal, earth and sand, soft blocks, ice, and "other blocks" (including modded blocks).
- More examples in the wall preview.

#### Changed
- Presets adapt to the server's voice range.
- The distance graph is more compact and easier to read.
- Files are renamed to `voice-physics-…`. Delete the old file when updating; settings are kept.

#### Fixed
- The Listen button looked disabled.

### Русский

#### Добавлено
- Новые материалы: металл, земля и песок, мягкие блоки, лёд и «остальные блоки» (в том числе из модов).
- Больше примеров в превью стен.

#### Изменено
- Пресеты подстраиваются под дальность голоса сервера.
- График дальности компактнее и понятнее.
- Файлы переименованы в `voice-physics-…`. При обновлении удалите старый файл; настройки сохраняются.

#### Исправлено
- Кнопка «Прослушать» выглядела выключенной.

## [1.5.0] - 2026-09-26

### English

#### Added
- Voices come through nearby doorways and windows instead of only through walls.
- `/vcd` commands for server admins.
- Sound zones per world or WorldGuard region.
- Full version on NeoForge 26.x.

#### Changed
- The mod is called Voice Physics everywhere.
- The HUD moved to the top right and shows distance units.
- The radar has a legend; Listen turns into Stop while playing.

### Русский

#### Добавлено
- Голос проходит через ближайшие проёмы и окна, а не только сквозь стены.
- Команды `/vcd` для админов сервера.
- Звуковые зоны для мира или региона WorldGuard.
- Полная версия на NeoForge 26.x.

#### Изменено
- Мод везде называется Voice Physics.
- HUD переехал в правый верхний угол и показывает единицы расстояния.
- У радара есть легенда; «Прослушать» во время звучания превращается в «Стоп».

## [1.4.0] - 2026-09-25

### English

#### Added
- Echo in caves and halls.
- Dull voices under water.
- Rain and thunder make far voices harder to hear.
- Effects tab with switches for echo, water and weather.
- Voice HUD: who is talking, how far and where; how many players hear you.
- Direction arrows and a radar in the monitor.
- Voice chat status of nearby players (Simple Voice Chat 2.6.1+).
- Listen button to hear the distance curve.
- Ukrainian, German, Spanish, Portuguese (Brazil) and Chinese.

### Русский

#### Добавлено
- Эхо в пещерах и залах.
- Глухие голоса под водой.
- Дождь и гроза заглушают дальние голоса.
- Вкладка «Эффекты» с переключателями эха, воды и погоды.
- HUD голоса: кто говорит, как далеко и где; сколько игроков вас слышат.
- Стрелки направления и радар в мониторе.
- Статус голосового чата игроков рядом (Simple Voice Chat 2.6.1+).
- Кнопка «Прослушать» для кривой громкости.
- Украинский, немецкий, испанский, португальский (Бразилия) и китайский.

## [1.3.0] - 2026-09-25

### English

#### Added
- The monitor lists everyone in voice range, not only those talking.
- With the addon on the server, the monitor shows who has voice chat off, disconnected or muted.

### Русский

#### Добавлено
- Монитор показывает всех в радиусе голоса, а не только говорящих.
- С аддоном на сервере монитор показывает, у кого голосовой чат выключен, отключён или без звука.

## [1.2.4] - 2026-09-25

### English

#### Fixed
- The exponential curve was not really exponential.
- 1/r and exponential curves cut voices off at the edge of the range.

### Русский

#### Исправлено
- Экспоненциальная кривая была не экспоненциальной.
- Кривые 1/r и экспонента обрывали голос на краю дальности.

## [1.2.3] - 2026-09-25

### English

#### Added
- The Paper / Purpur / Spigot plugin supports Minecraft 1.20.1 – 26.3.

### Русский

#### Добавлено
- Плагин для Paper / Purpur / Spigot поддерживает Minecraft 1.20.1 – 26.3.

## [1.2.2] - 2026-09-25

### English

#### Added
- Support for Minecraft 26.1 – 26.2.
- Releases on Modrinth and CurseForge.

#### Fixed
- The mod icon did not show in Mod Menu.

### Русский

#### Добавлено
- Поддержка Minecraft 26.1 – 26.2.
- Релизы на Modrinth и CurseForge.

#### Исправлено
- Иконка мода не показывалась в Mod Menu.

## [1.2.1] - 2026-09-25

### English

#### Changed
- Settings files are ordered and commented in English and Russian.
- Simpler server settings; walls are the same for everyone.

### Русский

#### Изменено
- Файлы настроек упорядочены и прокомментированы на английском и русском.
- Настройки сервера проще; стены одинаковые для всех.

## [1.2.0] - 2026-09-25

### English

#### Added
- Optional server side (Fabric and Paper / Purpur / Spigot): walls for players without the addon, a server sound profile, exact whisper range.
- New settings screen with Distance, Walls, Materials and Monitor tabs.
- Adjustable wall strength per material.

#### Changed
- Wall muffling is much more noticeable, without clicks.

#### Fixed
- The mod did not load on 1.20 and 1.21.
- Crashes and broken walls on some 1.21.x versions.
- Missing texts on 26.x; glass, leaves and doors did not count as walls.
- Voices stayed slightly muffled after the first wall.

### Русский

#### Добавлено
- Необязательная серверная часть (Fabric и Paper / Purpur / Spigot): стены для игроков без аддона, профиль звука сервера, точная дальность шёпота.
- Новый экран настроек с вкладками «Дистанция», «Стены», «Материалы» и «Монитор».
- Сила стен для каждого материала.

#### Изменено
- Приглушение стенами стало намного заметнее и без щелчков.

#### Исправлено
- Мод не загружался на 1.20 и 1.21.
- Вылеты и неработающие стены на некоторых версиях 1.21.x.
- Недостающие тексты на 26.x; стекло, листва и двери не считались стенами.
- После первой стены голос оставался слегка приглушённым.

## [1.1.0] - 2026-09-25

### English

#### Added
- Voices behind walls are muffled.
- Files for Forge, NeoForge and Minecraft 1.20.1, 1.21.x and 26.x.

### Русский

#### Добавлено
- Голоса за стенами приглушаются.
- Файлы для Forge, NeoForge и Minecraft 1.20.1, 1.21.x и 26.x.

## [1.0.0] - 2026-09-25

### English

#### Added
- Distance curves: linear, 1/r and exponential.
- Volume at the edge of the range.
- Whisper falloff setting.
- Live graph of volume by distance.
- Presets.
- Settings from Controls, Mod Menu and Simple Voice Chat's settings.
- English and Russian.

### Русский

#### Добавлено
- Кривые громкости: линейная, 1/r и экспоненциальная.
- Громкость на краю дальности.
- Настройка спада шёпота.
- Живой график громкости по расстоянию.
- Пресеты.
- Настройки из «Управления», Mod Menu и настроек Simple Voice Chat.
- Английский и русский.
