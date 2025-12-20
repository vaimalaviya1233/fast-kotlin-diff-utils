plugins {
    kotlin("jvm")
    `java-library`
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":kt-diff-core"))
    implementation(project(":kt-diff-jvm-io"))
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    
    coordinates(
        groupId = "io.github.bernaferrari",
        artifactId = "fast-kotlin-diff-jgit",
        version = "1.0.0"
    )
    
    pom {
        name.set("Fast Kotlin Diff Utils JGit")
        description.set("JGit histogram diff adapter")
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
