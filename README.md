# 🎙️ Voice Physics — Simple Voice Chat addon

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.x%20%7C%2026.x-blue.svg?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Server](https://img.shields.io/badge/Server-Fabric%20%7C%20Paper%20%7C%20Purpur%20%7C%20Spigot-lightgrey.svg)](#versions-and-files)
[![Simple Voice Chat](https://img.shields.io/badge/Simple%20Voice%20Chat-2.4%2B-orange.svg)](https://modrinth.com/plugin/simple-voice-chat)
[![Build Status](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml/badge.svg)](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Shamanalle/voicechat-audio-distance?logo=github&color=brightgreen)](https://github.com/Shamanalle/voicechat-audio-distance/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**[English](#english)** · **[Русский](#русский)**

| Distance / Дистанция | Walls / Стены | Monitor / Монитор | Server profile / Профиль сервера |
|---|---|---|---|
| ![Distance](docs/images/ui-distance.png) | ![Walls](docs/images/ui-walls.png) | ![Monitor](docs/images/ui-monitor.png) | ![Server profile](docs/images/ui-server-enforced.png) |

<sub>Renders of the settings screen made outside the game with the mod's own layout and drawing code. · Рендеры экрана настроек вне игры тем же кодом раскладки и отрисовки, что в моде.</sub>

---

## English

An addon for **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** that shapes how voices fade with distance and muffles them through walls.

It works on either side, and each side is useful alone:
- **Client only** — you choose how *you* hear voices; nothing is needed on the server.
- **Server only** — players with plain Simple Voice Chat hear voices muffled through walls.
- **Both** — the server can share its sound profile, and the client gets exact whisper ranges while doing the wall muffling itself.

### Features

#### Distance curve (client)
- **Three curves:** linear (Simple Voice Chat's own), realistic 1/r and a true exponential. Each one fades to silence at the edge of the range, so voices never cut off abruptly.
- **Adjustable:** falloff, the distance heard at full volume, the volume at the edge of the range, and whisper falloff.
- **Live graph:**
  - shows the loudness at every distance;
  - hover it for the exact value in blocks, % and dB;
  - the whisper curve is dashed;
  - the people you hear right now appear as dots.
- **Edge volume** uses OpenAL `AL_MIN_GAIN` scaled by each player's own volume, so muted players stay muted.

#### Walls (client)
- Voices behind walls become **quieter and duller**: a 24 dB/octave low-pass filter plus broadband loss.
  - One stone wall at the default strength: about −8 dB, muffled above ~2.5 kHz.
  - Three stone walls: about −18 dB and ~600 Hz.
- **Real block shapes:** slabs, open doors, fences and carpets do not count as full cubes.
- **Soft edges:** 5 parallel rays instead of one, so a voice around a corner or through a doorway fades gradually instead of switching.
- **Materials:** 13 groups — stone, metal, earth & sand, wood, wool, soft blocks, glass, ice, doors, leaves, bars & fences, water & lava, and *other blocks* for everything else (bedrock, blocks from other mods). Wool and metal muffle more than stone, glass and leaves less. Every group's weight is adjustable on the *Materials* tab.
- **Smooth:** filter changes glide over ~90 ms without clicks. Without a wall the audio passes through bit for bit.
- **Sound Physics Remastered:** when it is installed, our wall muffling turns itself off so voices are not muffled twice.

#### Echo, water and weather (client)
- **Echo in caves and halls:** every half second 18 rays from your head measure how closed and how big the space around you is. A big cave or hall gives a long echo, a small room a short one, the open air none. The echo glides as you walk and has its own strength setting.
- **Under water:** when your head or the speaker's is under water, voices become dull (~600 Hz) and 10 dB quieter.
- **Rain and thunder:** under the open sky, rain takes up to 6 dB off far voices and a thunderstorm up to about 10 dB; close voices stay clear.
- **Around corners:** when a wall is between you, the addon looks for a way round it through open blocks (air, water, open doors and gates, fences). If a doorway or window is close, the voice comes through it: less muffled than through the wall, and from the doorway's side, like a voice from the next room through an open door.
- The *Effects* tab has a switch for each, the echo strength, and a live view of what is around you right now.
- With Sound Physics Remastered installed, our echo and water stay off (it does them itself); rain still works.

#### Voice HUD (client)
A small panel in a corner of the screen:
- who is talking nearby, how far away and from which direction (an arrow), whispering or behind a wall;
- **while you talk: how many players hear you** — within your voice range, or your whisper range while you whisper — and how many cannot (no voice chat, sound off);
- modes: off, while talking (default: only when someone speaks), always (also how many are in range); any of the four corners; a key in Controls cycles the modes.

#### Monitor (client)
Shows live:
- every player within voice range, talking or not, how far away and in which direction;
- how loud each voice reaches you and how much the walls take off;
- who has no Simple Voice Chat, has it disconnected, turned the sound off, or is in a voice chat group — from your own Simple Voice Chat (2.6.1+: disconnected, sound off) and, when the server has the addon, from the server (all of them);
- whether the server has the addon.

Talking players come first, then the others by distance. A **radar** view shows the same from above, with the voice and whisper range as rings. Only players you can see are listed: spectators (unless you are one), invisible players and, on Paper, players hidden by vanish plugins are left out.

#### Listen to the curve (client)
*Listen* on the Distance tab plays a voice walking away from you along the current curve, with a marker moving over the graph, so you hear the fade before you use it.

#### Languages
English, Russian, Ukrainian, German, Spanish, Brazilian Portuguese and Chinese (Simplified).

#### Presets (client)
- **Vanilla** — exactly like Simple Voice Chat.
- **Realistic** — natural falloff with walls.
- **Clear** — everyone stays understandable, for events.
- **Stealth** — hide-and-seek, horror.

The preset that matches your current settings is highlighted.

#### Server side (optional)
Available as part of the Fabric mod or as a plugin for **Paper, Purpur, Spigot and Bukkit**. Both work the same way and work with the same client.
- **Walls for everyone:** players without the addon hear voices muffled through walls. The server decodes the speaker's audio once, filters it for each listener behind a wall, and re-encodes it. Voices with a clear line of sight, group chat, spectators and other addons' audio are passed through untouched.
- **CPU limit:** at most `server_walls_max_streams` voices (default 24) are processed at once; everything above that passes through. Any error falls back to the original audio, so voice chat never goes silent because of the addon.
- **Server sound profile** for players who have the addon:
  - `suggest` — they get a chat notice and an *Apply server profile* button;
  - `enforce` — the server's profile is used while they play there (fair play for PvP and events); their own settings return when they leave.
- **Exact whisper range:** the server sends its real voice and whisper distances, so the whisper curve on the graph is exact.
- **Voice chat state of nearby players:** once a second the server tells each player with the addon who within voice range has no Simple Voice Chat, has it disconnected, turned the sound off, or is in a group, for the monitor.
- **Hot reload:** edits to the server settings file are picked up without a restart and sent to connected players.
- **Sound zones:** a world (dimension) or, on Paper with WorldGuard, a region can have its own profile mode and preset — a quiet library, a loud arena, a stealth dungeon. The profile follows players as they move, and the HUD names the zone.
- **`/vcd` command** for operators (level 2+) and the console:

  | Command | What it does |
  |---|---|
  | `/vcd` or `/vcd status` | Version, voice range, walls, players with the addon, profile, zones |
  | `/vcd reload` | Re-read the settings file |
  | `/vcd profile off\|suggest\|enforce` | How the profile is offered |
  | `/vcd preset vanilla\|realistic\|clear\|stealth\|custom` | The server's sound |
  | `/vcd walls 0-100\|off` | Wall strength for everyone, in % |
  | `/vcd serverwalls on\|off` | Walls for players without the addon |
  | `/vcd zones` | Worlds and regions with their own profile |

  Changes are saved to the settings file and sent to players with the addon right away. On Paper the permission is `vcd.admin` (operators by default). Replies are in English or Russian (`messages_language`).

### What works where

| | Client only | Server only | Both |
|---|---|---|---|
| Distance curve | ✅ | — | ✅ (the server profile can be suggested or enforced) |
| Wall muffling | ✅ locally | ✅ for players without the addon | ✅ locally; the server skips these players |
| Settings screen, monitor, HUD | ✅ | — | ✅ plus server status and every nearby player's voice chat state |
| Echo, water, weather | ✅ | — | ✅ (can be part of the server profile) |
| Whisper curve on the graph | approximate (½ of the range) | — | exact |

### Versions and files

| Loader | Minecraft | File | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voice-physics-fabric-1.6.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voice-physics-fabric-1.6.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **Fabric** | 26.1 – 26.3 | `voice-physics-fabric-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |
| **Paper / Purpur / Spigot / Bukkit** (server) | 1.20.1 – 26.3 | `voice-physics-bukkit-1.6.0.jar` | 17+ | Bukkit version |
| Forge | 1.20.1 | `voice-physics-forge-1.6.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| NeoForge / Forge | 1.21 – 1.21.11 | `voice-physics-{neoforge,forge}-1.6.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **NeoForge** | 26.1 – 26.3 | `voice-physics-neoforge-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |
| Forge | 26.1 – 26.3 | `voice-physics-forge-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |

- **Fabric** is the full version, on the client and on the server. It needs [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is optional.
- **Paper / Purpur / Spigot / Bukkit** is the server side as a plugin: walls for players without the addon, and the server profile for players with it. Players can join with any client: with the Fabric addon, without it, or without mods at all. The plugin is compiled against the 1.20.1 API and checked in CI against every Paper release from 1.20.1 to 26.3: every class, method, field and override it uses resolves the same way (Paper 1.20.5 cannot be checked: its API snapshot is no longer downloadable).
- **NeoForge for 26.x** is the full version, the same as Fabric: client and server, settings screen, walls, HUD, monitor, `/vcd`. The settings are also under *Mods → Voice Physics → Config*.
- **Forge, and NeoForge for 1.20.1 / 1.21.x,** are a lite version: distance curves only, configured in `config/vc-audio-distance.properties`. There is no settings screen, no walls, no monitor and no server side (those jars are built for Fabric's class names; 26.x has one set of names for every loader).
- The 1.21.x jar was checked against the signatures of every Minecraft method it uses on each release from 1.21 to 1.21.11.
- The 26.x jar is built for 26.3 and checked in CI on every 26.x release (26.1 – 26.3): every class, method, field and override the jar uses resolves on each one exactly as on 26.3. Where 26.x changed (screens moved to `Gui` in 26.2, SDL input in 26.3), the jar picks the right API at runtime.

### Installation

**Client**
1. Install [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the matching `.jar` into `.minecraft/mods/`.
3. In game, open the voice chat settings (`V`) → **Voice Physics…**. The screen is also available from Mod Menu or with your own key (*Options → Controls*, unbound by default).

Changes are heard immediately. *Done* or `Esc` saves; *Cancel* restores everything.

**Server (Fabric)**
1. Put the same `.jar` into the server's `mods/` folder, next to Simple Voice Chat and Fabric API.
2. Start the server once; it creates `config/vc-audio-distance-server.properties`.
3. Walls for players without the addon are on by default. To share a profile, set `profile_mode` to `suggest` or `enforce` and pick a `profile_preset`. Every key in the file has a comment in English and Russian, and the file is re-read automatically.

**Server (Paper / Purpur / Spigot / Bukkit)**
1. Put `voice-physics-bukkit-1.6.0.jar` into the server's `plugins/` folder, next to the Bukkit version of Simple Voice Chat.
2. Start the server once; it creates `plugins/VoicechatAudioDistance/vc-audio-distance-server.properties`.
3. The settings are the same as on Fabric (see below), and the file is also re-read automatically.

### Client settings — `config/vc-audio-distance.properties`

| Key | Range | Default | What it does |
|---|---|---|---|
| `distance_model` | `linear` / `realistic_inverse` / `exponential` | `linear` | Shape of the curve |
| `attenuation_factor` | 0.0 – 1.0 | 1.0 | Falloff strength |
| `openal_reference_ratio` | 0.05 – 1.0 | 0.5 | Share of the range heard at full volume |
| `min_volume_fraction` | 0.0 – 0.5 | 0.0 | Volume at the edge of the range |
| `whisper_multiplier` | 0.5 – 2.0 | 1.0 | Falloff multiplier while whispering |
| `occlusion_enabled` | true / false | true | Wall muffling |
| `occlusion_strength` | 0.0 – 1.0 | 0.6 | Wall muffling strength |
| `material.<id>` | 0.0 – 3.0 | see the *Materials* tab | How much one block muffles; stone = 1.0 |
| `reverb_enabled` | true / false | true | Echo in caves and halls |
| `reverb_strength` | 0.0 – 1.0 | 0.6 | Echo strength |
| `underwater_enabled` | true / false | true | Dull, quiet voices under water |
| `weather_enabled` | true / false | true | Rain and thunder cover far voices |
| `diffraction_enabled` | true / false | true | Voices come round walls through doorways |
| `hud_mode` | `off` / `talking` / `always` | `talking` | Voice HUD |
| `hud_corner` | `top_left` / `top_right` / `bottom_left` / `bottom_right` | `top_right` | Corner of the voice HUD |

### Server settings — `config/vc-audio-distance-server.properties`

On Paper / Purpur / Spigot / Bukkit the file is `plugins/VoicechatAudioDistance/vc-audio-distance-server.properties`.

The file has six sections, and every key has a comment in English and Russian. Changes apply within 2 seconds without a restart. The voice and whisper range itself is set in Simple Voice Chat (`max_voice_distance`, `whisper_distance`).

**1. Walls, for every player**

| Key | Values | Default | What it does |
|---|---|---|---|
| `walls_strength` | 0.0 – 1.0 | 0.6 | How strongly walls muffle voices; 0 turns walls off |
| `material.<id>` | 0.0 – 3.0 | stone 1.0, metal 1.3, earth 0.9, wood 0.7, wool 1.4, soft 1.2, glass 0.4, ice 0.7, door 0.6, leaves 0.15, thin 0.2, liquid 0.35, other 1.0 | How much one block muffles; stone = 1.0 |

**2. Players without the addon**

| Key | Values | Default | What it does |
|---|---|---|---|
| `server_walls` | true / false | true | The server muffles voices through walls for them |
| `server_walls_max_streams` | 0 – 512 | 24 | Most voices muffled at once (CPU limit); voices above it are heard without walls |

**3. Players with the addon**

| Key | Values | Default | What it does |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | Keep their own settings / offer the profile / use it while they play here |
| `profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` / `custom` | `custom` | The server's sound profile; `custom` uses the `profile.*` values |
| `profile.*` | the curve keys of the client file | client defaults | Custom profile: `distance_model`, `attenuation_factor`, `openal_reference_ratio`, `min_volume_fraction`, `whisper_multiplier` |

**4. Echo, water and weather in the profile**

| Key | Values | Default | What it does |
|---|---|---|---|
| `profile.reverb_enabled`, `profile.reverb_strength`, `profile.underwater_enabled`, `profile.weather_enabled`, `profile.diffraction_enabled` | as in the client file | as in the client file | Part of the profile whatever `profile_preset` says, so an event can turn the echo off for everyone |

**5. Zones**

| Key | Values | What it does |
|---|---|---|
| `zone.world.<world>.profile_mode` | `off` / `suggest` / `enforce` | How the profile is offered in this world |
| `zone.world.<world>.profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` | The preset in this world |
| `zone.region.<region id>.profile_mode`, `zone.region.<region id>.profile_preset` | as above | The same for a WorldGuard region (Paper); a region wins over its world |

On Paper the world is its folder name (`world_nether`); on Fabric it is the dimension (`the_nether`, or `minecraft:the_nether` written as `minecraft\:the_nether`). What a zone leaves out comes from section 3; walls always come from section 1.

**6. Messages**

| Key | Values | Default | What it does |
|---|---|---|---|
| `messages_language` | `en` / `ru` | `en` | Language of the `/vcd` replies |

Walls always come from section 1, whichever preset is chosen. Older files are rewritten in this format on the first start, keeping their values.

### Building

JDK 25 is required; the 1.20 and 1.21 modules are compiled with `--release 17` / `21`.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test   # audio, server, config and translation tests
./gradlew build          # every jar in build/libs/
```

The project layout is described in [CONTRIBUTING.md](CONTRIBUTING.md).

### License

[MIT](LICENSE). Author: **Shamanalle**.

---

## Русский

Аддон для **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**: настраивает, как голоса затихают с расстоянием, и глушит их за стенами.

Работает на любой стороне, и каждая сторона полезна сама по себе:
- **Только клиент** — вы сами решаете, как слышите голоса *вы*; на сервер ничего ставить не нужно.
- **Только сервер** — игроки с обычным Simple Voice Chat слышат голоса приглушёнными за стенами.
- **Вместе** — сервер может передать свой профиль звука, а клиент получает точную дальность шёпота и сам глушит стены.

### Возможности

#### Кривая громкости (клиент)
- **Три кривые:** линейная (как в Simple Voice Chat), реалистичная 1/r и настоящая экспоненциальная. Каждая сходит на нет к краю дальности, поэтому голоса никогда не обрываются резко.
- **Настраивается:** сила спада, дистанция с полной громкостью, громкость на краю слышимости и спад шёпота.
- **Живой график:**
  - показывает громкость на каждой дистанции;
  - при наведении — точное значение в блоках, % и дБ;
  - кривая шёпота нарисована пунктиром;
  - люди, которых вы слышите прямо сейчас, отмечены точками.
- **Громкость на краю** задаётся через OpenAL `AL_MIN_GAIN` с учётом громкости каждого игрока, поэтому замьюченные остаются замьюченными.

#### Стены (клиент)
- Голос за стеной становится **тише и глуше**: фильтр нижних частот 24 дБ/октаву плюс общее ослабление.
  - Одна каменная стена при силе по умолчанию — около −8 дБ, глухо выше ~2,5 кГц.
  - Три каменные стены — около −18 дБ и ~600 Гц.
- **Реальная форма блоков:** полублоки, открытые двери, заборы и ковры не считаются целым кубом.
- **Мягкие края:** 5 параллельных лучей вместо одного, поэтому голос из-за угла или через дверной проём глохнет плавно, а не рывком.
- **Материалы:** 13 групп — камень, металл, земля и песок, дерево, шерсть, мягкие блоки, стекло, лёд, двери, листва, решётки и заборы, вода и лава и *остальные блоки* для всего прочего (бедрок, блоки из других модов). Шерсть и металл глушат сильнее камня, стекло и листва — слабее. Вес каждой группы меняется на вкладке «Материалы».
- **Плавно:** параметры фильтра меняются за ~90 мс, без щелчков. Без стены звук проходит без изменений, бит в бит.
- **Sound Physics Remastered:** если он установлен, наше приглушение стенами выключается само, чтобы голос не глушился дважды.

#### Эхо, вода и погода (клиент)
- **Эхо в пещерах и залах:** раз в полсекунды 18 лучей от вашей головы измеряют, насколько пространство вокруг закрытое и большое. Большая пещера или зал дают долгое эхо, маленькая комната — короткое, открытый воздух — никакого. Эхо плавно меняется, пока вы идёте, у него своя настройка силы.
- **Под водой:** когда ваша голова или голова говорящего под водой, голоса становятся глухими (~600 Гц) и на 10 дБ тише.
- **Дождь и гроза:** под открытым небом дождь отнимает у дальних голосов до 6 дБ, гроза — примерно до 10 дБ; близкие голоса остаются чёткими.
- **Из-за угла:** если между вами стена, аддон ищет путь в обход через открытые блоки (воздух, вода, открытые двери и калитки, заборы). Если рядом есть проём или окно, голос проходит через него: глушится меньше, чем сквозь стену, и слышен со стороны проёма — как голос из соседней комнаты через открытую дверь.
- На вкладке «Эффекты» — переключатель для каждого, сила эха и живая панель того, что вокруг вас сейчас.
- Если установлен Sound Physics Remastered, наши эхо и вода выключены (он делает их сам); дождь работает.

#### HUD голоса (клиент)
Небольшая панель в углу экрана:
- кто рядом говорит, как далеко и с какой стороны (стрелка), шепчет ли и не за стеной ли;
- **пока говорите вы — сколько игроков вас слышат**: в радиусе голоса, а когда шепчете — в радиусе шёпота, и сколько не слышат (нет голосового чата, выключен звук);
- режимы: выкл., когда говорят (по умолчанию: только пока кто-то говорит), всегда (ещё и сколько человек в радиусе); любой из четырёх углов; клавиша в «Управлении» переключает режимы.

#### Монитор (клиент)
Показывает в реальном времени:
- всех игроков в радиусе голоса, говорят они или нет, как далеко и в какой стороне;
- с какой громкостью доходит каждый голос и сколько отнимают стены;
- у кого нет Simple Voice Chat, у кого он не подключён, кто выключил звук и кто в группе голосового чата — от вашего Simple Voice Chat (2.6.1+: не подключён, звук выключен) и, если на сервере есть аддон, от сервера (всё);
- есть ли аддон на сервере.

Сначала идут те, кто говорит, затем остальные по расстоянию. Вид **«радар»** показывает то же сверху, кольца — дальность голоса и шёпота. В списке только те, кого вы видите: наблюдатели (если вы сами не наблюдатель), невидимые игроки и, на Paper, игроки, скрытые плагинами ваниша, не показываются.

#### Прослушивание кривой (клиент)
Кнопка «Прослушать» на вкладке «Дистанция» проигрывает голос, который уходит от вас по текущей кривой, а по графику движется метка, — спад слышно ещё до игры.

#### Языки
Английский, русский, украинский, немецкий, испанский, португальский (Бразилия) и китайский (упрощённый).

#### Пресеты (клиент)
- **Ваниль** — ровно как Simple Voice Chat.
- **Реализм** — естественный спад со стенами.
- **Чётко** — всех хорошо слышно, для ивентов.
- **Стелс** — прятки, хорроры.

Пресет, совпадающий с текущими настройками, подсвечивается.

#### Серверная часть (по желанию)
Есть в составе мода для Fabric и в виде плагина для **Paper, Purpur, Spigot и Bukkit**. Оба варианта работают одинаково и с тем же клиентом.
- **Стены для всех:** игроки без аддона слышат голоса приглушёнными за стенами. Сервер один раз декодирует звук говорящего, фильтрует его для каждого слушателя за стеной и кодирует заново. Голоса без преград, групповой чат, наблюдатели и звук других аддонов проходят без изменений.
- **Ограничение нагрузки:** одновременно обрабатывается не больше `server_walls_max_streams` голосов (по умолчанию 24), остальные проходят как есть. При любой ошибке уходит исходный звук, так что голосовой чат из-за аддона не замолчит.
- **Профиль звука сервера** для игроков с аддоном:
  - `suggest` — они получают сообщение в чате и кнопку «Применить профиль сервера»;
  - `enforce` — профиль сервера действует, пока они на нём играют (честная игра в PvP и на ивентах); их собственные настройки возвращаются при выходе.
- **Точная дальность шёпота:** сервер передаёт настоящие дальности голоса и шёпота, поэтому кривая шёпота на графике точная.
- **Состояние голосового чата у игроков рядом:** раз в секунду сервер сообщает каждому игроку с аддоном, у кого в радиусе голоса нет Simple Voice Chat, у кого он не подключён, кто выключил звук и кто в группе, — для монитора.
- **Горячая перезагрузка:** изменения в файле настроек сервера подхватываются без перезапуска и отправляются подключённым игрокам.
- **Звуковые зоны:** у мира (измерения) или, на Paper с WorldGuard, у региона может быть свой режим и пресет профиля — тихая библиотека, громкая арена, стелс-подземелье. Профиль следует за игроком, а HUD называет зону.
- **Команда `/vcd`** для операторов (уровень 2+) и консоли:

  | Команда | Что делает |
  |---|---|
  | `/vcd` или `/vcd status` | Версия, дальность голоса, стены, игроки с аддоном, профиль, зоны |
  | `/vcd reload` | Перечитать файл настроек |
  | `/vcd profile off\|suggest\|enforce` | Как предлагать профиль |
  | `/vcd preset vanilla\|realistic\|clear\|stealth\|custom` | Звук сервера |
  | `/vcd walls 0-100\|off` | Сила стен для всех, в % |
  | `/vcd serverwalls on\|off` | Стены для игроков без аддона |
  | `/vcd zones` | Миры и регионы со своим профилем |

  Изменения сохраняются в файл настроек и сразу отправляются игрокам с аддоном. На Paper право — `vcd.admin` (по умолчанию у операторов). Ответы на английском или русском (`messages_language`).

### Что где работает

| | Только клиент | Только сервер | Вместе |
|---|---|---|---|
| Кривая громкости | ✅ | — | ✅ (профиль сервера можно рекомендовать или закрепить) |
| Приглушение стенами | ✅ у себя | ✅ для игроков без аддона | ✅ у себя; сервер этих игроков пропускает |
| Экран настроек, монитор, HUD | ✅ | — | ✅ плюс статус сервера и состояние голосового чата у всех игроков рядом |
| Эхо, вода, погода | ✅ | — | ✅ (могут входить в профиль сервера) |
| Кривая шёпота на графике | примерная (½ дальности) | — | точная |

### Версии и файлы

| Загрузчик | Minecraft | Файл | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voice-physics-fabric-1.6.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voice-physics-fabric-1.6.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **Fabric** | 26.1 – 26.3 | `voice-physics-fabric-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |
| **Paper / Purpur / Spigot / Bukkit** (сервер) | 1.20.1 – 26.3 | `voice-physics-bukkit-1.6.0.jar` | 17+ | версия для Bukkit |
| Forge | 1.20.1 | `voice-physics-forge-1.6.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| NeoForge / Forge | 1.21 – 1.21.11 | `voice-physics-{neoforge,forge}-1.6.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **NeoForge** | 26.1 – 26.3 | `voice-physics-neoforge-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |
| Forge | 26.1 – 26.3 | `voice-physics-forge-1.6.0+mc26.x.jar` | 25+ | 2.6.0+ |

- **Fabric** — полная версия, на клиенте и на сервере. Нужен [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) — по желанию.
- **Paper / Purpur / Spigot / Bukkit** — серверная часть в виде плагина: стены для игроков без аддона и профиль сервера для игроков с ним. Заходить можно с любым клиентом: с аддоном для Fabric, без него или совсем без модов. Плагин собран против API 1.20.1 и в CI проверяется на каждом релизе Paper от 1.20.1 до 26.3: каждый класс, метод, поле и переопределение, которые он использует, разрешаются одинаково (Paper 1.20.5 проверить нельзя: снимок его API больше не скачивается).
- **NeoForge для 26.x** — полная версия, как на Fabric: клиент и сервер, экран настроек, стены, HUD, монитор, `/vcd`. Настройки есть и в «Моды → Voice Physics → Настроить».
- **Forge, а также NeoForge для 1.20.1 / 1.21.x,** — облегчённая версия: только кривые громкости, настройка в `config/vc-audio-distance.properties`. Нет экрана настроек, стен, монитора и серверной части (эти файлы собраны под имена классов Fabric; в 26.x имена одни для всех загрузчиков).
- JAR для 1.21.x проверен по сигнатурам каждого используемого метода Minecraft на всех версиях с 1.21 по 1.21.11.
- JAR для 26.x собран под 26.3 и в CI проверяется на каждом релизе 26.x (26.1 – 26.3): каждый класс, метод, поле и переопределение, которые использует JAR, разрешаются на каждой версии так же, как на 26.3. Там, где 26.x менялся (экраны переехали в `Gui` в 26.2, ввод через SDL в 26.3), JAR выбирает нужный API во время работы.

### Установка

**Клиент**
1. Установите [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) и [Fabric API](https://modrinth.com/mod/fabric-api).
2. Положите подходящий `.jar` в `.minecraft/mods/`.
3. В игре откройте настройки голосового чата (`V`) → **«Voice Physics…»**. Экран также открывается через Mod Menu или своей клавишей (*Настройки → Управление*, по умолчанию не назначена).

Изменения слышны сразу. «Готово» или `Esc` сохраняют, «Отмена» возвращает всё как было.

**Сервер (Fabric)**
1. Положите тот же `.jar` в папку `mods/` сервера, рядом с Simple Voice Chat и Fabric API.
2. Запустите сервер один раз — он создаст `config/vc-audio-distance-server.properties`.
3. Стены для игроков без аддона включены по умолчанию. Чтобы передавать профиль, поставьте `profile_mode` в `suggest` или `enforce` и выберите `profile_preset`. У каждого ключа в файле есть комментарий на английском и русском, файл перечитывается автоматически.

**Сервер (Paper / Purpur / Spigot / Bukkit)**
1. Положите `voice-physics-bukkit-1.6.0.jar` в папку `plugins/` сервера, рядом с версией Simple Voice Chat для Bukkit.
2. Запустите сервер один раз — он создаст `plugins/VoicechatAudioDistance/vc-audio-distance-server.properties`.
3. Настройки те же, что на Fabric (см. ниже), файл тоже перечитывается автоматически.

### Настройки клиента — `config/vc-audio-distance.properties`

| Ключ | Диапазон | По умолчанию | Что делает |
|---|---|---|---|
| `distance_model` | `linear` / `realistic_inverse` / `exponential` | `linear` | Форма кривой |
| `attenuation_factor` | 0.0 – 1.0 | 1.0 | Сила спада |
| `openal_reference_ratio` | 0.05 – 1.0 | 0.5 | Доля дальности с полной громкостью |
| `min_volume_fraction` | 0.0 – 0.5 | 0.0 | Громкость на краю слышимости |
| `whisper_multiplier` | 0.5 – 2.0 | 1.0 | Множитель спада шёпота |
| `occlusion_enabled` | true / false | true | Приглушение стенами |
| `occlusion_strength` | 0.0 – 1.0 | 0.6 | Сила приглушения стенами |
| `material.<id>` | 0.0 – 3.0 | см. вкладку «Материалы» | Насколько глушит один блок; камень = 1.0 |
| `reverb_enabled` | true / false | true | Эхо в пещерах и залах |
| `reverb_strength` | 0.0 – 1.0 | 0.6 | Сила эха |
| `underwater_enabled` | true / false | true | Глухие, тихие голоса под водой |
| `weather_enabled` | true / false | true | Дождь и гроза заглушают дальние голоса |
| `diffraction_enabled` | true / false | true | Голоса обходят стены через проёмы |
| `hud_mode` | `off` / `talking` / `always` | `talking` | HUD голоса |
| `hud_corner` | `top_left` / `top_right` / `bottom_left` / `bottom_right` | `top_right` | Угол экрана для HUD |

### Настройки сервера — `config/vc-audio-distance-server.properties`

На Paper / Purpur / Spigot / Bukkit файл находится в `plugins/VoicechatAudioDistance/vc-audio-distance-server.properties`.

В файле шесть разделов, у каждого ключа есть комментарий на английском и русском. Изменения применяются в течение 2 секунд без перезапуска. Сама дальность голоса и шёпота задаётся в Simple Voice Chat (`max_voice_distance`, `whisper_distance`).

**1. Стены, для всех игроков**

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `walls_strength` | 0.0 – 1.0 | 0.6 | Насколько сильно стены глушат голоса; 0 выключает стены |
| `material.<id>` | 0.0 – 3.0 | камень 1.0, металл 1.3, земля 0.9, дерево 0.7, шерсть 1.4, мягкие 1.2, стекло 0.4, лёд 0.7, двери 0.6, листва 0.15, решётки и заборы 0.2, жидкости 0.35, остальные 1.0 | Насколько глушит один блок; камень = 1.0 |

**2. Игроки без аддона**

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `server_walls` | true / false | true | Сервер глушит для них голоса за стенами |
| `server_walls_max_streams` | 0 – 512 | 24 | Сколько голосов глушится одновременно (ограничение нагрузки); голоса сверх лимита слышно без стен |

**3. Игроки с аддоном**

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | Свои настройки / предложить профиль / включить его, пока игрок здесь |
| `profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` / `custom` | `custom` | Профиль звука сервера; `custom` берёт значения `profile.*` |
| `profile.*` | ключи кривой из файла клиента | как у клиента | Свой профиль: `distance_model`, `attenuation_factor`, `openal_reference_ratio`, `min_volume_fraction`, `whisper_multiplier` |

**4. Эхо, вода и погода в профиле**

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `profile.reverb_enabled`, `profile.reverb_strength`, `profile.underwater_enabled`, `profile.weather_enabled`, `profile.diffraction_enabled` | как в файле клиента | как в файле клиента | Входят в профиль при любом `profile_preset` — например, на ивенте можно выключить эхо всем |

**5. Зоны**

| Ключ | Значения | Что делает |
|---|---|---|
| `zone.world.<мир>.profile_mode` | `off` / `suggest` / `enforce` | Как предлагать профиль в этом мире |
| `zone.world.<мир>.profile_preset` | `vanilla` / `realistic` / `clear` / `stealth` | Пресет в этом мире |
| `zone.region.<id региона>.profile_mode`, `zone.region.<id региона>.profile_preset` | как выше | То же для региона WorldGuard (Paper); регион главнее своего мира |

На Paper мир — это имя его папки (`world_nether`); на Fabric — измерение (`the_nether` или `minecraft:the_nether`, записанное как `minecraft\:the_nether`). Чего в зоне нет, берётся из раздела 3; стены всегда из раздела 1.

**6. Сообщения**

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `messages_language` | `en` / `ru` | `en` | Язык ответов `/vcd` |

Стены всегда берутся из раздела 1, какой бы пресет ни был выбран. Старые файлы при первом запуске переписываются в этот формат с сохранением значений.

### Сборка

Нужен JDK 25; модули 1.20 и 1.21 собираются с `--release 17` / `21`.

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test   # тесты звука, сервера, конфига и переводов
./gradlew build          # все JAR в build/libs/
```

Устройство проекта описано в [CONTRIBUTING.md](CONTRIBUTING.md).

### Лицензия

[MIT](LICENSE). Автор: **Shamanalle**.
