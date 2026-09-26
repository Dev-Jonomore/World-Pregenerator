plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "mc.jonomore"
version = "3.0"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "chunky-repo"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3-pre-2.build.0-alpha")
    compileOnly("org.popcraft:chunky-common:1.5.3")
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    testImplementation("io.papermc.paper:paper-api:26.3-pre-2.build.0-alpha")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.3")
        downloadPlugins {
            // Modrinth version ID of Chunky-Bukkit 1.5.3 (the version number is shared across loaders)
            modrinth("chunky", "MdY6JATr")
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        filteringCharset = "UTF-8"
        // Read at configuration time: filesMatching runs during execution, where touching
        // `project` is Task.project -- unsupported with the configuration cache.
        val pluginVersion = project.version
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    shadowJar {
        archiveClassifier = ""
        // Paper bundles its own Configurate; relocate ours to avoid clashes
        mergeServiceFiles()
        val libs = "mc.jonomore.worldPregenerator.libs"
        relocate("org.spongepowered.configurate", "$libs.configurate")
        relocate("io.leangen.geantyref", "$libs.geantyref")
        relocate("net.kyori.option", "$libs.option")
    }

    build {
        dependsOn(shadowJar)
    }
}
