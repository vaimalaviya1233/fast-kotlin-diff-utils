package com.bernaferrari.difflib.benchmark

import com.bernaferrari.difflib.DiffUtils
import com.bernaferrari.difflib.algorithm.myers.MyersDiff.ParallelConfig
import com.bernaferrari.difflib.algorithm.patience.PatienceDiff
import com.bernaferrari.difflib.patch.Patch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

/**
 * Micro benchmarks to help track slow spots inside the Myers diff implementation.
 *
 * A deterministic random dataset is used so different runs are comparable.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
open class DiffBenchmark {

    @Param("myers", "patience")
    lateinit var algorithm: String

    @Param("500", "2000")
    var lineCount: Int = 500

    @Param("0.03", "0.10")
    var changeRatio: Double = 0.05

    @Param("0.0", "0.45")
    var stableEdgeRatio: Double = 0.0

    @Param("off", "on")
    lateinit var parallelMode: String

    @Param("copy", "zero")
    lateinit var chunkCopyMode: String

    private lateinit var original: List<String>
    private lateinit var revised: List<String>
    private lateinit var preparedPatch: Patch<String>
    private var runParallelSegments: Boolean = false
    private var copyChunks: Boolean = true

    @Setup(Level.Trial)
    fun generateSamples() {
        val random = Random(1234)
        original = MutableList(lineCount) { index -> "line-$index-${random.nextInt()}" }
        val working = original.toMutableList()

        val maxEdge = (lineCount / 2) - 1
        val edgeSize = minOf((lineCount * stableEdgeRatio).roundToInt(), maxEdge).coerceAtLeast(0)
        val prefixSize = edgeSize
        val suffixSize = edgeSize

        val edits = max(1, (lineCount * changeRatio).roundToInt())
        repeat(edits) { idx ->
            val rangeStart = prefixSize
            val rangeEndExclusive = max(rangeStart + 1, working.size - suffixSize)
            val pos = if (rangeEndExclusive <= rangeStart) {
                rangeStart
            } else {
                random.nextInt(rangeStart, rangeEndExclusive)
            }
            working[pos] = working[pos] + "-mutated-$idx"
        }
        repeat(edits / 2) { idx ->
            val rangeStart = prefixSize
            val rangeEndExclusive = max(rangeStart + 1, working.size - suffixSize + 1)
            val pos = if (rangeEndExclusive <= rangeStart) {
                rangeStart
            } else {
                random.nextInt(rangeStart, rangeEndExclusive)
            }
            working.add(pos, "inserted-$idx-${random.nextInt()}")
        }
        repeat(edits / 3) {
            val removableStart = prefixSize
            val removableEnd = max(removableStart + 1, working.size - suffixSize)
            if (working.isNotEmpty() && removableEnd > removableStart) {
                working.removeAt(random.nextInt(removableStart, removableEnd))
            }
        }
        revised = working
    }

    @Setup(Level.Iteration)
    fun preparePatch() {
        runParallelSegments = parallelMode == "on"
        copyChunks = chunkCopyMode == "copy"

        DiffUtils.withDefaultParallelConfig(
            ParallelConfig(
                enableParallelSegments = runParallelSegments,
                minSegmentCost = 1,
                minTotalCost = 1
            )
        )
        preparedPatch = computePatch(includeEquals = false)
    }

    @Benchmark
    fun diffDefault(blackhole: Blackhole) {
        blackhole.consume(computePatch(includeEquals = false))
    }

    @Benchmark
    fun diffWithEquals(blackhole: Blackhole) {
        blackhole.consume(computePatch(includeEquals = true))
    }

    @Benchmark
    fun applyPreparedPatch(blackhole: Blackhole) {
        blackhole.consume(preparedPatch.applyTo(original))
    }

    @Benchmark
    fun repeatedDeltaAccess(blackhole: Blackhole) {
        var counter = 0
        repeat(500) {
            counter += preparedPatch.deltas.size
        }
        blackhole.consume(counter)
    }

    private fun computePatch(includeEquals: Boolean): Patch<String> {
        val diffAlgo = when (algorithm) {
            "patience" -> PatienceDiff.factory().create<String>()
            else -> DiffUtils.DEFAULT_DIFF.create<String>()
        }
        return DiffUtils.diff(
            original,
            revised,
            diffAlgo,
            null,
            includeEquals,
            copyChunks
        )
    }
}
