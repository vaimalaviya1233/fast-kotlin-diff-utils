plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.7.3"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":kt-diff-core"))
}

jmh {
    includes.set(listOf("DiffBenchmark"))
    duplicateClassesStrategy.set(org.gradle.api.file.DuplicatesStrategy.WARN)
    warmupIterations.set(1)
    iterations.set(1)
    fork.set(1)
    warmup.set("1s")
    timeOnIteration.set("1s")
}
