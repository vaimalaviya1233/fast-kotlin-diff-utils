# KT Diff Utils

Lightweight Kotlin diff utilities optimized for Android and Kotlin Multiplatform projects. KT Diff Utils started as the change engine inside [ChangeDetection](https://github.com/bernaferrari/ChangeDetection), where a Java port of java-diff-utils kept app size down. This codebase grew from that foundation into a full Kotlin-first successor to java-diff-utils: same API surface, but backed by profiling-driven Myers tweaks and modern build tooling.

## Highlights

- **Kotlin-first**: idiomatic APIs, null-safety, and multiplatform-friendly collections. All blocking `java.io` usage is isolated to optional modules, and the entire diff/patch implementation now lives in `commonMain`, so you can wire new Kotlin Multiplatform targets without touching the algorithms.
- **Performance obsessed**: profiled and benchmarked with multiple optimization layers:
  - Hash-based snake detection for ~50% fewer equality comparisons
  - Shared-prefix/suffix trimming to skip unchanged content
  - Patience-style anchors using unique elements for parallel segment processing
  - Optimized parallelization with configurable thresholds
  - Cached delta ordering eliminates repeated sorts
- **Modular**: pick the pieces you need.
  - `kt-diff-core`: Myers and Histogram diffs, patch apply/restore, diff-row generation – 100% Kotlin.
  - `kt-diff-jvm-io`: optional unified diff parser/writer plus legacy Java serialization helpers.
  - `kt-diff-jgit`: histogram diff adapter backed by Eclipse JGit.
- **Benchmarked**: run `./gradlew :kt-diff-bench:jmh` to reproduce the numbers below (see `kt-diff-bench/src/jmh/...` for workloads).

## Performance

### Benchmark Results (JDK 22)

Core diff performance across different workloads:

| Scenario | Time | Notes |
| --- | --- | --- |
| `DiffUtils.diff` – 2,000 lines, 10% edits | **0.27 ms/op** | Default equalizer with hash optimization |
| `DiffUtils.diff` – 2,000 lines, 10% edits, 45% stable edges | **0.04 ms/op** | Real-world scenario with unchanged headers/footers |
| `diffWithEquals` – 2,000 lines, 10% edits | **0.29 ms/op** | Custom equalizer path |
| `diffWithEquals` – 2,000 lines, 10% edits, 45% stable edges | **0.04 ms/op** | 94% faster than worst-case |
| Applying prepared patch – 2,000 lines | **0.05 ms/op** | Efficient patch data structures |
| Repeated `patch.deltas` access (500×) | **~0.001 ms/op** | Cached ordering eliminates re-sorts |

### Stable Edge Optimization

Real-world diffs typically have unchanged content at the beginning and end of files (imports, headers, footers, boilerplate). KT Diff Utils recognizes this pattern and delivers **5-16x speedup** for these common cases:

| Workload (2,000 lines, 10% edits) | No Stable Edges | 45% Stable Edges | Speedup |
| --- | --- | --- | --- |
| `diffDefault` | 0.27 ms/op | 0.04 ms/op | **6.8×** |
| `diffWithEquals` | 0.29 ms/op | 0.04 ms/op | **7.3×** |

### Histogram vs Myers

The new `HistogramDiff` algorithm (a.k.a. Patience Diff) offers competitive performance, particularly for larger files or sparse changes where it can outperform Myers while producing more readable output (e.g. by avoiding "sliding" matches).

| Algorithm | Time (2,000 lines, 10% edits) | Notes |
| --- | --- | --- |
| Myers | **0.28 ms/op** | Optimized for minimal edit scripts |
| Histogram | **~0.30 ms/op** | Comparable speed; prioritizes unique common lines |

### vs java-diff-utils

| Scenario (JDK 22) | java-diff-utils | KT Diff Utils | Speedup |
| --- | --- | --- | --- |
| `DiffUtils.diff` – 2,000 lines, 10% edits | 2.73 ms/op | **0.27 ms/op** | ~10× faster |
| `diffWithEquals` – same workload | 2.83 ms/op | **0.29 ms/op** | ~10× faster |
| Applying prepared patch | 0.05 ms/op | **0.05 ms/op** | Comparable |
| Repeated `patch.deltas` access (500×) | 0.67 ms/op | **~0.001 ms/op** | ~670× faster |

### vs kotlin-multiplatform-diff

We cloned [petertrr/kotlin-multiplatform-diff](https://github.com/petertrr/kotlin-multiplatform-diff) at `79b80e1` and reused our `DiffBenchmark` dataset to compare JVM performance. Both libraries were run on JDK 22, identical inputs.

| Workload | KT Diff Utils | kotlin-multiplatform-diff | Speedup |
| --- | --- | --- | --- |
| `diffDefault`, 2,000 lines, 10% edits | **0.27 ms/op** | 3.044 ms/op | ~11× faster |
| `diffDefault`, 2,000 lines, 10% edits, 45% stable edges | **0.04 ms/op** | 0.701 ms/op | ~18× faster |
| `diffWithEquals`, 2,000 lines, 10% edits | **0.29 ms/op** | 3.737 ms/op | ~13× faster |
| `applyPreparedPatch`, 2,000 lines, 10% edits | **0.05 ms/op** | 0.056 ms/op | Comparable |
| `repeatedDeltaAccess`, 2,000 lines, 10% edits | **≈0.001 ms/op** | 1.209 ms/op | ~1200× faster |

Smaller workloads (500 lines / 3% edits) show similar patterns. To reproduce, temporarily add the competitor's `src/commonMain/kotlin` directory to the `kt-diff-bench` JMH source set and run `./gradlew :kt-diff-bench:jmh`.

Benchmark reports land in `kt-diff-bench/build/results/jmh/results.txt`. Adjust workloads via `kt-diff-bench/src/jmh/kotlin/com/bernaferrari/difflib/benchmark/DiffBenchmark.kt`.

## Optimization Techniques

KT Diff Utils employs several optimization strategies to achieve its performance:

### Hash-Based Snake Detection

The Myers algorithm spends most of its time in "snake" detection—finding sequences of matching elements. We pre-compute hash codes for all elements and check hash equality before calling `equals()`:

```kotlin
// Fast path: hash mismatch means elements definitely differ
while (i < n && j < m && origHashes[i] == revHashes[j] && orig[i] == rev[j]) {
    i++; j++
}
```

This reduces expensive `equals()` calls by ~50% when elements don't match, which is the common case during diff computation.

### Prefix/Suffix Trimming

Before running Myers, we trim matching elements from both ends:

```kotlin
val trimmed = trimEdges(source, target)  // O(min(N,M)) scan
// Only diff the changed middle portion
```

Files with unchanged headers (imports, license blocks) or footers (closing braces, boilerplate) benefit significantly—the algorithm only processes the genuinely different content.

### Patience-Style Anchors

For large inputs, we identify unique elements that appear exactly once in both sequences. These "anchors" let us split the diff into independent segments:

```kotlin
val anchors = findAnchorsOptimized(source, target, sourceHashes, targetHashes)
// Process segments between anchors (potentially in parallel)
```

This technique, inspired by patience diff, improves both performance and diff quality for structured content like source code.

### Configurable Parallelization

When anchor-based segmentation creates multiple large segments, KT Diff Utils can process them in parallel:

```kotlin
MyersDiff(parallelConfig = ParallelConfig(
    enableParallelSegments = true,
    minSegmentCost = 2_000,    // Minimum segment size for parallel processing
    minTotalCost = 8_000       // Minimum total work before enabling parallelism
))
```

Parallelization adds overhead, so it's only enabled when the workload justifies it.

## Related Kotlin Ports

- [petertrr/kotlin-multiplatform-diff](https://github.com/petertrr/kotlin-multiplatform-diff) focuses on sharing a java-diff-utils port across JVM/JS/Native. KT Diff Utils narrows in on JVM/Android today, trading breadth for optimized Myers and patience-style heuristics.
- [GitLiveApp/kotlin-diff-utils](https://github.com/GitLiveApp/kotlin-diff-utils) offers another JVM-first port. KT Diff Utils targets the same API compatibility but adds benchmark-driven optimizations and optional modules for unified diff IO or JGit histogram support.

If you already depend on one of those ports, swapping to KT Diff Utils should only require updating package names to `com.bernaferrari.difflib.*`.

## Installation

There's no published artifact yet—grab the source from GitHub (fork or clone) and include it directly:

1. Keep this repo next to your app/library and wire it via a composite build (adjust the path if your folder layout differs):

   ```kotlin
   // settings.gradle.kts of your consumer project
   includeBuild("../kt-diff-utils")
   ```

2. Declare the module dependency:

   ```kotlin
   dependencies {
       implementation(project(":kt-diff-core"))
       // Optional JVM helpers:
       // implementation(project(":kt-diff-jvm-io"))
       // Histogram diff support:
       // implementation(project(":kt-diff-jgit"))
   }
   ```

If you need a Maven artifact internally, run `./gradlew publishToMavenLocal` and reference `com.bernaferrari.difflib:kt-diff-*`. Public artifacts are not published yet.

## Usage

KT Diff Utils mirrors the java-diff-utils API. Existing examples on the [original wiki](https://github.com/java-diff-utils/java-diff-utils/wiki) compile unchanged; simply update imports to `com.bernaferrari.difflib.*` and, if needed, swap coordinates to the modules above.

Key entry points:

```kotlin
val patch = DiffUtils.diff(originalList, revisedList)
val result = patch.applyTo(originalList)
val diffRows = DiffRowGenerator.create()
    .ignoreWhiteSpaces(true)
    .showInlineDiffs(true)
    .build()
    .generateDiffRows(originalLines, revisedLines)
```

### Custom Equalizer

For domain-specific comparison logic:

```kotlin
val patch = DiffUtils.diff(
    originalList,
    revisedList,
    MyersDiff { a, b -> a.id == b.id }  // Compare by ID only
)
```

### Parallel Processing

Enable parallel segment processing for large diffs:

```kotlin
val algorithm = MyersDiff<String>(
    parallelConfig = MyersDiff.ParallelConfig(
        enableParallelSegments = true,
        minSegmentCost = 2_000,
        minTotalCost = 8_000
    )
)
val patch = DiffUtils.diff(original, revised, algorithm)
```

Unified diff parsing/writing lives in `kt-diff-jvm-io` (`UnifiedDiffReader`, `UnifiedDiffWriter`). Histogram diff support is exposed via `kt-diff-jgit`.

## Multiplatform Status

- `kt-diff-core` is published as a Kotlin Multiplatform module (`commonMain` + `jvmMain`). Only the JVM target is enabled today, but every algorithm already sits in common code, so adding JS/Native/Wasm targets is mostly a Gradle switch away.
- `kt-diff-jvm-io` intentionally uses `BufferedReader`, `InputStream`, and `Writer` for GNU diff compatibility.

## Contributing

- Build & test: `./gradlew test`
- Benchmarks: `./gradlew :kt-diff-bench:jmh`
- Formatting: `./gradlew spotlessCheck`

Issues and pull requests are welcome.

## License

MIT License – see [LICENSE](LICENSE).
