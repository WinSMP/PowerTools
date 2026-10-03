import java.text.SimpleDateFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.*

val kotlinVersion = "2.3.20"

plugins {
    id("com.gradleup.shadow") version "9.3.0"
    kotlin("jvm") version "2.3.20"
}

group = "org.winlogon.powertools"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date()).toString()
}

val shortVersion: String? = if (project.hasProperty("ver")) {
    val ver = project.property("ver").toString()
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
} else {
    null
}

val version: String = when {
    shortVersion.isNullOrEmpty() -> "${getTime()}-SNAPSHOT"
    shortVersion.contains("-RC-") -> shortVersion.substringBefore("-RC-") + "-SNAPSHOT"
    else -> shortVersion
}

val pluginName = rootProject.name
val pluginVersion = version
val pluginPackage = project.group.toString()
val projectName = rootProject.name

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("net.md-5", "bungeecord-chat")
        }
    }
    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }
    maven {
        name = "winlogon"
        url = uri("https://maven.winlogon.org/releases/")
    }
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    mavenCentral()
}

val lampVersion = "4.0.0-rc.16"
val retrohueVersion = "0.2.0"
val minecraftVersion = "26.1.2"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$minecraftVersion.build.+")
    compileOnly("org.winlogon:retrohue:$retrohueVersion")
    compileOnly("org.winlogon:asynccraftr:0.2.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("io.github.revxrsal:lamp.common:$lampVersion")
    implementation("io.github.revxrsal:lamp.bukkit:$lampVersion")
    implementation("io.github.revxrsal:lamp.brigadier:$lampVersion")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // the plugin ships neither, but the code under test is compiled against them
    testRuntimeOnly("org.winlogon:retrohue:$retrohueVersion")
    testImplementation("io.papermc.paper:paper-api:$minecraftVersion.build.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    // MockBukkit pins its own paper-api build, so drop it to keep a single paper-api on the test classpath
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.115.0") {
        exclude(group = "io.papermc.paper", module = "paper-api")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesMatching("**/paper-plugin.yml") {
        expand(
            "NAME" to pluginName,
            "VERSION" to pluginVersion,
            "PACKAGE" to pluginPackage,
            "API_VERSION" to minecraftVersion
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
    dependencies {
        include(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        include(dependency("io.github.revxrsal:lamp.common:$lampVersion"))
        include(dependency("io.github.revxrsal:lamp.bukkit:$lampVersion"))
        include(dependency("io.github.revxrsal:lamp.brigadier:$lampVersion"))
    }
    relocate("io.github.revxrsal.lamp", "${project.group}.shaded.lamp")
    relocate("kotlin", "${project.group}.shaded.kotlin")
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        javaParameters = true
    }
}

tasks.register("printProjectName") {
    doLast {
        println(projectName)
    }
}

var shadowJarTask = tasks.shadowJar.get()
tasks.register("release") {
    dependsOn(tasks.build)
    doLast {
        if (!version.endsWith("-SNAPSHOT")) {
            shadowJarTask.archiveFile.get().asFile.renameTo(
                file("${layout.buildDirectory.get()}/libs/${rootProject.name}.jar")
            )
        }
    }
}
