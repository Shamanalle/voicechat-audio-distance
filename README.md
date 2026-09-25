# 🎙️ VoiceChat Audio Distance

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.x%20%7C%2026.3-blue.svg?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Simple Voice Chat](https://img.shields.io/badge/Simple%20Voice%20Chat-2.4%2B-orange.svg)](https://modrinth.com/plugin/simple-voice-chat)
[![Build Status](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml/badge.svg)](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Shamanalle/voicechat-audio-distance?logo=github&color=brightgreen)](https://github.com/Shamanalle/voicechat-audio-distance/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Клиентский аддон для **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**: сам решаете, как голоса затихают с расстоянием, и слышите, как стены их глушат. Меняет только то, что слышите **вы** — на сервер ставить не нужно.

*[English below](#-english)*

| Дистанция | Стены | Монитор |
|---|---|---|
| ![Дистанция](docs/images/ui-distance.png) | ![Стены](docs/images/ui-walls.png) | ![Monitor](docs/images/ui-monitor.png) |

<sub>Рендеры экрана настроек вне игры (тот же код раскладки и отрисовки, что в моде).</sub>

---

## Возможности

### Кривая громкости
- **Три кривые:** линейная (как в Simple Voice Chat), реалистичная 1/r и экспоненциальная.
- **Спад, зона полной громкости, громкость на краю** — всё настраивается, а на живом графике сразу видно, сколько процентов громкости будет на каждой дистанции. Наведите курсор — покажется точное значение в блоках, процентах и децибелах.
- **Шёпот** затухает по отдельной кривой (пунктир на графике).
- **Громкость на краю** выставляется через OpenAL `AL_MIN_GAIN` и учитывает громкость каждого игрока: замьюченные остаются замьюченными.
- **Люди, которых вы слышите прямо сейчас**, отмечены точками на графике.

### Стены
- Голос за стеной становится **тише и глуше**: срез высоких частот (фильтр 24 дБ/окт) плюс общее ослабление. Одна каменная стена при силе по умолчанию — около −8 дБ и глухо выше ~2,5 кГц, три стены — около −18 дБ и ~600 Гц.
- **Считается реальная геометрия блока:** полублоки, открытые двери, заборы и ковры не глушат как целый куб.
- **Мягкие края:** 5 параллельных лучей вместо одного, поэтому голос из-за угла или дверного проёма глохнет плавно, а не щелчком.
- **Материалы:** шерсть глушит сильнее камня, стекло и листва — слабее. Вес каждого материала можно поменять на вкладке «Материалы».
- **Плавные переходы:** параметры фильтра меняются за ~90 мс, без щелчков. Когда стены нет, звук проходит без изменений, бит в бит.
- **Совместимость с Sound Physics Remastered:** если он установлен, наше приглушение выключается само, чтобы голос не глушился дважды.

### Монитор
Кто говорит рядом, на каком расстоянии, с какой громкостью до вас доходит голос и сколько отнимают стены — в реальном времени.

### Пресеты
**Ваниль** (ровно как SVC), **Реализм**, **Чётко** (для ивентов — всех хорошо слышно), **Стелс** (прятки, хорроры). Активный пресет подсвечивается.

### Где найти настройки
- Кнопка **«Дальность голоса и стены…»** в настройках Simple Voice Chat (клавиша `V`).
- Через **Mod Menu**.
- Своя клавиша: *Настройки → Управление*, не назначена по умолчанию.

Изменения слышны сразу. «Готово» или `Esc` сохраняют, «Отмена» возвращает всё как было.

---

## Версии и файлы

| Загрузчик | Minecraft | Файл | Java | Simple Voice Chat |
|---|---|---|---|---|
| **Fabric / Quilt** | 1.20 – 1.20.1 | `voicechat-audio-distance-fabric-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| **Fabric / Quilt** | 1.21 – 1.21.11 | `voicechat-audio-distance-fabric-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| **Fabric** | 26.3 | `voicechat-audio-distance-fabric-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |
| Forge | 1.20.1 | `voicechat-audio-distance-forge-1.2.0+mc1.20.1.jar` | 17+ | 1.20.1-2.4.0+ |
| NeoForge / Forge | 1.21 – 1.21.11 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc1.21.x.jar` | 21+ | 1.21-2.5.0+ |
| NeoForge / Forge | 26.3 | `voicechat-audio-distance-{neoforge,forge}-1.2.0+mc26.x.jar` | 25+ | 2.6.0+ |

**Fabric** — полная версия. Для Fabric нужен [Fabric API](https://modrinth.com/mod/fabric-api), [Mod Menu](https://modrinth.com/mod/modmenu) — по желанию.

**Forge / NeoForge — облегчённая версия:** работают кривые громкости, настройка — в файле `config/vc-audio-distance.properties`. Экрана настроек, стен и монитора там нет.

Совместимость JAR для 1.21.x проверена по сигнатурам всех используемых методов Minecraft для каждой версии с 1.21 по 1.21.11.

### Установка
1. Установите [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) (и Fabric API для Fabric).
2. Положите подходящий `.jar` в `.minecraft/mods/`.
3. Зайдите в игру и откройте настройки голосового чата (`V`) → **«Дальность голоса и стены…»**.

---

## Файл настроек

`config/vc-audio-distance.properties` — всё то же, что на экране:

| Ключ | Диапазон | По умолчанию | Что делает |
|---|---|---|---|
| `distance_model` | `linear` / `realistic_inverse` / `exponential` | `linear` | Форма кривой |
| `attenuation_factor` | 0.0 – 1.0 | 1.0 | Сила спада |
| `openal_reference_ratio` | 0.05 – 1.0 | 0.5 | Доля дистанции с полной громкостью |
| `min_volume_fraction` | 0.0 – 0.5 | 0.0 | Громкость на краю |
| `whisper_multiplier` | 0.5 – 2.0 | 1.0 | Множитель спада шёпота |
| `occlusion_enabled` | true / false | true | Приглушение стенами |
| `occlusion_strength` | 0.0 – 1.0 | 0.6 | Сила приглушения |
| `material.<id>` | 0.0 – 3.0 | см. вкладку «Материалы» | «Толщина» блока, камень = 1.0 |

---

## Сборка

Нужен **JDK 25** (модули 1.20 и 1.21 собираются с `--release 17` / `21`).

```bash
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance
./gradlew :common:test   # тесты звука, конфига и переводов
./gradlew build          # все JAR в build/libs/
```

Устройство проекта описано в [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 🇬🇧 English

A client-side addon for **Simple Voice Chat** that lets you shape how voices fade with distance and muffles them through walls. It only changes what **you** hear; nothing is needed on the server.

- **Distance curve:** linear (SVC's own), realistic 1/r or exponential. Falloff, full-volume distance, edge volume and whisper falloff are adjustable, with a live graph. Hover it to read exact blocks, % and dB. Voices you hear right now appear as dots on the graph.
- **Walls:** voices behind walls get quieter and duller (24 dB/oct low-pass plus broadband loss). One stone wall at default strength is about −8 dB and muffled above ~2.5 kHz. Rays respect real block shapes (slabs, open doors, fences). Five parallel rays soften corners and doorways. Per-material weights are adjustable, and filter changes glide smoothly without clicks. Stands down automatically when Sound Physics Remastered is installed.
- **Monitor:** who is talking, how far away, how loud they reach you, and how much the walls take off.
- **Presets:** Vanilla, Realistic, Clear, Stealth.
- **Open it** from Simple Voice Chat's settings (`V`), from Mod Menu, or with a key you bind yourself.

Fabric is the full version. The Forge / NeoForge jars are a lite build: distance curves only, configured in `config/vc-audio-distance.properties`.

Build with JDK 25: `./gradlew build`. License: [MIT](LICENSE). Author: **Kasper / Shamanalle**.
