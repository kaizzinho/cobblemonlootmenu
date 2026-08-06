import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    kotlin("jvm") version "2.2.20"
}

group = "dev.cobblemonlootmenu"
version = "0.1.0"

base {
    archivesName.set("cobblemon-loot-menu")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
}

repositories {
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases/")
}

dependencies {
    minecraft("net.minecraft:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:0.17.2")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.6+kotlin.2.2.20")

    // matches the cobblemon fabric-kotlin mdk
    modCompileOnly("com.cobblemon:mod:1.7.3+1.21.1") {
        isTransitive = false
    }
    modImplementation("com.cobblemon:fabric:1.7.3+1.21.1")

    modCompileOnly(files("libs/wildbosses-1.0.0.jar"))
    modCompileOnly(files("libs/rctapi-fabric-1.21.1-0.15.2-beta.jar"))
    modCompileOnly(files("libs/rctmod-fabric-1.21.1-0.18.1-beta.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
