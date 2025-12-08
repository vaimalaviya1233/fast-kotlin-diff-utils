package com.bernaferrari.difflib.algorithm.histogram

import com.bernaferrari.difflib.algorithm.Change
import com.bernaferrari.difflib.algorithm.DiffAlgorithmFactory
import com.bernaferrari.difflib.algorithm.DiffAlgorithmI
import com.bernaferrari.difflib.algorithm.DiffAlgorithmListener
import com.bernaferrari.difflib.algorithm.Equalizer
import com.bernaferrari.difflib.algorithm.myers.MyersDiff
import com.bernaferrari.difflib.patch.DeltaType

class HistogramDiff<T>(
    private val equalizer: Equalizer<T> = defaultEqualizer()
) : DiffAlgorithmI<T> {

    override fun computeDiff(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        // 1. Trim common prefix/suffix to simplify the problem
        val trimmed = trimEdges(source, target)
        if (trimmed.source.isEmpty() || trimmed.target.isEmpty()) {
            return applyOffset(
                buildTrivialDiff(trimmed.source.size, trimmed.target.size),
                trimmed.offset,
                trimmed.offset
            )
        }

        // 2. Find unique common lines (Anchors)
        val anchors = findAnchors(trimmed.source, trimmed.target)

        // 3. If anchors found, split and recurse. Otherwise fallback to Myers.
        val changes = if (anchors.isNotEmpty()) {
            buildDiffWithAnchors(trimmed.source, trimmed.target, anchors, progress)
        } else {
            // Fallback to Myers for leaf nodes with no unique lines
            MyersDiff(equalizer).computeDiff(trimmed.source, trimmed.target, progress)
        }

        return applyOffset(changes, trimmed.offset, trimmed.offset)
    }

    private fun findAnchors(source: List<T>, target: List<T>): List<Anchor> {
        val sourceCounts = HashMap<T, Int>()
        val targetCounts = HashMap<T, Int>()

        source.forEach { item -> sourceCounts[item] = (sourceCounts[item] ?: 0) + 1 }
        target.forEach { item -> targetCounts[item] = (targetCounts[item] ?: 0) + 1 }

        val uniqueSourcePositions = HashMap<T, Int>()
        source.forEachIndexed { index, item ->
            if (sourceCounts[item] == 1) {
                uniqueSourcePositions[item] = index
            }
        }

        val candidates = mutableListOf<Anchor>()
        target.forEachIndexed { index, item ->
            if (sourceCounts[item] == 1 && targetCounts[item] == 1) {
                val sourceIndex = uniqueSourcePositions[item]
                if (sourceIndex != null) {
                    candidates.add(Anchor(sourceIndex, index))
                }
            }
        }

        return longestIncreasingSubsequence(candidates)
    }

    private fun buildDiffWithAnchors(
        source: List<T>,
        target: List<T>,
        anchors: List<Anchor>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        val segments = collectAnchorSegments(anchors, source.size, target.size)
        val result = mutableListOf<Change>()

        for (segment in segments) {
            val subSource = source.subList(segment.sourceStart, segment.sourceEnd)
            val subTarget = target.subList(segment.targetStart, segment.targetEnd)

            val subChanges = computeDiff(subSource, subTarget, progress)
            result.addAll(applyOffset(subChanges, segment.sourceStart, segment.targetStart))
        }

        return result
    }

    private fun collectAnchorSegments(
        anchors: List<Anchor>,
        sourceSize: Int,
        targetSize: Int
    ): List<AnchorSegment> {
        val segments = mutableListOf<AnchorSegment>()
        var sourceStart = 0
        var targetStart = 0

        for (anchor in anchors) {
            // Region before the anchor
            if (anchor.sourceIndex > sourceStart || anchor.targetIndex > targetStart) {
                segments.add(
                    AnchorSegment(
                        sourceStart,
                        anchor.sourceIndex,
                        targetStart,
                        anchor.targetIndex
                    )
                )
            }
            // The anchor itself is a match, so we skip it in the diff (it's not a change)
            sourceStart = anchor.sourceIndex + 1
            targetStart = anchor.targetIndex + 1
        }

        // Region after the last anchor
        if (sourceStart < sourceSize || targetStart < targetSize) {
            segments.add(
                AnchorSegment(
                    sourceStart,
                    sourceSize,
                    targetStart,
                    targetSize
                )
            )
        }
        return segments
    }

    // --- Helper classes & functions (duplicated/adapted from MyersDiff to be self-contained or slightly modified) ---

    private data class Anchor(val sourceIndex: Int, val targetIndex: Int)

    private data class AnchorSegment(
        val sourceStart: Int,
        val sourceEnd: Int,
        val targetStart: Int,
        val targetEnd: Int
    )

    private data class TrimmedLists<T>(
        val source: List<T>,
        val target: List<T>,
        val offset: Int
    )

    private fun trimEdges(source: List<T>, target: List<T>): TrimmedLists<T> {
        var start = 0
        val maxPrefix = minOf(source.size, target.size)
        while (start < maxPrefix && equalizer(source[start], target[start])) {
            start++
        }
        var endSource = source.size
        var endTarget = target.size
        while (
            endSource > start &&
            endTarget > start &&
            equalizer(source[endSource - 1], target[endTarget - 1])
        ) {
            endSource--
            endTarget--
        }
        val trimmedSource = source.subList(start, endSource)
        val trimmedTarget = target.subList(start, endTarget)
        return TrimmedLists(trimmedSource, trimmedTarget, start)
    }

    private fun buildTrivialDiff(sourceSize: Int, targetSize: Int): List<Change> {
        if (sourceSize == 0 && targetSize == 0) return emptyList()
        if (sourceSize == 0) return listOf(Change(DeltaType.INSERT, 0, 0, 0, targetSize))
        if (targetSize == 0) return listOf(Change(DeltaType.DELETE, 0, sourceSize, 0, 0))
        return emptyList()
    }

    private fun applyOffset(
        changes: List<Change>,
        originalOffset: Int,
        revisedOffset: Int
    ): List<Change> {
        if ((originalOffset == 0 && revisedOffset == 0) || changes.isEmpty()) {
            return changes
        }
        return changes.map { change ->
            Change(
                change.deltaType,
                change.startOriginal + originalOffset,
                change.endOriginal + originalOffset,
                change.startRevised + revisedOffset,
                change.endRevised + revisedOffset
            )
        }
    }

    // Standard LIS (Longest Increasing Subsequence) algorithm O(N log N)
    private fun longestIncreasingSubsequence(candidates: List<Anchor>): List<Anchor> {
        val size = candidates.size
        if (size == 0) return emptyList()

        val predecessors = IntArray(size) { -1 }
        val tails = IntArray(size)
        var length = 0

        for (i in 0 until size) {
            // We use the sourceIndex as the value to check for increasing order
            // Since candidates are already sorted by targetIndex (because we iterated target),
            // finding LIS on sourceIndex gives us the LCS of unique lines.
            // Wait, standard Patience iterates target and looks up source index.
            // candidates.add(Anchor(sourceIndex, index)) -> index is targetIndex.
            // Iteration order is targetIndex 0..M. So candidates are sorted by targetIndex.
            // So we just need LIS of sourceIndex.
            val value = candidates[i].sourceIndex
            var low = 0
            var high = length
            while (low < high) {
                val mid = (low + high) ushr 1
                val midValue = candidates[tails[mid]].sourceIndex
                if (midValue < value) {
                    low = mid + 1
                } else {
                    high = mid
                }
            }
            if (low > 0) {
                predecessors[i] = tails[low - 1]
            }
            tails[low] = i
            if (low == length) {
                length++
            }
        }
        if (length == 0) {
            return emptyList()
        }
        var idx = tails[length - 1]
        val result = mutableListOf<Anchor>()
        while (idx >= 0) {
            result.add(candidates[idx])
            idx = predecessors[idx]
        }
        result.reverse()
        return result
    }

    companion object {
        private val DEFAULT_EQUALIZER: Equalizer<Any?> = { a, b -> a == b }

        fun factory(): DiffAlgorithmFactory = object : DiffAlgorithmFactory {
            override fun <T> create(): DiffAlgorithmI<T> = HistogramDiff()
            override fun <T> create(equalizer: Equalizer<T>): DiffAlgorithmI<T> = HistogramDiff(equalizer)
        }

        private fun <T> defaultEqualizer(): Equalizer<T> = DEFAULT_EQUALIZER
    }
}