plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("multiplatform") version "2.3.0" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
    id("com.diffplug.spotless") version "6.25.0"
    id("com.vanniktech.maven.publish") version "0.35.0" apply false
}

allprojects {
    group = "io.github.bernaferrari"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

spotless {
    format("misc") {
        target("*.md", "**/.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
