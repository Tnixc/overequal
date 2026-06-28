import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
    id("com.diffplug.spotless") version "8.7.0"
}

group = "dev.overequal"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Discord4J 3.3.2 is the first stable release with full Components V2 support
    // (Container / Section / TextDisplay / MediaGallery / Separator).
    implementation("com.discord4j:discord4j-core:3.3.2")

    // Bridge Discord4J's Reactor (Mono/Flux) API to idiomatic Kotlin coroutines.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.11.0")

    // JSON for the on-disk message cache (the merged.jsonl-equivalent corpus).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Kandy (JetBrains' Kotlin plotting library, on Lets-Plot) renders every
    // visualization. kandy-lets-plot transitively brings lets-plot-image-export,
    // so headless PNG export via `plot.save(...)` works with no native setup.
    implementation("org.jetbrains.kotlinx:kandy-lets-plot:0.8.4")

    // CLI flag parsing for headless / local rendering of visualizations.
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    implementation("ch.qos.logback:logback-classic:1.5.37")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.overequal.MainKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// The `application` plugin contributes a `compileJava` task that defaults to the
// running JDK (26 here). Pin it to 21 so it stays consistent with the Kotlin
// jvmTarget above (there is no Java source, but the targets must agree).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.5.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.5.0")
    }
}
