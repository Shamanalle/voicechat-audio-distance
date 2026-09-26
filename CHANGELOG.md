# Changelog

All notable changes to this project are documented in this file. Each version is described in English first, then in Russian.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

*Все заметные изменения проекта описываются в этом файле. Каждая версия описана сначала на английском, затем на русском.*

## [1.8.0] - 2026-09-26

### English

#### Added
- **Sound zones do much more.** Besides a world or a WorldGuard region, a zone can now be a **box** you draw in game (`/vcd zone pos1`, `pos2`, `create`, or `create <name> <radius>` around you). A zone can set:
  - how far voices carry: `range_multiplier` (a stage ×2, a library ×0.4) or `voice_range` / `whisper_range` in blocks. It works for every player, with or without the addon, because the server itself decides who gets a voice;
  - `isolated`: voices neither leave nor enter the zone (a soundproof booth);
  - `walls_strength`, `echo` (always this much echo, like a cathedral, or none), `enter_message` and `priority` for overlapping zones.
- **Game rules:**
  - sneaking players carry less far (`sneak_range_multiplier`);
  - dead players are silent until they respawn;
  - spectators are heard only by spectators;
  - an item held in hand works as a **megaphone** (`megaphone_item`, `megaphone_multiplier`).
- **Addon requirement** (`require_addon`): players who have Simple Voice Chat but not the addon, or a version below `min_addon_version`, can get a message once, on every join, or be disconnected. Players without Simple Voice Chat are never affected.
- **Server tab.** Operators with the addon get a *Server* tab in the settings screen with the profile, preset, walls, rules, the requirement and zones as buttons.
- **New commands:**
  - `/vcd zone …`, `/vcd rule …` and `/vcd require …`;
  - `/vcd debug <player>`, which shows whom a player hears and who hears them, and why not.
- **Server messages in seven languages.** `/vcd` replies and messages to players come in each player's own game language (1.21+). The texts are written to `vc-audio-distance-lang/` next to the server settings file, where any line can be changed or a language added.

#### Changed
- `messages_language` now takes `auto` (the default) or a language code such as `ru_ru`; `en` and `ru` still work.
- Old server settings files are rewritten with the new sections on the first start, keeping their values.

### Русский

#### Добавлено
- **Звуковые зоны умеют гораздо больше.** Кроме мира или региона WorldGuard, зоной теперь может быть **бокс**, который вы рисуете в игре (`/vcd zone pos1`, `pos2`, `create` или `create <имя> <радиус>` вокруг себя). Зона может задать:
  - как далеко слышно голоса: `range_multiplier` (сцена ×2, библиотека ×0.4) или `voice_range` / `whisper_range` в блоках. Это работает для всех игроков, с аддоном и без, потому что сервер сам решает, кому доставлять голос;
  - `isolated`: голоса не выходят из зоны и не заходят в неё (звуконепроницаемая кабинка);
  - `walls_strength`, `echo` (всегда такое эхо, как в соборе, или никакого), `enter_message` и `priority` для пересекающихся зон.
- **Правила игры:**
  - на корточках голос слышно ближе (`sneak_range_multiplier`);
  - мёртвых не слышно до возрождения;
  - наблюдателей слышат только наблюдатели;
  - предмет в руке работает как **мегафон** (`megaphone_item`, `megaphone_multiplier`).
- **Требование аддона** (`require_addon`): игрокам с Simple Voice Chat, но без аддона или с версией ниже `min_addon_version` можно один раз написать, напоминать при каждом входе или отключать их. Игроков без Simple Voice Chat это не касается.
- **Вкладка «Сервер».** Операторы с аддоном видят в экране настроек вкладку «Сервер»: профиль, пресет, стены, правила, требование аддона и зоны — кнопками.
- **Новые команды:**
  - `/vcd zone …`, `/vcd rule …` и `/vcd require …`;
  - `/vcd debug <игрок>`: показывает, кого слышит игрок и кто слышит его, и почему нет.
- **Серверные сообщения на семи языках.** Ответы `/vcd` и сообщения игрокам приходят на языке игры самого игрока (1.21+). Тексты записываются в `vc-audio-distance-lang/` рядом с файлом настроек сервера — там можно поменять любую строку или добавить язык.

#### Изменено
- `messages_language` теперь принимает `auto` (по умолчанию) или код языка, например `ru_ru`; `en` и `ru` тоже работают.
- Старые файлы настроек сервера при первом запуске дописываются новыми разделами с сохранением значений.

## [1.7.0] - 2026-09-26

### English

#### Added
- **Voice HUD settings** on the Monitor tab:
  - size, 50–150%;
  - background opacity, down to text only;
  - a **compact** mode: one line for everyone talking, with the closest voice and how many more.
- **Colors for color blindness.** A switch on the Monitor tab turns the HUD, the monitor and the radar to colors that stay apart with red-green color blindness: blue for talking, purple for whispers, orange for walls.
- **Radar marks differ in shape** as well as color: a square for talking, a cross for whispering, a diamond behind a wall. The radar legend matches.
- **Profile codes.**
  - *Copy profile code* and *Paste profile code* on the Distance tab share your curve, walls, materials and effects as one line of text (`VP1:…`).
  - Server admins can load a code with `/vcd preset import <code>` and give out the server's with `/vcd preset export`.
- **Load meter.**
  - The monitor shows how long the addon's own work takes per tick: walls, ways round and echo.
  - Above 2 ms the addon spaces that work out by itself.
  - `/vcd status` shows the same for the server's walls for players without the addon.

#### Changed
- In the HUD a quiet voice (between words) now has a hollow mark instead of a dimmed one.

### Русский

#### Добавлено
- **Настройки HUD голоса** на вкладке «Монитор»:
  - размер, 50–150%;
  - прозрачность фона, вплоть до одного текста;
  - **компактный** режим: одна строка на всех говорящих — ближайший голос и сколько ещё.
- **Цвета для дальтоников.** Переключатель на вкладке «Монитор» переводит HUD, монитор и радар на цвета, которые различимы при красно-зелёном дальтонизме: синий — говорит, фиолетовый — шёпот, оранжевый — стены.
- **Метки на радаре различаются формой**, а не только цветом: квадрат — говорит, крестик — шепчет, ромб — за стеной. Легенда радара такая же.
- **Коды профиля.**
  - Кнопки «Скопировать код профиля» и «Вставить код профиля» на вкладке «Дистанция» передают вашу кривую, стены, материалы и эффекты одной строкой текста (`VP1:…`).
  - Админ сервера может загрузить код через `/vcd preset import <код>` и раздать код сервера через `/vcd preset export`.
- **Счётчик нагрузки.**
  - Монитор показывает, сколько времени за тик занимает работа самого аддона: стены, обход углов и эхо.
  - Если больше 2 мс, аддон сам начинает делать её реже.
  - `/vcd status` показывает то же для серверных стен для игроков без аддона.

#### Изменено
- В HUD тихий голос (между словами) теперь отмечен пустой меткой, а не тусклой.

## [1.6.0] - 2026-09-26

### English

#### Added
- **More materials.** There are 13 groups now:
  - new ones: metal (blocks of iron, gold, copper, netherite, anvils), earth & sand (everything dug with a shovel), soft blocks (hay, sponge, moss, sculk), ice, and **other blocks**;
  - *other blocks* covers every solid block not in the list, such as bedrock and blocks from other mods; before, they counted as stone.
  - Stone, wood and the new groups are recognised by the tool that mines the block, so blocks from newer versions and data packs fall into the right group.
- The wall preview also shows a block of dirt and an iron block wall.

#### Changed
- **Presets fit the server's voice range:**
  - Realistic, Clear and Stealth now set full volume in blocks: about 12, 24 and 7. Before, it was 60%, 80% and 35% of the range, so on a 48-block server Realistic stayed at full volume up to 29 blocks and Stealth up to 17.
  - The zone is kept within limits of the server's range, so a voice at a given distance sounds the same on servers with different ranges.
  - The preset you picked is fitted again when you join a server with another range, unless you changed the values yourself.
  - Settings that still hold one of the old presets move to the new one; walls are kept.
  - Server profiles set by preset name (`profile_preset`, zones) are fitted to the server's own range.
- **The distance graph is more compact.** It no longer stretches to the window's full height, and the sliders sit right under it.
- **The graph is easier to read:**
  - it has a volume scale on the left;
  - the edge of the full-volume zone is marked;
  - the whisper curve stays dashed on steep parts;
  - a mark on the distance scale shows where the whisper range ends.
- **The summary, legend and Listen button moved above the graph.**
- The wall preview panel is only as tall as its rows.
- **Files are now named after Voice Physics:** `voice-physics-fabric-…`, `voice-physics-neoforge-…`, `voice-physics-forge-…` and `voice-physics-bukkit-…` instead of `voicechat-audio-distance-…`. Delete the old file when you update, so the game or server does not load both. Settings are kept.

#### Fixed
- The Listen button looked disabled because the graph panel was drawn over it.

### Русский

#### Добавлено
- **Больше материалов.** Теперь их 13 групп:
  - новые: металл (блоки железа, золота, меди, незерита, наковальни), земля и песок (всё, что копается лопатой), мягкие блоки (сено, губка, мох, скалк), лёд и **остальные блоки**;
  - *остальные блоки* — все твёрдые блоки не из списка, например бедрок и блоки из других модов; раньше они считались камнем.
  - Камень, дерево и новые группы определяются по инструменту, которым добывается блок, поэтому блоки из новых версий и датапаков попадают в нужную группу.
- В превью стен добавлены блок земли и стена из блоков железа.

#### Изменено
- **Пресеты подстраиваются под дальность голоса сервера:**
  - «Реализм», «Чётко» и «Стелс» теперь задают полную громкость в блоках: примерно 12, 24 и 7. Раньше это было 60%, 80% и 35% от дальности, поэтому на сервере с 48 блоками «Реализм» держал полную громкость до 29 блоков, а «Стелс» — до 17.
  - Зона не выходит за пределы, заданные от дальности сервера, поэтому голос на одном и том же расстоянии звучит одинаково на серверах с разной дальностью.
  - Выбранный пресет подгоняется заново, когда вы заходите на сервер с другой дальностью, если вы не меняли значения сами.
  - Настройки, в которых остался один из старых пресетов, переходят на новый; стены сохраняются.
  - Профили сервера, заданные именем пресета (`profile_preset`, зоны), подгоняются под дальность самого сервера.
- **График дальности стал компактнее.** Он больше не растягивается на всю высоту окна, а ползунки идут сразу под ним.
- **График стало легче читать:**
  - слева появилась шкала громкости;
  - граница зоны полной громкости отмечена;
  - кривая шёпота остаётся пунктирной на крутых участках;
  - метка на шкале расстояния показывает, где заканчивается дальность шёпота.
- **Сводка, легенда и кнопка «Прослушать» переехали над графиком.**
- Панель превью стен теперь высотой по своим строкам.
- **Файлы теперь называются по имени Voice Physics:** `voice-physics-fabric-…`, `voice-physics-neoforge-…`, `voice-physics-forge-…` и `voice-physics-bukkit-…` вместо `voicechat-audio-distance-…`. При обновлении удалите старый файл, чтобы игра или сервер не загрузили оба. Настройки сохраняются.

#### Исправлено
- Кнопка «Прослушать» выглядела выключенной: панель графика рисовалась поверх неё.

## [1.5.0] - 2026-09-26

### English

#### Added
- **Sound around corners.** When a wall is between you and a speaker, the addon looks for a way round it through open blocks. If a doorway or window is close, the voice comes through it: less muffled than through the wall, and from the doorway's side. A switch on the Effects tab.
- **`/vcd` for server admins** on Fabric and Paper: `status`, `reload`, `profile`, `preset`, `walls`, `serverwalls`, `zones`. Changes are saved and sent to players with the addon right away. Operators (level 2+) and the console; on Paper the `vcd.admin` permission. Replies in English or Russian.
- **Sound zones.** A world (dimension) or, on Paper with WorldGuard, a region can have its own profile mode and preset. The profile follows players as they move, and the HUD names the zone.
- **The full addon on NeoForge 26.x:** settings screen, walls, echo, HUD, monitor and the server side, the same as on Fabric. Forge, and NeoForge for 1.20.1 / 1.21.x, stay lite.

#### Changed
- **One name everywhere: Voice Physics.** The settings title, the button in Simple Voice Chat's settings (now with a tooltip), the Controls category, chat messages and the mod list all use it.
- **Voice HUD:**
  - it now sits in the top right by default, clear of Simple Voice Chat's group list, and moves down when status effect icons are shown;
  - distances show their unit;
  - "nobody hears you" is shown quietly instead of as a warning;
  - in a voice chat group it says so, since the group hears you anywhere.
- **Monitor:**
  - shorter HUD buttons, and the corner button goes round the screen clockwise;
  - the radar has a legend.
- **The Listen button** turns into **Stop** while the voice plays.

### Русский

#### Добавлено
- **Звук из-за угла.** Если между вами и говорящим стена, аддон ищет путь в обход через открытые блоки. Если рядом есть проём или окно, голос проходит через него: глушится меньше, чем сквозь стену, и слышен со стороны проёма. Переключатель на вкладке «Эффекты».
- **`/vcd` для админов сервера** на Fabric и Paper: `status`, `reload`, `profile`, `preset`, `walls`, `serverwalls`, `zones`. Изменения сохраняются и сразу отправляются игрокам с аддоном. Для операторов (уровень 2+) и консоли; на Paper — право `vcd.admin`. Ответы на английском или русском.
- **Звуковые зоны.** У мира (измерения) или, на Paper с WorldGuard, у региона может быть свой режим и пресет профиля. Профиль следует за игроком, а HUD называет зону.
- **Полный аддон на NeoForge 26.x:** экран настроек, стены, эхо, HUD, монитор и серверная часть — как на Fabric. Forge, а также NeoForge для 1.20.1 / 1.21.x, остаются облегчёнными.

#### Изменено
- **Одно название везде — Voice Physics.** Заголовок настроек, кнопка в настройках Simple Voice Chat (теперь с подсказкой), раздел в «Управлении», сообщения в чате и список модов.
- **HUD голоса:**
  - по умолчанию теперь справа сверху, не перекрывая список группы Simple Voice Chat, и опускается ниже, когда видны значки эффектов;
  - у расстояний указаны единицы;
  - «вас никто не слышит» показывается спокойно, а не как предупреждение;
  - в группе голосового чата HUD пишет об этом: группа слышит вас где угодно.
- **Монитор:**
  - кнопки HUD стали короче, а кнопка угла обходит экран по часовой стрелке;
  - у радара появилась легенда.
- **Кнопка «Прослушать»** превращается в **«Стоп»**, пока звучит голос.

## [1.4.0] - 2026-09-25

### English

#### Added
- **Echo in caves and halls.** Rays from your head measure how closed and how big the space around you is; big caves and halls echo long, small rooms briefly, the open air not at all. The echo changes smoothly as you walk.
- **Voices under water** are dull and quiet, whether your head or the speaker's is in the water.
- **Rain and thunder** make far voices harder to hear under the open sky; close voices stay clear.
- **Effects tab:** a switch for echo, water and weather, the echo strength, and a live view of your surroundings. A server can include these in its profile. With Sound Physics Remastered installed, our echo and water stay off.
- **Voice HUD:** a small panel in a corner of the screen with who is talking nearby, how far and from which direction, and while you talk, how many players hear you (or your whisper) and how many cannot. Off, while talking or always; any corner; a key to switch it.
- **Monitor:** an arrow towards every player, and a radar view seen from above with the voice and whisper range as rings.
- **Voice chat state without the server addon:** with Simple Voice Chat 2.6.1+ the monitor and the HUD show who has voice chat disconnected or the sound off even on servers without the addon.
- **Listen to the curve:** a button on the Distance tab plays a voice walking away from you along the curve.
- **A hint in chat** the first time you join a world, on how to open the settings.
- **Languages:** Ukrainian, German, Spanish, Brazilian Portuguese and Chinese (Simplified).

### Русский

#### Добавлено
- **Эхо в пещерах и залах.** Лучи от вашей головы измеряют, насколько пространство вокруг закрытое и большое; в больших пещерах и залах эхо долгое, в маленьких комнатах — короткое, на открытом воздухе его нет. Эхо плавно меняется, пока вы идёте.
- **Голоса под водой** глухие и тихие — неважно, под водой ваша голова или голова говорящего.
- **Дождь и гроза** делают дальние голоса под открытым небом менее слышными; близкие голоса остаются чёткими.
- **Вкладка «Эффекты»:** переключатели эха, воды и погоды, сила эха и живая панель того, что вокруг. Сервер может включить их в свой профиль. Если установлен Sound Physics Remastered, наши эхо и вода выключены.
- **HUD голоса:** небольшая панель в углу экрана — кто рядом говорит, как далеко и с какой стороны, а пока говорите вы — сколько игроков вас (или ваш шёпот) слышат и сколько не слышат. Выкл., когда говорят или всегда; любой угол; клавиша для переключения.
- **Монитор:** стрелка к каждому игроку и вид «радар» сверху, где кольца — дальность голоса и шёпота.
- **Состояние голосового чата без аддона на сервере:** с Simple Voice Chat 2.6.1+ монитор и HUD показывают, у кого голосовой чат не подключён или выключен звук, даже на серверах без аддона.
- **Прослушивание кривой:** кнопка на вкладке «Дистанция» проигрывает голос, который уходит от вас по кривой.
- **Подсказка в чате** при первом входе в мир — как открыть настройки.
- **Языки:** украинский, немецкий, испанский, португальский (Бразилия) и китайский (упрощённый).

## [1.3.0] - 2026-09-25

### English

#### Added
- **The monitor lists everyone within voice range**, not only the people talking: talking players first, then the others by distance, each with their distance. Players you cannot see (spectators, invisible players) are left out.
- **Voice chat state of nearby players** when the server has the addon (Fabric or the Paper / Purpur / Spigot plugin): the monitor shows who has no Simple Voice Chat, has it disconnected, turned the sound off (and so will not hear you), or is in a voice chat group. The server sends it once a second, only about players the receiving player may see; players hidden by vanish plugins on Paper are never listed.

### Русский

#### Добавлено
- **Монитор показывает всех в радиусе голоса**, а не только говорящих: сначала те, кто говорит, затем остальные по расстоянию, у каждого — дистанция. Игроки, которых вы не видите (наблюдатели, невидимые), не показываются.
- **Состояние голосового чата у игроков рядом**, если на сервере есть аддон (Fabric или плагин для Paper / Purpur / Spigot): монитор показывает, у кого нет Simple Voice Chat, у кого он не подключён, кто выключил звук (и поэтому вас не услышит) и кто в группе голосового чата. Сервер присылает это раз в секунду и только про игроков, которых получатель может видеть; игроки, скрытые плагинами ваниша на Paper, в список не попадают.

## [1.2.4] - 2026-09-25

### English

#### Changed
- The summary above the graph names the loudness halfway through the fade, where the curves differ.

#### Fixed
- **The exponential curve was not exponential.** It used OpenAL's "exponent" model, a power law that at 100% falloff is the same curve as 1/r. It is now a true exponential: most of the loudness is gone soon after the full-volume distance.
- **1/r and the exponential curve no longer cut voices off at the edge.** Both stopped at 50% at the edge of the range, where Simple Voice Chat stops sending the voice, so it cut off from half volume to silence. Every curve now fades to silence at the edge (1/r over the last quarter of the range).
- The addon now applies the curve itself, the same function that draws the graph, instead of OpenAL's distance models, and no longer changes OpenAL's context-wide distance model. Per-player volume and muting in Simple Voice Chat keep working.

### Русский

#### Изменено
- Сводка над графиком показывает громкость на середине спада, где кривые различаются.

#### Исправлено
- **Экспоненциальная кривая не была экспонентой.** Она использовала модель OpenAL «exponent» — степенную функцию, которая при спаде 100% совпадает с 1/r. Теперь это настоящая экспонента: громкость уходит почти сразу за зоной полной громкости.
- **1/r и экспонента больше не обрывают голос у края.** Обе останавливались на 50% на краю дальности, где Simple Voice Chat перестаёт передавать голос, и он обрывался с половины громкости в тишину. Теперь каждая кривая сходит на нет к краю (1/r — в последней четверти дальности).
- Аддон теперь применяет кривую сам, той же функцией, что рисует график, а не через модели расстояния OpenAL, и больше не меняет общую для всего контекста модель расстояния OpenAL. Громкость игроков и mute в Simple Voice Chat работают как раньше.

## [1.2.3] - 2026-09-25

### English

#### Added
- **The Paper / Purpur / Spigot plugin is listed for Minecraft 1.20.1 – 26.3.** A CI check builds it against the 1.20.1 API and checks that every class, method, field and override it uses resolves the same way on every Paper release from 1.20.1 to 26.3. The one difference, `Sound` becoming an interface in 1.21.3, was reviewed: the plugin only reads its constants. Paper 1.20.5 cannot be checked, as its API snapshot is no longer downloadable.

#### Fixed
- The Modrinth upload no longer fails after uploading: it tried to unfeature older versions, which the token is not allowed to do.

### Русский

#### Добавлено
- **Плагин для Paper / Purpur / Spigot отмечен для Minecraft 1.20.1 – 26.3.** Проверка в CI собирает его против API 1.20.1 и проверяет, что каждый класс, метод, поле и переопределение, которые он использует, разрешаются одинаково на каждом релизе Paper от 1.20.1 до 26.3. Единственное различие — `Sound` стал интерфейсом в 1.21.3 — разобрано: плагин только читает его константы. Paper 1.20.5 проверить нельзя: снимок его API больше не скачивается.

#### Исправлено
- Загрузка на Modrinth больше не падает после загрузки: она пыталась снять отметку «featured» со старых версий, а токену это не разрешено.

## [1.2.2] - 2026-09-25

### English

#### Changed
- **New icon**, pixel art drawn by `docs/make-icon.py`: a speaker, sound waves and a stone-brick wall. The old icon was a JPEG saved as `.png`, which Minecraft cannot read, so Mod Menu showed no icon.

#### Added
- Releases are also uploaded to Modrinth and CurseForge.
- **Minecraft 26.1, 26.1.1, 26.1.2 and 26.2** with the same 26.x jar (it was 26.3 only). Screens moved from `Minecraft` to `Gui` in 26.2 and input switched to SDL in 26.3; the jar now picks the right API at runtime. It needs Fabric API 0.145.1 or newer.
- A CI check runs the 26.x jar against every 26.x release: every class, method, field and override the jar uses must resolve the same way as on 26.3, and the screen methods it finds by name must exist.

### Русский

#### Изменено
- **Новая иконка** в пиксельном стиле, её рисует `docs/make-icon.py`: динамик, звуковые волны и стена из каменного кирпича. Старая иконка была JPEG-файлом с расширением `.png`, который Minecraft не читает, поэтому Mod Menu показывал мод без иконки.

#### Добавлено
- Релизы также загружаются на Modrinth и CurseForge.
- **Minecraft 26.1, 26.1.1, 26.1.2 и 26.2** с тем же JAR для 26.x (раньше только 26.3). В 26.2 экраны переехали из `Minecraft` в `Gui`, а в 26.3 ввод перешёл на SDL; теперь JAR выбирает нужный API во время работы. Нужен Fabric API 0.145.1 или новее.
- Проверка в CI запускает JAR для 26.x на каждом релизе 26.x: каждый класс, метод, поле и переопределение, которые использует JAR, должны разрешаться так же, как на 26.3, а методы экранов, которые он ищет по имени, должны существовать.

## [1.2.1] - 2026-09-25

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
