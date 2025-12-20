import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                    freeCompilerArgs.add("-Xjsr305=strict")
                }
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs()
    }

    // Native - Apple
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()
    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    // Native - Linux
    linuxX64()
    linuxArm64()

    // Native - Windows
    mingwX64()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
                implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
                runtimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
                implementation("org.assertj:assertj-core:3.27.3")
            }
        }
    }
}

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Automatic-Module-Name"] = "com.bernaferrari.difflib.core"
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    
    coordinates(
        groupId = "io.github.bernaferrari",
        artifactId = "fast-kotlin-diff-core",
        version = "1.0.0"
    )

    pom {
        name.set("Fast Kotlin Diff Utils Core")
        description.set("Core algorithms")
        url.set("https://github.com/bernaferrari/fast-kotlin-diff-utils")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("bernaferrari")
                name.set("Bernardo Ferrari")
            }
        }
        scm {
            url.set("https://github.com/bernaferrari/fast-kotlin-diff-utils")
            connection.set("scm:git:git://github.com/bernaferrari/fast-kotlin-diff-utils.git")
            developerConnection.set("scm:git:ssh://github.com/bernaferrari/fast-kotlin-diff-utils.git")
        }
    }
}
