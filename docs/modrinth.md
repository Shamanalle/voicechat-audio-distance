An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that makes voices behave like sound: they fade with distance, get muffled behind walls, come round corners through doorways and echo in caves.

Install it on your client, on the server, or on both. Each works on its own.

![The Distance tab: the fade curve and presets](https://raw.githubusercontent.com/Shamanalle/voice-physics/main/docs/images/ui-distance.png)

## For players

**🗣️ Distance**
- Choose how voices fade: like Simple Voice Chat, realistic, or steep. No sudden cut-off at the edge.
- A live graph with the players you hear right now, and a *Listen* button to try the fade.
- Presets: **Vanilla**, **Realistic**, **Clear** (events), **Stealth** (hide-and-seek, horror). They adapt to the server's voice range.

**🧱 Walls and corners**
- Voices behind walls are quieter and duller. Wool and metal block more, glass and leaves less, open doors let sound through.
- A voice from the next room comes through the doorway, and it gets duller the sharper the turn.
- Players side by side in a narrow tunnel hear each other clearly.

**🌊 Echo, water, rain**
- The echo fits the place: short in a stone room, long in a cave, soft in a wooden house, none in a forest or field.
- Near cliffs the voice comes back a moment later.
- Dull voices under water; rain and thunder cover far voices.

**👀 HUD and monitor**
- See who is talking, how far, from where, and whether they are behind a wall.
- While you talk, see how many players hear you.
- A monitor and radar with everyone in voice range. Colors for color blindness.

**🔗 Also:** share your settings with a friend as one code. Seven languages.

## For servers

Players **without the addon** also hear voices muffled through walls.

- **Sound zones:** a stage heard twice as far, a quiet library, a soundproof room, a cathedral with echo, a message on entering. WorldGuard regions work too.
- **Game rules:** sneaking is quieter, the dead are silent, spectators talk only to each other, a goat horn works as a megaphone.
- **One sound for everyone:** offer or enforce the server's settings for fair PvP and events.
- **No seeing through walls:** turn off the monitor and radar.
- **Require the addon:** send a download link, remind on every join, or kick.
- **In-game Server tab** for admins, and `/vcd` commands.

## Which file do I need?

| You play on | File |
|---|---|
| Fabric, Quilt | full, needs [Fabric API](https://modrinth.com/mod/fabric-api) |
| NeoForge 26.x | full |
| Paper, Purpur, Spigot | plugin (server only) |
| Forge, NeoForge before 26.x | lite: distance only |

Minecraft 1.20 – 1.20.6, 1.21 – 1.21.11 and 26.1 – 26.3.

**[All settings, commands and what works where →](https://github.com/Shamanalle/voice-physics#readme)** · [Changelog](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Report a bug](https://github.com/Shamanalle/voice-physics/issues)

---

<details>
<summary><b>🇷🇺 Русский</b></summary>

Аддон для [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), с которым голоса ведут себя как звук: затихают с расстоянием, глохнут за стенами, доносятся из-за угла через проёмы и отдаются эхом в пещерах.

Ставится на клиент, на сервер или туда и туда. Каждый вариант работает сам по себе.

## Для игроков

**🗣️ Дистанция**
- Выберите, как тихнут голоса: как в Simple Voice Chat, реалистично или круче. Без резкого обрыва на краю.
- Живой график с теми, кого вы слышите сейчас, и кнопка «Прослушать», чтобы услышать спад.
- Пресеты: **Ваниль**, **Реализм**, **Чётко** (ивенты), **Стелс** (прятки, хоррор). Сами подстраиваются под дальность голоса на сервере.

**🧱 Стены и углы**
- Голоса за стенами тише и глуше. Шерсть и металл глушат сильнее, стекло и листва слабее, открытые двери пропускают звук.
- Голос из соседней комнаты идёт через дверной проём и тем глуше, чем круче поворот.
- Двое рядом в узком тоннеле слышат друг друга чисто.

**🌊 Эхо, вода, дождь**
- Эхо по месту: короткое в каменной комнате, долгое в пещере, мягкое в деревянном доме, никакого в лесу и в поле.
- У скал голос возвращается через мгновение.
- Глухие голоса под водой; дождь и гроза заглушают дальние голоса.

**👀 HUD и монитор**
- Видно, кто говорит, как далеко, откуда и за стеной ли.
- Пока говорите вы — сколько игроков вас слышат.
- Монитор и радар со всеми в радиусе голоса. Цвета для дальтоников.

**🔗 Ещё:** поделитесь настройками с другом одним кодом. Семь языков.

## Для серверов

Игроки **без аддона** тоже слышат голоса за стенами приглушёнными.

- **Звуковые зоны:** сцена, которую слышно вдвое дальше, тихая библиотека, звукоизолированная комната, собор с эхом, сообщение при входе. Регионы WorldGuard тоже подходят.
- **Правила игры:** на корточках тише, мёртвые молчат, наблюдатели говорят только между собой, козий рог работает как мегафон.
- **Один звук для всех:** предложите или закрепите настройки сервера для честного PvP и ивентов.
- **Без взгляда сквозь стены:** выключите монитор и радар.
- **Обязательный аддон:** ссылка на скачивание, напоминание при каждом входе или кик.
- **Вкладка «Сервер» в игре** для админов и команды `/vcd`.

## Какой файл нужен?

| Вы играете на | Файл |
|---|---|
| Fabric, Quilt | полный, нужен [Fabric API](https://modrinth.com/mod/fabric-api) |
| NeoForge 26.x | полный |
| Paper, Purpur, Spigot | плагин (только сервер) |
| Forge, NeoForge до 26.x | облегчённый: только дистанция |

Minecraft 1.20 – 1.20.6, 1.21 – 1.21.11 и 26.1 – 26.3.

**[Все настройки, команды и что где работает →](https://github.com/Shamanalle/voice-physics/blob/main/README.ru.md)** · [Список изменений](https://github.com/Shamanalle/voice-physics/blob/main/CHANGELOG.md) · [Сообщить об ошибке](https://github.com/Shamanalle/voice-physics/issues)

</details>
