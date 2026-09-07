plugins {
    id("dev.isxander.modstitch.base") version "0.8.5"
}

val modId = property("mod.id").toString()
val mcVersion = property("deps.minecraft").toString()
val targetNeoForgeVersion = property("deps.neoforge").toString()
val artifactVersion = "${property("mod.version")}+$mcVersion"
val javaVersion = 21

group = property("mod.group").toString()
version = artifactVersion
base.archivesName = "$modId-neoforge"

modstitch {
    minecraftVersion = mcVersion
    moddevgradle {
        neoForgeVersion = targetNeoForgeVersion
        defaultRuns()
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
}

tasks {
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
            "neoForgeVersionRange" to project.property("vers.neoForgeRange"),
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(props)
        }
        filesMatching("pack.mcmeta") {
            expand(props)
        }
        exclude("META-INF/mods.toml")
        exclude("data/**/loot_tables/**")
        exclude("data/**/tags/items/**")
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