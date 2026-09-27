plugins {
    id("dev.architectury.loom") version "1.11.458"
}

val modId = property("mod.id").toString()
val mcVersion = property("vers.mcVersion").toString()
val forgeVersion = property("vers.deps.fml").toString()
val jeiVersion = property("vers.deps.jei").toString()
val curiosVersion = property("vers.deps.curios").toString()
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
    exclusiveContent {
        forRepository { maven("https://maven.theillusivec4.top/") }
        filter { includeGroup("top.theillusivec4.curios") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com/") }
        filter {
            includeGroup("mezz.jei")
            includeGroup("net.mezzdev.config")
        }
    }
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:$mcVersion-$forgeVersion")
    modImplementation("software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4")
    modImplementation("maven.modrinth:attributefix:21.0.5")
    modCompileOnly("top.theillusivec4.curios:curios-forge:$curiosVersion:api")
    modRuntimeOnly("top.theillusivec4.curios:curios-forge:$curiosVersion")
    modCompileOnly("mezz.jei:jei-$mcVersion-common-api:$jeiVersion")
    modCompileOnly("mezz.jei:jei-$mcVersion-forge-api:$jeiVersion")
    // The full mod already contains JEI's internal modules and API classes.
    modRuntimeOnly("mezz.jei:jei-$mcVersion-forge:$jeiVersion") { isTransitive = false }
    // Loom strips jar-in-jar metadata while remapping; expose JEI's required config mod explicitly.
    modRuntimeOnly("net.mezzdev.config:mezz_config-$mcVersion-forge:0.6.3") { isTransitive = false }
    forgeRuntimeLibrary("com.eliotlash.mclib:mclib:20")
    // Projection poses sample GeckoLib's baked keyframes; IValue is part of their public signature.
    compileOnly("com.eliotlash.mclib:mclib:20")
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
        exclude("data/*/structure/**")
        exclude("data/*/recipe/**")
        exclude("data/**/loot_table/**")
        exclude("data/**/tags/block/**")
        exclude("data/**/tags/entity_type/**")
        exclude("data/**/tags/item/**")
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
