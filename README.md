# 🎙️ VoiceChat Audio Distance Addon

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-blue.svg?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Simple Voice Chat](https://img.shields.io/badge/Simple%20Voice%20Chat-2.4.0%2B-orange.svg)](https://modrinth.com/plugin/simple-voice-chat)
[![Build Status](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml/badge.svg)](https://github.com/Shamanalle/voicechat-audio-distance/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Shamanalle/voicechat-audio-distance?logo=github&color=brightgreen)](https://github.com/Shamanalle/voicechat-audio-distance/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Продвинутый клиентский аддон для **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**, предоставляющий полный контроль над физикой затухания 3D-звука в пространстве, аппаратным порогом громкости через OpenAL, настраиваемым спадом шёпота и наглядным интерактивным графиком слышимости в реальном времени.

---

## 🇷🇺 Описание возможностей

### 1. Физические модели распространения звука (OpenAL Distance Models)
* **Линейная (Linear / Vanilla SVC)**: Стандартная модель Simple Voice Chat. Громкость держится на 100% до заданной дистанции, затем линейно спадает.
* **Реалистичная акустическая (Realistic Inverse 1/r)**: Реальное физическое затухание звуковых волн в воздухе по закону обратных квадратов. Голос вблизи звучит естественно и объемно, плавно растворяясь на расстоянии.
* **Экспоненциальная (Exponential)**: Быстрый спад звука, создающий напряженную атмосферу. Идеально для хоррор-карт, стелс-миссий и приключений.
* **Безопасность слуха (Clamped Bounds)**: Использование нативных Clamped-моделей OpenAL 1.1 предотвращает акустические удары в упор.

### 2. Физическое приглушение через стены (Sound Occlusion & Muffling)
* **Акустическое поглощение препятствиями**: Когда между говорящим и слушателем находятся твердые блоки (стены домов, двери, полы, своды пещер), звук динамически фильтруется низкочастотным фильтром (Low-Pass Filter) прямо на уровне PCM-фреймов.
* **DSP 1-pole IIR Фильтр**: Срезает звонкие высокочастотные согласные по формуле `y[n] = y[n-1] + α · (x[n] - y[n-1])`, оставляя мягкие басовые гармоники (эффект «голоса из соседней комнаты»). Сохраняет непрерывность фазы между фреймами (без щелчков и артефактов).
* **3D Воксельный Raycast (`BlockGetter.traverseBlocks`)**: Мгновенно трассирует луч через блоки с учетом физики материалов:
  - Шерсть и ковры — сильная звукоизоляция (0.45).
  - Сплошной камень, кирпич, обсидиан — полное перекрытие (0.35).
  - Двери и люки — умеренное глушение (0.25).
  - Стекло, решетки, заборы — частичное пропускание звука (0.15 - 0.18).
  - Листва и вода — естественное рассеивание звука.
* **Настройки в меню**: Переключатель «Стены: ВКЛ / ВЫКЛ» и слайдер глубины приглушения («Глубина: 0% – 100%»).

### 3. Аппаратный порог слышимости (`AL_MIN_GAIN`)
* Никаких цифровых искажений и перегрузок PCM (`Math.tanh`).
* Порог громкости задается напрямую в аудиочип через нативный параметр OpenAL `AL_MIN_GAIN`.
* Автоматически учитывает индивидуальный мут и громкость игроков (замьюченный игрок остаётся неслышимым).
* На предельной дистанции голос собеседника не затихает в абсолютный ноль, если вам нужно слышать радиопереговоры на краю зоны.

### 4. Интерактивный предпросмотр затухания (Live Audio Curve)
* В меню настроек отображается динамическая шкала (от 0 блоков до максимального радиуса), которая в реальном времени отрисовывает точную математическую кривую громкости звука при смене моделей или перемещении ползунков.
* Встроенный инспектор курсора показывает точную дистанцию в блоках и итоговый процент громкости в любой точке кривой.

### 5. Множитель спада шёпота (Whisper Falloff)
* Отдельный ползунок в интерфейсе (`0.50x – 2.00x`) для регулировки разборчивости и затухания шёпота на дистанции.

### 6. Быстрые пресеты в 1 клик
* **Ваниль (Vanilla)**: Сброс к поведению чистого Simple Voice Chat (100% спад, 0% мин. громкость, 50% старт, приглушение стен выкл).
* **Мягкий (Realistic)**: Акустическая модель 1/r, комфортный естественный баланс для выживания, мягкое приглушение за стенами (65%).
* **Чёткий (Audible)**: Повышенная слышимость на дальних расстояниях для серверов, стримов и мини-игр.
* **Стелс (Stealth)**: Резкое затухание и глубокое глушение за препятствиями (85%) для игр в прятки и хорроров.

### 7. Удобство и интеграция
* **Горячая клавиша**: Назначается в стандартном меню Minecraft «Управление» -> «Назначение клавиш».
* **Интеграция с Mod Menu**: Настройки открываются прямо из списка модов Fabric.
* **Кнопка в меню голосового чата**: Добавляется в стандартное окно настроек Simple Voice Chat с умным позиционированием.
* **Полная локализация**: Поддержка русского (`ru_ru`) и английского (`en_us`) языков.

---

## 🇬🇧 Features Overview

* **Physical Sound Occlusion & Muffling**: Real-time DSP low-pass filter (IIR 1-pole `y[n] = y[n-1] + α · (x[n] - y[n-1])`) dynamically muffles voice through walls, doors, and caves.
* **3D Voxel Raycasting**: Fast traversal via Minecraft's internal DDA engine with material-based absorption (wool = soundproofing, stone, wood, glass, water).
* **Physical Acoustic Attenuation**: Switch between *Linear (Vanilla)*, *Realistic Inverse (1/r)*, and *Exponential* falloff curves with OpenAL 1.1 clamped bounds.
* **Native OpenAL Hardware Floor**: Uses `AL_MIN_GAIN` hardware clamping — zero clipping, zero latency, pure audio quality, fully respecting mute & volume levels.
* **Whisper Falloff Multiplier**: Dedicated in-GUI slider (`0.50x – 2.00x`) to control whisper decay distance.
* **Live Curve Visualizer**: Real-time acoustic audibility graph with built-in block & volume inspector.
* **Instant Presets**: 1-click presets for Vanilla, Realistic, High Audibility, and Stealth modes.
* **Keybinding & Mod Menu**: Fully configurable hotkey, Mod Menu integration, and SVC screen hook with responsive layout.
* **Bilingual Localization**: English (`en_us`) and Russian (`ru_ru`).
* **Automated Unit Tests**: Suite of 28 JUnit 5 tests verifying audio physics, DSP frequency response, continuity, and preset math.

---

## ⚙️ Сравнение / Comparison

| Возможность | Обычный Simple Voice Chat | VoiceChat Audio Distance Addon |
|---|---|---|
| **Модель затухания** | Только линейная (Linear) | **Linear, Realistic Inverse (1/r), Exponential** |
| **Кривая слышимости** | Нельзя изменить | **Настраиваемый спад и старт затухания (10% - 100%)** |
| **Минимальная громкость на максимуме** | Всегда 0% (полная тишина) | **Настраиваемый аппаратный порог (0% - 100%)** |
| **Визуализация кривой** | Отсутствует | **Интерактивный рендерер кривой громкости в GUI** |
| **Быстрые профили** | Нет | **4 готовых пресета в 1 клик** |
| **Множитель затухания шёпота** | Фиксированный (50%) | **Настраиваемый коэффициент** |

---

## 📦 Совместимость и установка / Versions & Installation

Аддон выпускается в виде отдельных оптимизированных сборок под каждое поколение Minecraft:

| Версия Minecraft | Релизный файл аддона | Требуемая Java | Версия Simple Voice Chat |
|---|---|---|---|
| **Minecraft 1.21.x** (`1.21`, `1.21.1` ... `1.21.11`) | `voicechat-audio-distance-addon-1.1.0+mc1.21.x.jar` | Java 21+ | `>=2.4.0` |
| **Minecraft 26.x** (`26.1`, `26.2`, `26.3`) | `voicechat-audio-distance-addon-1.1.0+mc26.3.jar` | Java 25+ | `>=2.6.0` (например, `2.6.24+26.3`) |

### Инструкция по установке:
1. Выберите подходящий файл аддона из таблицы выше под вашу версию Minecraft.
2. Убедитесь, что у вас установлены **Fabric Loader**, **Fabric API** и мод **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**.
3. Поместите скачанный `.jar` файл в папку `.minecraft/mods/`.
4. *(Опционально)* Установите **Mod Menu** для быстрого доступа к интерфейсу настроек.

---

## 🛠️ Сборка из исходников / Building from Source

Требуется **JDK 25** (поддерживает сборку обоих модулей):

```bash
# Клонируйте репозиторий
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance

# Полная сборка всех поддерживаемых версий (1.21.x и 26.x)
./gradlew build
```

Собранные JAR-архивы для всех версий появятся в `build/libs/`:
- `voicechat-audio-distance-addon-1.1.0+mc1.21.x.jar`
- `voicechat-audio-distance-addon-1.1.0+mc26.3.jar`

---

## 📄 Лицензия / License

Проект распространяется под свободной лицензией [MIT](LICENSE).
Автор: **Kasper / Shamanalle**.
