plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
}

base {
    archivesName = project.property("archives_base_name") as String
    version = project.property("mod_version") as String
    group = project.property("maven_group") as String
}

repositories {
    mavenCentral()
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    minecraft(libs.minecraft)
    // No explicit mappings(...) call - matches the verified LiquidBounce 26.2 project, which
    // relies on Loom's default mapping resolution for this Minecraft version rather than
    // declaring Yarn/Mojmap explicitly.

    api(libs.fabric.loader)
    api(libs.fabric.api)
    api(libs.fabric.kotlin)
}

java {
    withSourcesJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

kotlin {
    compilerOptions {
        jvmToolchain(libs.versions.jdk.get().toInt())
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.jar {
    val archivesBaseName = providers.gradleProperty("archives_base_name")
    val modVersion = providers.gradleProperty("mod_version")

    manifest {
        attributes["Implementation-Title"] = archivesBaseName.get()
        attributes["Implementation-Version"] = modVersion.get()
    }
}
