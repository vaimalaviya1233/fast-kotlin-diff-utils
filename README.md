<p align="center">
  <img src=".github/header.png" alt="Fast Kotlin Diff Utils" width="100%">
</p>

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bernaferrari/fast-kotlin-diff-core)](https://central.sonatype.com/artifact/io.github.bernaferrari/fast-kotlin-diff-core)

# Fast Kotlin Diff Utils

A drop-in replacement for [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) that is **~10× faster**.

Fast Kotlin Diff Utils started as the change engine for [ChangeDetection](https://github.com/bernaferrari/ChangeDetection) since [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) was causing UI freezes. By rewriting the core in Kotlin and applying profiling-driven optimizations (with help from _Claude Opus 4.5_, _GPT-5.2_, and _Gemini 3 Pro_), we achieved massive performance gains without changing the API.

## Why switch?

### 🚀 It's much faster

On typical file diffs (2,000 lines, 10% edits), Fast Kotlin Diff Utils operates in **microseconds** where the original took milliseconds.

| Scenario                     | java-diff-utils | Fast Kotlin Diff Utils | Speedup   |
| :--------------------------- | :-------------- | :--------------------- | :-------- |
| `DiffUtils.diff`             | 2.73 ms         | **0.17 ms**            | **~16×**  |
| Custom Equalizer             | 2.83 ms         | **0.19 ms**            | **~15×**  |
| `patch.deltas` access (500×) | 0.67 ms         | **0.001 ms**           | **~670×** |

### ⚡️ Smart optimizations

We don't just run the same algorithm in proper Kotlin; we improved it:

- **Hash-based snake detection**: We verify hash equality before expensive `equals()` calls, skipping ~50% of comparisons.
- **Stable edge trimming**: Real files often have unchanged headers/footers (imports, licenses). We detect and trim them _before_ running the O(ND) algorithm, delivering **4-18× speedups** for common cases.
- **Patience-style anchors**: For large files, we use unique lines as anchors to split the problem into smaller, independent chunks.
- **Primitive collections**: Our custom `IntIntMap` stores raw integers directly, avoiding the heavy memory overhead of allocating millions of `Integer` objects.

## Installation

```kotlin
dependencies {
    implementation("io.github.bernaferrari:fast-kotlin-diff-core:1.0.0")

    // Optional extensions:
    // implementation("io.github.bernaferrari:fast-kotlin-diff-jvm-io:1.0.0")  // Unified diff parsing/writing
    // implementation("io.github.bernaferrari:fast-kotlin-diff-jgit:1.0.0")    // Histogram diff via JGit
}
```

## Usage

The API is identical to java-diff-utils, so you can likely just change your imports.

**Basic diff:**

```kotlin
import com.bernaferrari.difflib.DiffUtils

val patch = DiffUtils.diff(original, revised)
val result = patch.applyTo(original)
```

**Side-by-side diff rows:**

```kotlin
val generator = DiffRowGenerator.create()
    .showInlineDiffs(true)
    .ignoreWhiteSpaces(true)
    .build()

val rows = generator.generateDiffRows(originalLines, revisedLines)
```

**Complex comparison (Custom Equalizer):**

```kotlin
// Compare objects by ID only, ignoring other fields
val patch = DiffUtils.diff(
    users,
    updatedUsers,
    MyersDiff { a, b -> a.id == b.id }
)
```

## Modules

- **`fast-kotlin-diff-core`**: The main library. Myers diff, patch apply/restore, diff-row generation. 100% Kotlin, Multiplatform-ready.
- **`fast-kotlin-diff-jvm-io`**: Unified diff parser/writer and Java serialization helpers. (JVM only).
- **`fast-kotlin-diff-jgit`**: Histogram diff adapter via Eclipse JGit.

## Contributing

We welcome contributions!

- Run tests: `./gradlew test`
- Run benchmarks: `./gradlew :fast-kotlin-diff-bench:jmh`
- Format code: `./gradlew spotlessCheck`

## Other Kotlin Ports

There are other Kotlin ports of [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils):

- **[petertrr/kotlin-multiplatform-diff](https://github.com/petertrr/kotlin-multiplatform-diff)**
- **[GitLiveApp/kotlin-diff-utils](https://github.com/GitLiveApp/kotlin-diff-utils)**

These are straight ports—they convert the Java code to Kotlin but don't change the underlying algorithm. **Fast Kotlin Diff Utils** went further: we profiled real workloads, identified bottlenecks, and rewrote the core. The result is **~10× faster** performance on typical diffs. If you're migrating from one of these, just update your imports to `com.bernaferrari.difflib.*`.

## License

MIT — see [LICENSE](LICENSE).
