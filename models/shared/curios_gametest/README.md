# Curios runtime checks

These opt-in Forge GameTests load the real Curios mod, mixins and data packs in a
temporary world under `build/curios-<case>-gametest-run`. No existing game saves are used.
They check slot assignment, material rejection, all four equipment bonuses,
duplicate rejection, cosmetic slots, non-scaling stack counts, equip/unequip,
inventory save/load, weapon attributes and the three remembrance recipes. Equipment
values are loaded from real TOML fixtures, including fractional weapon damage,
modified durability/enchantability and zero bonuses. The checks also cover packet
serialization, logical-side isolation and disconnect cleanup of client settings.

With Java 25 selected for Gradle:

```powershell
.\gradlew.bat -I models/shared/curios_gametest.init.gradle :1.20.1-forge:runCuriosTest
.\gradlew.bat -I models/shared/curios_gametest.init.gradle -PequipmentConfigCase=custom :1.20.1-forge:runCuriosTest
.\gradlew.bat -I models/shared/curios_gametest.init.gradle -PequipmentConfigCase=zero :1.20.1-forge:runCuriosTest
.\gradlew.bat -I models/shared/curios_gametest.init.gradle -PwithoutCurios -PequipmentConfigCase=custom :1.20.1-forge:runCuriosTest
```

The final run excludes Curios from the runtime, verifies that its API classes
are absent, and checks successful loading, weapons and crafting. Its world is
stored separately in `build/curios-absent-custom-gametest-run`. The init script
copies the selected fixture over this test world's common config before each run.

Only these test invocations include the fixture sources and empty template.
Run the normal build without the init script afterwards to remove test classes
and resources from the output. Never build a release JAR with this init script.

```powershell
.\gradlew.bat :1.20.1-forge:build :1.21.1-neoforge:build
```

NeoForge compilation and packaged resources are checked separately. These tests
do not constitute a visual review of the Curios inventory or worn models.
