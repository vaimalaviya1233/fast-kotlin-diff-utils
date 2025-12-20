package com.bernaferrari.difflib.algorithm.histogram

import com.bernaferrari.difflib.algorithm.Change
import com.bernaferrari.difflib.algorithm.DiffAlgorithmFactory
import com.bernaferrari.difflib.algorithm.DiffAlgorithmI
import com.bernaferrari.difflib.algorithm.DiffAlgorithmListener
import com.bernaferrari.difflib.algorithm.Equalizer
import com.bernaferrari.difflib.algorithm.myers.MyersDiff
import com.bernaferrari.difflib.patch.DeltaType

import com.bernaferrari.difflib.algorithm.myers.IntIntMap

class HistogramDiff<T>(
    private val equalizer: Equalizer<T> = defaultEqualizer()
) : DiffAlgorithmI<T> {

    private val myersAlgorithm by lazy { MyersDiff(equalizer) }
    private val usesDefaultEqualizer = equalizer === DEFAULT_EQUALIZER

    override fun computeDiff(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        val result = ArrayList<Change>()
        val sourceHashes = if (usesDefaultEqualizer) IntArray(source.size) { source[it].hashCode() } else null
        val targetHashes = if (usesDefaultEqualizer) IntArray(target.size) { target[it].hashCode() } else null
        
        computeRecursive(source, target, 0, 0, result, progress, sourceHashes, targetHashes)
        return result
    }

    private fun computeRecursive(
        source: List<T>,
        target: List<T>,
        startOrig: Int,
        startRev: Int,
        result: MutableList<Change>,
        progress: DiffAlgorithmListener?,
        sourceHashes: IntArray?,
        targetHashes: IntArray?
    ) {
        val trimmed = trimEdges(source, target)
        val offsetOrig = startOrig + trimmed.offset
        val offsetRev = startRev + trimmed.offset

        val currSource = trimmed.source
        val currTarget = trimmed.target

        // 1. Trivial cases (empty source or target)
        if (currSource.isEmpty() || currTarget.isEmpty()) {
            if (currSource.isNotEmpty()) {
                result.add(
                    Change(
                        DeltaType.DELETE,
                        offsetOrig,
                        offsetOrig + currSource.size,
                        offsetRev,
                        offsetRev
                    )
                )
            } else if (currTarget.isNotEmpty()) {
                result.add(
                    Change(
                        DeltaType.INSERT,
                        offsetOrig,
                        offsetOrig,
                        offsetRev,
                        offsetRev + currTarget.size
                    )
                )
            }
            return
        }

        // 2. Threshold optimization:
        // For small segments, the overhead of finding anchors (Map construction) dominates.
        // Fallback to Myers immediately.
        if (currSource.size + currTarget.size < 256) {
            val changes = myersAlgorithm.computeDiff(currSource, currTarget, progress)
            for (c in changes) {
                result.add(
                    Change(
                        c.deltaType,
                        c.startOriginal + offsetOrig,
                        c.endOriginal + offsetOrig,
                        c.startRevised + offsetRev,
                        c.endRevised + offsetRev
                    )
                )
            }
            return
        }

        // 3. Find unique common lines (Anchors)
        val anchors = if (sourceHashes != null && targetHashes != null) {
            findAnchorsOptimized(currSource, currTarget, sourceHashes, targetHashes, offsetOrig, offsetRev)
        } else {
            findAnchors(currSource, currTarget)
        }

        // 4. If no anchors, fallback to Myers
        if (anchors.isEmpty()) {
            val changes = myersAlgorithm.computeDiff(currSource, currTarget, progress)
            for (c in changes) {
                result.add(
                    Change(
                        c.deltaType,
                        c.startOriginal + offsetOrig,
                        c.endOriginal + offsetOrig,
                        c.startRevised + offsetRev,
                        c.endRevised + offsetRev
                    )
                )
            }
            return
        }

        // 5. Split and Recurse
        var curSrcIdx = 0
        var curTgtIdx = 0

        for (anchor in anchors) {
            val subSrc = currSource.subList(curSrcIdx, anchor.sourceIndex)
            val subTgt = currTarget.subList(curTgtIdx, anchor.targetIndex)

            computeRecursive(
                subSrc,
                subTgt,
                offsetOrig + curSrcIdx,
                offsetRev + curTgtIdx,
                result,
                progress,
                sourceHashes,
                targetHashes
            )

            curSrcIdx = anchor.sourceIndex + 1
            curTgtIdx = anchor.targetIndex + 1
        }

        // Final segment after last anchor
        if (curSrcIdx < currSource.size || curTgtIdx < currTarget.size) {
            val subSrc = currSource.subList(curSrcIdx, currSource.size)
            val subTgt = currTarget.subList(curTgtIdx, currTarget.size)
            computeRecursive(
                subSrc,
                subTgt,
                offsetOrig + curSrcIdx,
                offsetRev + curTgtIdx,
                result,
                progress,
                sourceHashes,
                targetHashes
            )
        }
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

    private fun findAnchorsOptimized(
        source: List<T>,
        target: List<T>,
        sourceHashes: IntArray,
        targetHashes: IntArray,
        sourceOffset: Int,
        targetOffset: Int
    ): List<Anchor> {
        val n = source.size
        val m = target.size
        
        // Use IntIntMap for counting
        val sourceCounts = IntIntMap(minOf(n, 4096))
        val targetCounts = IntIntMap(minOf(m, 4096))

        // First pass: count hash occurrences in the relevant segments
        for (i in 0 until n) {
            val h = sourceHashes[sourceOffset + i]
            sourceCounts.put(h, sourceCounts.get(h) + 1)
        }
        for (i in 0 until m) {
            val h = targetHashes[targetOffset + i]
            targetCounts.put(h, targetCounts.get(h) + 1)
        }

        // Second pass: identifying unique hashes in source
        val uniqueSourcePositions = IntIntMap(minOf(n, 4096))
        for (i in 0 until n) {
            val h = sourceHashes[sourceOffset + i]
            if (sourceCounts.get(h) == 1) {
                uniqueSourcePositions.put(h, i)
            }
        }

        // Third pass: iterate target, check if matches unique source
        val candidates = mutableListOf<Anchor>()
        for (j in 0 until m) {
            val h = targetHashes[targetOffset + j]
            if (sourceCounts.get(h) == 1 && targetCounts.get(h) == 1) {
                val i = uniqueSourcePositions.get(h)
                // If i was found (and map behaves correctly), check object equality
                // IntIntMap returns 0 if missing. 0 is valid index. 
                // But since count==1, it MUST be in uniqueSourcePositions.
                // We trust the structure.
                
                if (source[i] == target[j]) {
                    candidates.add(Anchor(i, j))
                }
            }
        }

        return longestIncreasingSubsequence(candidates)
    }

    // Standard LIS (Longest Increasing Subsequence) algorithm O(N log N)
    private fun longestIncreasingSubsequence(candidates: List<Anchor>): List<Anchor> {
        val size = candidates.size
        if (size == 0) return emptyList()

        val predecessors = IntArray(size) { -1 }
        val tails = IntArray(size)
        var length = 0

        for (i in 0 until size) {
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

    private data class Anchor(val sourceIndex: Int, val targetIndex: Int)

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

    companion object {
        private val DEFAULT_EQUALIZER: Equalizer<Any?> = { a, b -> a == b }

        fun factory(): DiffAlgorithmFactory = object : DiffAlgorithmFactory {
            override fun <T> create(): DiffAlgorithmI<T> = HistogramDiff()
            override fun <T> create(equalizer: Equalizer<T>): DiffAlgorithmI<T> = HistogramDiff(equalizer)
        }

        private fun <T> defaultEqualizer(): Equalizer<T> = DEFAULT_EQUALIZER
    }
}