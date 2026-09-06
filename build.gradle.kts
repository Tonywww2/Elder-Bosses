plugins {
    id("dev.architectury.loom") version "1.11.458"
}

val modId = property("mod.id").toString()
val mcVersion = property("vers.mcVersion").toString()
val forgeVersion = property("vers.deps.fml").toString()
val artifactVersion = "${property("mod.version")}+$mcVersion"
val javaVersion = 17

group = property("mod.group").toString()
version = artifactVersion
base.archivesName = "$modId-forge"

loom {
    silentMojangMappingsLicense()
    mixin {
        defaultRefmapName = "$modId.refmap.json"
    }
    forge {
        mixinConfig("$modId.mixins.json")
    }
    if (stonecutter.current.isActive) {
        runConfigs.all {
            ideConfigGenerated(true)
            runDir("../../run")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:$mcVersion-$forgeVersion")
}

tasks {
    configureEach {
        if (name == "createMinecraftArtifacts") {
            dependsOn("stonecutterGenerate")
        }
    }

    processResources {
        dependsOn("stonecutterGenerate")
        val props = mapOf(
            "id" to project.property("mod.id"),
            "name" to project.property("mod.name"),
            "version" to project.property("mod.version"),
            "authors" to project.property("mod.authors"),
            "description" to project.property("mod.description"),
            "license" to project.property("mod.license"),
            "packFormat" to project.property("vers.packFormat"),
            "loaderVersion" to project.property("vers.loaderRange"),
            "minecraftVersionRange" to project.property("vers.minecraftRange"),
            "forgeVersionRange" to project.property("vers.forgeRange"),
        )
        inputs.properties(props)
        filesMatching("META-INF/mods.toml") {
            expand(props)
        }
        filesMatching("pack.mcmeta") {
            expand(props)
        }
        exclude("META-INF/neoforge.mods.toml")
    }

    withType<JavaCompile>().configureEach {
        dependsOn("stonecutterGenerate")
        options.encoding = "UTF-8"
        options.release.set(javaVersion)
    }

    withType<Jar>().configureEach {
        archiveVersion.set(artifactVersion)
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}