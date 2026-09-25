# Contributing to VoiceChat Audio Distance Addon

Thank you for your interest in contributing to **VoiceChat Audio Distance Addon**!

## Development Setup

1. **Prerequisites**:
   - **JDK 21** or higher.
   - Git.

2. **Clone the repository**:
   ```bash
   git clone https://github.com/Shamanalle/voicechat-audio-distance.git
   cd voicechat-audio-distance
   ```

3. **Build and Test**:
   ```bash
   # Run automated test suite
   ./gradlew test

   # Build mod JAR
   ./gradlew build
   ```

4. **Launch Minecraft in Development Environment**:
   ```bash
   ./gradlew runClient
   ```

## Pull Request Guidelines

1. Create a descriptive feature branch:
   ```bash
   git checkout -b feature/my-cool-improvement
   ```
2. Write clean, readable Java code following Mojang/Fabric conventions.
3. Ensure all tests pass (`./gradlew test`) before submitting.
4. If you modify UI texts or add configuration options, update both `en_us.json` and `ru_ru.json`.
5. Submit a pull request describing the changes and problem solved.
