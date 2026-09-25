# 🎙️ VoiceChat Audio Distance

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.x%20%7C%2026.3-blue.svg?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&logoColor=white)](https://fabricmc.net/)
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
- **Three curves:** linear (Simple Voice Chat's own), realistic 1/r and exponential.
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
- **Materials:** wool muffles more than stone, glass and leaves less. Every material's weight is adjustable on the *Materials* tab.
- **Smooth:** filter changes glide over ~90 ms without clicks. Without a wall the audio passes through bit for bit.
- **Sound Physics Remastered:** when it is installed, our wall muffling turns itself off so voices are not muffled twice.

#### Monitor (client)
Shows live:
- who is talking and how far away;
- how loud each voice reaches you and how much the walls take off;
- whether the server has the addon.

#### Presets (client)
- **Vanilla** — exactly like Simple Voice Chat.
- **Realistic** — natural falloff with walls.
- **Clear** — everyone stays understandable, for events.
- **Stealth** — hide-and-seek, horror.

The preset that matches your current settings is highlighted.

#### Server side (optional)
- **Walls for everyone:** players without the addon hear voices muffled through walls. The server decodes the speaker's audio once, filters it for each listener behind a wall, and re-encodes it. Voices with a clear line of sight, group chat, spectators and other addons' audio are passed through untouched.
- **CPU limit:** at most `server_walls_max_streams` voices (default 24) are processed at once; everything above that passes through. Any error falls back to the original audio, so voice chat never goes silent because of the addon.
- **Server sound profile** for players who have the addon:
  - `suggest` — they get a chat notice and an *Apply server profile* button;
  - `enforce` — the server's profile is used while they play there (fair play for PvP and events); their own settings return when they leave.
- **Exact whisper range:** the server sends its real voice and whisper distances, so the whisper curve on the graph is exact.
- **Hot reload:** edits to the server settings file are picked up without a restart and sent to connected players.

### What works where

| | Client only | Server only | Both |
|---|---|---|---|
| Distance curve | ✅ | — | ✅ (the server profile can be suggested or enforced) |
| Wall muffling | ✅ locally | ✅ for players without the addon | ✅ locally; the server skips these players |
| Settings screen and monitor | ✅ | — | ✅ plus server status |
| Whisper curve on the graph | approximate (½ of the range) | — | exact |

### Versions and files

| Loader | Minecraft | File | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voicechat-audio-distance-fabric-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voicechat-audio-distance-fabric-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **Fabric** | 26.3 | `voicechat-audio-distance-fabric-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |
| Forge | 1.20.1 | `voicechat-audio-distance-forge-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| NeoForge / Forge | 1.21 – 1.21.11 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| NeoForge / Forge | 26.3 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |

- **Fabric** is the full version, on the client and on the server. It needs [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) is optional.
- **Forge / NeoForge** is a lite version: distance curves only, configured in `config/vc-audio-distance.properties`. There is no settings screen, no walls, no monitor and no server side.
- **Paper / Bukkit** servers are not supported yet.
- The 1.21.x jar was checked against the signatures of every Minecraft method it uses on each release from 1.21 to 1.21.11.

### Installation

**Client**
1. Install [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put the matching `.jar` into `.minecraft/mods/`.
3. In game, open the voice chat settings (`V`) → **Voice distance & walls…**. The screen is also available from Mod Menu or with your own key (*Options → Controls*, unbound by default).

Changes are heard immediately. *Done* or `Esc` saves; *Cancel* restores everything.

**Server (Fabric)**
1. Put the same `.jar` into the server's `mods/` folder, next to Simple Voice Chat and Fabric API.
2. Start the server once; it creates `config/vc-audio-distance-server.properties`.
3. Walls for players without the addon are on by default. To share a profile, set `profile_mode` to `suggest` or `enforce` and edit the `profile.*` keys. The file is re-read automatically.

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

### Server settings — `config/vc-audio-distance-server.properties`

| Key | Values | Default | What it does |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | How players with the addon get the profile |
| `server_walls` | true / false | true | Wall muffling for players without the addon |
| `server_walls_max_streams` | 0 – 512 | 24 | Most voices re-encoded at once (CPU limit) |
| `profile.*` | same keys as the client file | client defaults | The server's sound profile; its `occlusion_*` and `material.*` also drive server walls |

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

[MIT](LICENSE). Author: **Kasper / Shamanalle**.

---

## Русский

Аддон для **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**: настраивает, как голоса затихают с расстоянием, и глушит их за стенами.

Работает на любой стороне, и каждая сторона полезна сама по себе:
- **Только клиент** — вы сами решаете, как слышите голоса *вы*; на сервер ничего ставить не нужно.
- **Только сервер** — игроки с обычным Simple Voice Chat слышат голоса приглушёнными за стенами.
- **Вместе** — сервер может передать свой профиль звука, а клиент получает точную дальность шёпота и сам глушит стены.

### Возможности

#### Кривая громкости (клиент)
- **Три кривые:** линейная (как в Simple Voice Chat), реалистичная 1/r и экспоненциальная.
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
- **Материалы:** шерсть глушит сильнее камня, стекло и листва — слабее. Вес каждого материала меняется на вкладке «Материалы».
- **Плавно:** параметры фильтра меняются за ~90 мс, без щелчков. Без стены звук проходит без изменений, бит в бит.
- **Sound Physics Remastered:** если он установлен, наше приглушение стенами выключается само, чтобы голос не глушился дважды.

#### Монитор (клиент)
Показывает в реальном времени:
- кто говорит и на каком расстоянии;
- с какой громкостью доходит каждый голос и сколько отнимают стены;
- есть ли аддон на сервере.

#### Пресеты (клиент)
- **Ваниль** — ровно как Simple Voice Chat.
- **Реализм** — естественный спад со стенами.
- **Чётко** — всех хорошо слышно, для ивентов.
- **Стелс** — прятки, хорроры.

Пресет, совпадающий с текущими настройками, подсвечивается.

#### Серверная часть (по желанию)
- **Стены для всех:** игроки без аддона слышат голоса приглушёнными за стенами. Сервер один раз декодирует звук говорящего, фильтрует его для каждого слушателя за стеной и кодирует заново. Голоса без преград, групповой чат, наблюдатели и звук других аддонов проходят без изменений.
- **Ограничение нагрузки:** одновременно обрабатывается не больше `server_walls_max_streams` голосов (по умолчанию 24), остальные проходят как есть. При любой ошибке уходит исходный звук, так что голосовой чат из-за аддона не замолчит.
- **Профиль звука сервера** для игроков с аддоном:
  - `suggest` — они получают сообщение в чате и кнопку «Применить профиль сервера»;
  - `enforce` — профиль сервера действует, пока они на нём играют (честная игра в PvP и на ивентах); их собственные настройки возвращаются при выходе.
- **Точная дальность шёпота:** сервер передаёт настоящие дальности голоса и шёпота, поэтому кривая шёпота на графике точная.
- **Горячая перезагрузка:** изменения в файле настроек сервера подхватываются без перезапуска и отправляются подключённым игрокам.

### Что где работает

| | Только клиент | Только сервер | Вместе |
|---|---|---|---|
| Кривая громкости | ✅ | — | ✅ (профиль сервера можно рекомендовать или закрепить) |
| Приглушение стенами | ✅ у себя | ✅ для игроков без аддона | ✅ у себя; сервер этих игроков пропускает |
| Экран настроек и монитор | ✅ | — | ✅ плюс статус сервера |
| Кривая шёпота на графике | примерная (½ дальности) | — | точная |

### Версии и файлы

| Загрузчик | Minecraft | Файл | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voicechat-audio-distance-fabric-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voicechat-audio-distance-fabric-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **Fabric** | 26.3 | `voicechat-audio-distance-fabric-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |
| Forge | 1.20.1 | `voicechat-audio-distance-forge-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| NeoForge / Forge | 1.21 – 1.21.11 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| NeoForge / Forge | 26.3 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |

- **Fabric** — полная версия, на клиенте и на сервере. Нужен [Fabric API](https://modrinth.com/mod/fabric-api); [Mod Menu](https://modrinth.com/mod/modmenu) — по желанию.
- **Forge / NeoForge** — облегчённая версия: только кривые громкости, настройка в `config/vc-audio-distance.properties`. Нет экрана настроек, стен, монитора и серверной части.
- Серверы **Paper / Bukkit** пока не поддерживаются.
- JAR для 1.21.x проверен по сигнатурам каждого используемого метода Minecraft на всех версиях с 1.21 по 1.21.11.

### Установка

**Клиент**
1. Установите [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) и [Fabric API](https://modrinth.com/mod/fabric-api).
2. Положите подходящий `.jar` в `.minecraft/mods/`.
3. В игре откройте настройки голосового чата (`V`) → **«Дальность голоса и стены…»**. Экран также открывается через Mod Menu или своей клавишей (*Настройки → Управление*, по умолчанию не назначена).

Изменения слышны сразу. «Готово» или `Esc` сохраняют, «Отмена» возвращает всё как было.

**Сервер (Fabric)**
1. Положите тот же `.jar` в папку `mods/` сервера, рядом с Simple Voice Chat и Fabric API.
2. Запустите сервер один раз — он создаст `config/vc-audio-distance-server.properties`.
3. Стены для игроков без аддона включены по умолчанию. Чтобы передавать профиль, поставьте `profile_mode` в `suggest` или `enforce` и настройте ключи `profile.*`. Файл перечитывается автоматически.

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

### Настройки сервера — `config/vc-audio-distance-server.properties`

| Ключ | Значения | По умолчанию | Что делает |
|---|---|---|---|
| `profile_mode` | `off` / `suggest` / `enforce` | `off` | Как игроки с аддоном получают профиль |
| `server_walls` | true / false | true | Приглушение стенами для игроков без аддона |
| `server_walls_max_streams` | 0 – 512 | 24 | Сколько голосов перекодируется одновременно (ограничение нагрузки) |
| `profile.*` | те же ключи, что в файле клиента | как у клиента | Профиль звука сервера; его `occlusion_*` и `material.*` также управляют стенами на сервере |

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

[MIT](LICENSE). Автор: **Kasper / Shamanalle**.
