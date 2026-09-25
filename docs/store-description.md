# Store pages / Страницы на площадках

Text for the Modrinth and CurseForge project pages. Copy the English part, then the Russian part, into the description field; the summary goes into the short summary field.

Текст для страниц проекта на Modrinth и CurseForge. В поле описания скопируйте английскую часть, затем русскую; краткое описание — в поле summary.

---

## English

**Name:** Simple Voice Chat: Voice Physics

**Summary:** Make voices feel real: they fade naturally with distance and sound muffled behind walls, doors and glass. Live distance graph, presets, and an optional server side for Fabric, Paper and Purpur that brings walls even to players without the addon.

**Categories:** Utility, Social, Game Mechanics

**Icon:** `icon.png` in the repository root (512×512).

**Description:**

An addon for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) that shapes how voices fade with distance and muffles them through walls.

It works on either side, and each side is useful alone:
- **Client only**: you choose how *you* hear voices; nothing is needed on the server.
- **Server only**: players with plain Simple Voice Chat hear voices muffled through walls.
- **Both**: the server can share its sound profile, and the client gets exact whisper ranges.

### Features
- **Distance curves:** linear (Simple Voice Chat's own), realistic 1/r and exponential, with a live graph of the loudness at every distance.
- **Walls:** voices behind walls become quieter and duller. Real block shapes (slabs, open doors, fences) and materials (wool muffles more, glass and leaves less) are taken into account. Five rays per voice give soft edges around corners and doorways.
- **Monitor:** who is talking, how far away, and how much the walls take off.
- **Presets:** Vanilla, Realistic, Clear, Stealth.
- **Server side (optional):** walls for players without the addon, and a server sound profile that can be suggested or enforced. Available in the Fabric mod and as a plugin for Paper, Purpur, Spigot and Bukkit.
- Works together with Sound Physics Remastered: our wall muffling turns itself off so voices are not muffled twice.

### Which file
- **Fabric / Quilt:** the full version, client and server. Needs Fabric API; Mod Menu is optional.
- **Forge / NeoForge:** a lite version with distance curves only, set in `config/vc-audio-distance.properties`.
- **Paper / Purpur / Spigot / Bukkit:** the server side as a plugin, Minecraft 1.20.1 and newer.

Source code, full documentation and the changelog: https://github.com/Shamanalle/voicechat-audio-distance

---

## Русский

**Название:** Simple Voice Chat: Voice Physics

**Краткое описание** (на площадках только на английском, как выше). Перевод: Голоса как в жизни: плавно затихают с расстоянием и звучат глухо за стенами, дверями и стеклом. Живой график дистанции, пресеты и серверная часть по желанию для Fabric, Paper и Purpur, которая приносит стены даже игрокам без аддона.

**Категории:** Utility, Social, Game Mechanics

**Иконка:** `icon.png` в корне репозитория (512×512).

**Описание:**

Аддон для [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat): настраивает, как голоса затихают с расстоянием, и глушит их за стенами.

Работает на любой стороне, и каждая полезна сама по себе:
- **Только клиент:** вы выбираете, как голоса слышите *вы*; на сервере ничего не нужно.
- **Только сервер:** игроки с обычным Simple Voice Chat слышат голоса за стенами приглушёнными.
- **Оба:** сервер может передать свой звуковой профиль, а клиент получает точную дальность шёпота.

### Возможности
- **Кривые громкости:** линейная (как в Simple Voice Chat), реалистичная 1/r и экспоненциальная, с живым графиком громкости на любом расстоянии.
- **Стены:** голоса за стенами становятся тише и глуше. Учитываются настоящие формы блоков (плиты, открытые двери, заборы) и материалы (шерсть глушит сильнее, стекло и листва слабее). Пять лучей на каждый голос дают плавный переход за углами и в дверных проёмах.
- **Монитор:** кто говорит, на каком расстоянии и сколько забирают стены.
- **Пресеты:** Vanilla, Realistic, Clear, Stealth.
- **Серверная часть (по желанию):** стены для игроков без аддона и звуковой профиль сервера, который можно предложить или сделать обязательным. Есть в моде для Fabric и как плагин для Paper, Purpur, Spigot и Bukkit.
- Совместим с Sound Physics Remastered: наше приглушение стенами само выключается, чтобы голоса не глушились дважды.

### Какой файл
- **Fabric / Quilt:** полная версия, клиент и сервер. Нужен Fabric API; Mod Menu — по желанию.
- **Forge / NeoForge:** облегчённая версия, только кривые громкости, настройка в `config/vc-audio-distance.properties`.
- **Paper / Purpur / Spigot / Bukkit:** серверная часть в виде плагина, Minecraft 1.20.1 и новее.

Исходный код, полная документация и список изменений: https://github.com/Shamanalle/voicechat-audio-distance
