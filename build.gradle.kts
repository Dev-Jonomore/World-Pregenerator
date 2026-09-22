plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "mc.jonomore"
version = "2.1"

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
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly(group = "org.popcraft", name = "chunky-common", version = "1.3.38")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1")
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
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
