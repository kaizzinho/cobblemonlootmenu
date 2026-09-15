import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("dev.architectury.loom") version "1.13-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    kotlin("jvm") version "2.2.20"
}

group = "dev.cobblemonlootmenu"
version = "1.0"

base {
    archivesName.set("cobblemon-loot-menu")
}

architectury {
    fabric()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://artefacts.cobblemon.com/releases/")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:0.17.2")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.6+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.13.6+kotlin.2.2.20")

    modCompileOnly("com.cobblemon:mod:1.8.1+1.21.1") {
        isTransitive = false
    }
    modImplementation("com.cobblemon:fabric:1.8.1+1.21.1")

    modCompileOnly(files("libs/wildbosses-1.0.0.jar"))
    modCompileOnly(files("libs/rctapi-fabric-1.21.1-0.15.2-beta.jar"))
    modCompileOnly(files("libs/rctmod-fabric-1.21.1-0.18.1-beta.jar"))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
