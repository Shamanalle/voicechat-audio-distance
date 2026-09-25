# 🎙️ VoiceChat Audio Distance Addon

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%20--%201.21.8%2B-blue.svg?logo=minecraft&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-lightgrey.svg?logo=fabric&logoColor=white)](https://fabricmc.net/)
[![Simple Voice Chat](https://img.shields.io/badge/Simple%20Voice%20Chat-2.4.0%2B-orange.svg)](https://modrinth.com/plugin/simple-voice-chat)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Продвинутый клиентский аддон для **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)**, предоставляющий полный контроль над физикой затухания 3D-звука в пространстве, аппаратным порогом громкости через OpenAL и наглядным интерактивным графиком слышимости в реальном времени.

---

## 🇷🇺 Описание возможностей

### 1. Физические модели распространения звука (OpenAL Distance Models)
* **Линейная (Linear / Vanilla SVC)**: Стандартная модель Simple Voice Chat. Громкость держится на 100% до заданной дистанции, затем линейно спадает.
* **Реалистичная акустическая (Realistic Inverse $1/r$)**: Реальное физическое затухание звуковых волн в воздухе по закону обратных квадратов. Голос вблизи звучит естественно и объемно, плавно растворяясь на расстоянии.
* **Экспоненциальная (Exponential)**: Быстрый спад звука, создающий напряженную атмосферу. Идеально для хоррор-карт, стелс-миссий и приключений.

### 2. Аппаратный порог слышимости (`AL_MIN_GAIN`)
* Никаких цифровых искажений и перегрузок PCM (`Math.tanh`).
* Порог громкости задается напрямую в аудиочип через нативный параметр OpenAL `AL_MIN_GAIN`.
* На предельной дистанции голос собеседника не затихает в абсолютный ноль, если вам нужно слышать предупреждения или радиопереговоры на краю зоны.

### 3. Интерактивный предпросмотр затухания (Live Audio Curve)
* В меню настроек отображается динамическая шкала (от 0 блоков до максимального радиуса), которая в реальном времени отрисовывает точную математическую кривую громкости звука при смене моделей или перемещении ползунков.

### 4. Быстрые пресеты в 1 клик
* **Ваниль (Vanilla)**: Сброс к поведению чистого Simple Voice Chat (100% спад, 0% мин. громкость, 50% старт).
* **Мягкий (Realistic)**: Акустическая модель $1/r$, комфортный естественный баланс для выживания.
* **Чёткий (Audible)**: Повышенная слышимость на дальних расстояниях для серверов, стримов и мини-игр.
* **Стелс (Stealth)**: Резкое затухание для игр в прятки и хорроров.

### 5. Удобство и интеграция
* **Горячая клавиша**: Назначается в стандартном меню Minecraft «Управление» -> «Назначение клавиш».
* **Интеграция с Mod Menu**: Настройки открываются прямо из списка модов Fabric.
* **Кнопка в меню голосового чата**: Добавляется в стандартное окно настроек Simple Voice Chat.
* **Полная локализация**: Поддержка русского (`ru_ru`) и английского (`en_us`) языков.

---

## 🇬🇧 Features Overview

* **Physical Acoustic Attenuation**: Switch between *Linear (Vanilla)*, *Realistic Inverse ($1/r$)*, and *Exponential* falloff curves.
* **Native OpenAL Hardware Floor**: Uses `AL_MIN_GAIN` hardware clamping — zero clipping, zero latency, pure audio quality.
* **Live Curve Visualizer**: Real-time acoustic audibility graph rendered directly inside the configuration GUI.
* **Instant Presets**: 1-click presets for Vanilla, Realistic, High Audibility, and Stealth modes.
* **Keybinding & Mod Menu**: Fully configurable hotkey, Mod Menu integration, and SVC screen hook.
* **Bilingual Localization**: English (`en_us`) and Russian (`ru_ru`).

---

## ⚙️ Сравнение / Comparison

| Возможность | Обычный Simple Voice Chat | VoiceChat Audio Distance Addon |
|---|---|---|
| **Модель затухания** | Только линейная (Linear) | **Linear, Realistic Inverse ($1/r$), Exponential** |
| **Кривая слышимости** | Нельзя изменить | **Настраиваемый спад и старт затухания (10% - 100%)** |
| **Минимальная громкость на максимуме** | Всегда 0% (полная тишина) | **Настраиваемый аппаратный порог (0% - 100%)** |
| **Визуализация кривой** | Отсутствует | **Интерактивный рендерер кривой громкости в GUI** |
| **Быстрые профили** | Нет | **4 готовых пресета в 1 клик** |
| **Множитель затухания шёпота** | Фиксированный (50%) | **Настраиваемый коэффициент** |

---

## 📦 Установка / Installation

1. Установите **Minecraft** (версии `1.21` – `1.21.8+`).
2. Установите **Fabric Loader** и **Fabric API**.
3. Установите мод **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** (версии `2.4.0` или новее).
4. Поместите файл `voicechat-audio-distance-addon-1.0.0.jar` в папку `.minecraft/mods/`.
5. *(Опционально)* Установите **Mod Menu** для быстрого доступа к настройкам из списка модов.

---

## 🛠️ Сборка из исходников / Building from Source

Требуется **JDK 21** или новее:

```bash
# Клонируйте репозиторий
git clone https://github.com/Shamanalle/voicechat-audio-distance.git
cd voicechat-audio-distance

# Сборка проекта через Gradle
./gradlew build
```

Собранный JAR-архив появится в папке `build/libs/`.

---

## 📄 Лицензия / License

Проект распространяется под свободной лицензией [MIT](LICENSE).
Автор: **Kasper / Shamanalle**.
