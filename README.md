# Elder Bosses

Stonecutter project targeting:

- Minecraft 1.20.1 with Forge 47.4.10 and Java 17
- Minecraft 1.21.1 with NeoForge 21.1.233 and Java 21

Gradle itself runs on Java 25 because ModStitch 0.8.5 requires it. Node compilation still
targets the Java version listed above.

## Build

```powershell
.\gradlew.bat build
```

Build one target:

```powershell
.\gradlew.bat :1.20.1-forge:build
.\gradlew.bat :1.21.1-neoforge:build
```

The active source view is selected in `stonecutter.gradle.kts`. Cross-version adapters and
all Stonecutter-conditioned Java code belong under
`com.tonywww.elder_bosses.platforms`; shared gameplay code belongs elsewhere under
`com.tonywww.elder_bosses`.

## Documentation

- [Design documentation](docs/README.md)
- [Promised Consort implementation plan](docs/implementation/promised-consort-plan.md)