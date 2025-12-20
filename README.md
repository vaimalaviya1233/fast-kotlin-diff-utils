<p align="center">
  <img src=".github/header.png" alt="KT Diff Utils" width="100%">
</p>

# Fast Kotlin Diff Utils

Fast, Kotlin-first diff utilities for Android and Kotlin Multiplatform. Drop-in replacement for java-diff-utils — same API, ~10× faster.

```kotlin
val patch = DiffUtils.diff(original, revised)
val result = patch.applyTo(original)
```

## Installation

Include via composite build:

```kotlin
// settings.gradle.kts
includeBuild("../kt-diff-utils")
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":kt-diff-core"))
    
    // Optional:
    // implementation(project(":kt-diff-jvm-io"))  // Patch file (.patch) parsing
    // implementation(project(":kt-diff-jgit"))    // Histogram diff via JGit
}
```

For internal Maven artifacts: `./gradlew publishToMavenLocal` → `com.bernaferrari.difflib:kt-diff-*`

## Modules

| Module | Purpose |
|--------|---------|
| `kt-diff-core` | Myers diff, Histogram diff, patch apply/restore, diff-row generation. 100% Kotlin, multiplatform-ready. |
| `kt-diff-jvm-io` | Unified diff parser/writer, Java serialization helpers. JVM only. |
| `kt-diff-jgit` | Histogram diff adapter via Eclipse JGit. |

## Usage

**Basic diff and patch:**

```kotlin
val original = listOf("A", "B", "C")
val revised = listOf("A", "D", "C")

val patch = DiffUtils.diff(original, revised)
val result = patch.applyTo(original)  // ["A", "D", "C"]
```

**Side-by-side diff rows:**

```kotlin
val generator = DiffRowGenerator.create()
    .showInlineDiffs(true)
    .ignoreWhiteSpaces(true)
    .build()

val rows = generator.generateDiffRows(originalLines, revisedLines)
```

**Custom equalizer:**

```kotlin
val patch = DiffUtils.diff(
    users,
    updatedUsers,
    MyersDiff { a, b -> a.id == b.id }  // Compare by ID only
)
```

**Unified diff (requires `kt-diff-jvm-io`):**

```kotlin
val unifiedDiff = UnifiedDiffReader.parseUnifiedDiff(inputStream)
val patch = unifiedDiff.spawnPatch()
```

## Performance

~10× faster than java-diff-utils on typical workloads. The speedup comes from:

- **Hash-based snake detection** — check hash equality before `equals()`, skipping ~50% of comparisons
- **Primitive Collections (`IntIntMap`)** — custom open-addressing map avoids auto-boxing overhead in hot paths
- **Prefix/suffix trimming** — skip unchanged headers and footers before diffing
- **Patience-style anchors** — split large diffs at unique lines for segment processing (powered by `IntIntMap`)
- **Global Hashing** — pre-compute hashes once for recursive algorithms (HistogramDiff)
- **Cached delta ordering** — sort once, not on every access

<details>
<summary><strong>Benchmark results (JDK 22, 2,000 lines, 10% edits)</strong></summary>

#### vs java-diff-utils

| Scenario | java-diff-utils | Fast Kotlin Diff Utils | Speedup |
|----------|-----------------|---------------|---------|
| `DiffUtils.diff` | 2.73 ms | **0.17 ms** | ~16× |
| With custom equalizer | 2.83 ms | **0.19 ms** | ~15× |
| Patch application | 0.05 ms | **0.05 ms** | — |
| `patch.deltas` access (500×) | 0.67 ms | **0.001 ms** | ~670× |

#### Myers vs Histogram (2k lines)

Both algorithms are highly optimized. Myers is slightly faster for general use, while Histogram acts as a fallback or alternative.

| Algorithm | Latency |
|-----------|---------|
| Myers | **0.19 ms** |
| Histogram | **0.21 ms** |

#### vs kotlin-multiplatform-diff

| Scenario | kotlin-multiplatform-diff | Fast Kotlin Diff Utils | Speedup |
|----------|---------------------------|---------------|---------|
| Diff (no stable edges) | 3.04 ms | **0.17 ms** | ~18× |
| Diff (45% stable edges) | 0.70 ms | **0.04 ms** | ~18× |
| `patch.deltas` access (500×) | 1.21 ms | **0.001 ms** | ~1200× |

#### Stable edge optimization

Real files often have unchanged imports, headers, and footers. Fast Kotlin Diff Utils trims these automatically:

| Workload | No stable edges | 45% stable edges | Speedup |
|----------|-----------------|------------------|---------|
| `DiffUtils.diff` | 0.17 ms | **0.04 ms** | ~4× |

Run benchmarks: `./gradlew :kt-diff-bench:jmh`

</details>

## Migrating from java-diff-utils

Update your imports:

```diff
- import com.github.difflib.*
+ import com.bernaferrari.difflib.*
```

The API surface is identical. Existing code should compile unchanged.


## Related Projects

- [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) — The original Java library
- [kotlin-multiplatform-diff](https://github.com/petertrr/kotlin-multiplatform-diff) — KMP port, focuses on platform breadth
- [GitLiveApp/kotlin-diff-utils](https://github.com/GitLiveApp/kotlin-diff-utils) — Another KMP port

## Contributing

```bash
./gradlew test               # Build & test
./gradlew :kt-diff-bench:jmh # Benchmarks  
./gradlew spotlessCheck      # Formatting
```

## License

MIT — see [LICENSE](LICENSE).
