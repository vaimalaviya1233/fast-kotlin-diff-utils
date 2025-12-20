plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

kotlin {
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

    sourceSets {
        jvmMain {
            dependencies {
                api(project(":kt-diff-core"))
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    
    coordinates(
        groupId = "io.github.bernaferrari",
        artifactId = "fast-kotlin-diff-jvm-io",
        version = "1.0.0"
    )

    pom {
        name.set("Fast Kotlin Diff Utils JVM IO")
        description.set("Unified diff parsing and writing")
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
