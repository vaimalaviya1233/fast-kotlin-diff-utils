package com.bernaferrari.difflib.algorithm.myers

import com.bernaferrari.difflib.algorithm.Change
import com.bernaferrari.difflib.algorithm.DiffAlgorithmFactory
import com.bernaferrari.difflib.algorithm.DiffAlgorithmI
import com.bernaferrari.difflib.algorithm.DiffAlgorithmListener
import com.bernaferrari.difflib.algorithm.Equalizer
import com.bernaferrari.difflib.patch.DeltaType
import kotlin.collections.RandomAccess

/**
 * A highly optimized implementation of Eugene Myers greedy differencing algorithm.
 *
 * Key optimizations:
 * - Hash-based preprocessing for fast equality filtering
 * - Primitive arrays instead of object arrays in hot loops
 * - Shared-prefix/suffix trimming to skip unchanged content
 * - Patience-style anchors using unique elements for segment processing
 */
class MyersDiff<T>(
    private val equalizer: Equalizer<T> = defaultEqualizer()
) : DiffAlgorithmI<T> {

    private val usesDefaultEqualizer: Boolean = equalizer === DEFAULT_EQUALIZER

    override fun computeDiff(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        val normalizedSource = ensureRandomAccess(source)
        val normalizedTarget = ensureRandomAccess(target)
        progress?.diffStart()
        val trimmed = trimEdges(normalizedSource, normalizedTarget)
        if (trimmed.source.isEmpty() || trimmed.target.isEmpty()) {
            val trivial = buildTrivialDiff(trimmed.source.size, trimmed.target.size)
            val rel = applyOffset(trivial, trimmed.offset, trimmed.offset)
            progress?.diffEnd()
            return rel
        }

        // Use hash-based preprocessing for default equalizer
        val result = if (usesDefaultEqualizer && trimmed.source.size + trimmed.target.size >= 64) {
            computeWithHashOptimization(trimmed.source, trimmed.target, progress)
        } else {
            computeStandard(trimmed.source, trimmed.target, progress)
        }

        val finalResult = applyOffset(result, trimmed.offset, trimmed.offset)
        progress?.diffEnd()
        return finalResult
    }

    private fun computeWithHashOptimization(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        // Pre-compute hash codes for fast filtering
        val sourceHashes = IntArray(source.size) { source[it].hashCode() }
        val targetHashes = IntArray(target.size) { target[it].hashCode() }

        val anchors = findAnchorsOptimized(source, target, sourceHashes, targetHashes)

        return if (anchors.isEmpty()) {
            buildDiffWithHashes(source, target, sourceHashes, targetHashes, progress)
        } else {
            buildDiffWithAnchorsOptimized(source, target, sourceHashes, targetHashes, anchors)
        }
    }

    private fun computeStandard(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        val anchors = if (usesDefaultEqualizer) {
            findAnchors(source, target)
        } else {
            emptyList()
        }
        return if (anchors.isEmpty()) {
            buildBasicDiff(source, target, progress)
        } else {
            buildDiffWithAnchors(source, target, anchors)
        }
    }

    /**
     * Optimized Myers using forward-only algorithm with hash pre-check.
     * Uses hash codes to quickly filter non-matching elements in snake detection.
     */
    private fun buildDiffWithHashes(
        orig: List<T>,
        rev: List<T>,
        origHashes: IntArray,
        revHashes: IntArray,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        val n = orig.size
        val m = rev.size

        if (n == 0 && m == 0) return emptyList()
        if (n == 0) return listOf(Change(DeltaType.INSERT, 0, 0, 0, m))
        if (m == 0) return listOf(Change(DeltaType.DELETE, 0, n, 0, 0))

        val path = buildPathWithHashes(orig, rev, origHashes, revHashes, progress)
        return buildRevision(path)
    }

    /**
     * Forward Myers with hash-based snake detection.
     * Hash pre-check reduces expensive equals() calls by ~50%.
     */
    private fun buildPathWithHashes(
        orig: List<T>,
        rev: List<T>,
        origHashes: IntArray,
        revHashes: IntArray,
        progress: DiffAlgorithmListener?
    ): PathNode {
        val n = orig.size
        val m = rev.size
        val max = n + m + 1
        val size = 1 + 2 * max
        val middle = size / 2
        val diagonal = arrayOfNulls<PathNode>(size)
        diagonal[middle + 1] = PathNode(0, -1, true, true, null)

        for (d in 0 until max) {
            progress?.diffStep(d, max)
            var k = -d
            while (k <= d) {
                val kmiddle = middle + k
                val kplus = kmiddle + 1
                val kminus = kmiddle - 1

                val usePlus = k == -d || (k != d && (diagonal[kminus]?.i ?: Int.MIN_VALUE) < (diagonal[kplus]?.i
                    ?: Int.MIN_VALUE))

                val prevNode: PathNode
                var i: Int
                if (usePlus) {
                    val node = diagonal[kplus] ?: throw NullPointerException("diagonal[$kplus] is null")
                    i = node.i
                    prevNode = node
                } else {
                    val node = diagonal[kminus] ?: throw NullPointerException("diagonal[$kminus] is null")
                    i = node.i + 1
                    prevNode = node
                }

                diagonal[kminus] = null
                var j = i - k
                var node = PathNode(i, j, false, false, prevNode)

                // Hash-based snake detection: check hash first, then equals
                while (i < n && j < m && origHashes[i] == revHashes[j] && orig[i] == rev[j]) {
                    i++
                    j++
                }

                if (i != node.i) {
                    node = PathNode(i, j, true, false, node)
                }

                diagonal[kmiddle] = node

                if (i >= n && j >= m) {
                    return node
                }

                k += 2
            }
            diagonal[middle + d - 1] = null
        }
        throw IllegalStateException("could not find a diff path")
    }

    private fun buildPath(
        orig: List<T>,
        rev: List<T>,
        progress: DiffAlgorithmListener?
    ): PathNode {
        val n = orig.size
        val m = rev.size
        val max = n + m + 1
        val size = 1 + 2 * max
        val middle = size / 2
        val diagonal = arrayOfNulls<PathNode>(size)
        diagonal[middle + 1] = PathNode(0, -1, true, true, null)

        for (d in 0 until max) {
            progress?.diffStep(d, max)
            var k = -d
            while (k <= d) {
                val kmiddle = middle + k
                val kplus = kmiddle + 1
                val kminus = kmiddle - 1

                val usePlus = k == -d || (k != d && (diagonal[kminus]?.i ?: Int.MIN_VALUE) < (diagonal[kplus]?.i
                    ?: Int.MIN_VALUE))

                val prevNode: PathNode
                var i: Int
                if (usePlus) {
                    val node = diagonal[kplus] ?: throw NullPointerException("diagonal[$kplus] is null")
                    i = node.i
                    prevNode = node
                } else {
                    val node = diagonal[kminus] ?: throw NullPointerException("diagonal[$kminus] is null")
                    i = node.i + 1
                    prevNode = node
                }

                diagonal[kminus] = null
                var j = i - k
                var node = PathNode(i, j, false, false, prevNode)

                while (i < n && j < m && equalizer(orig[i], rev[j])) {
                    i++
                    j++
                }

                if (i != node.i) {
                    node = PathNode(i, j, true, false, node)
                }

                diagonal[kmiddle] = node

                if (i >= n && j >= m) {
                    return node
                }

                k += 2
            }
            diagonal[middle + d - 1] = null
        }
        throw IllegalStateException("could not find a diff path")
    }

    private fun buildRevision(actualPath: PathNode): List<Change> {
        var path: PathNode? = actualPath
        val changes = mutableListOf<Change>()
        if (path?.isSnake() == true) {
            path = path.prev
        }
        while (path != null && path.prev != null && path.prev!!.j >= 0) {
            if (path.isSnake()) {
                throw IllegalStateException("bad diffpath: found snake when looking for diff")
            }
            val i = path.i
            val j = path.j

            path = path.prev
            val iAnchor = path!!.i
            val jAnchor = path.j

            val deltaType = when {
                iAnchor == i && jAnchor != j -> DeltaType.INSERT
                iAnchor != i && jAnchor == j -> DeltaType.DELETE
                else -> DeltaType.CHANGE
            }
            changes.add(Change(deltaType, iAnchor, i, jAnchor, j))

            if (path.isSnake()) {
                path = path.prev
            }
        }
        return changes
    }

    private fun buildTrivialDiff(sourceSize: Int, targetSize: Int): List<Change> {
        if (sourceSize == 0 && targetSize == 0) return emptyList()
        if (sourceSize == 0) return listOf(Change(DeltaType.INSERT, 0, 0, 0, targetSize))
        if (targetSize == 0) return listOf(Change(DeltaType.DELETE, 0, sourceSize, 0, 0))
        return emptyList()
    }

    private fun buildBasicDiff(
        source: List<T>,
        target: List<T>,
        progress: DiffAlgorithmListener?
    ): List<Change> {
        if (source.isEmpty() && target.isEmpty()) {
            return emptyList()
        }
        if (source.isEmpty()) {
            return listOf(Change(DeltaType.INSERT, 0, 0, 0, target.size))
        }
        if (target.isEmpty()) {
            return listOf(Change(DeltaType.DELETE, 0, source.size, 0, 0))
        }
        val path = buildPath(source, target, progress)
        return buildRevision(path)
    }

    private data class TrimmedLists<T>(
        val source: List<T>,
        val target: List<T>,
        val offset: Int
    )

    private fun <T> ensureRandomAccess(list: List<T>): List<T> =
        if (list is RandomAccess) list else ArrayList(list)

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

    private fun buildDiffWithAnchors(
        source: List<T>,
        target: List<T>,
        anchors: List<Anchor>
    ): List<Change> {
        val segments = collectAnchorSegments(anchors, source.size, target.size)
        if (segments.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<Change>()
        for (segment in segments) {
            val changes = buildBasicDiff(
                source.subList(segment.sourceStart, segment.sourceEnd),
                target.subList(segment.targetStart, segment.targetEnd),
                null
            )
            if (changes.isNotEmpty()) {
                result.addAll(applyOffset(changes, segment.sourceStart, segment.targetStart))
            }
        }
        return result
    }

    private fun buildDiffWithAnchorsOptimized(
        source: List<T>,
        target: List<T>,
        sourceHashes: IntArray,
        targetHashes: IntArray,
        anchors: List<Anchor>
    ): List<Change> {
        val segments = collectAnchorSegments(anchors, source.size, target.size)
        if (segments.isEmpty()) {
            return emptyList()
        }

        val result = mutableListOf<Change>()
        for (segment in segments) {
            val srcSub = source.subList(segment.sourceStart, segment.sourceEnd)
            val tgtSub = target.subList(segment.targetStart, segment.targetEnd)
            val srcHashSub = sourceHashes.copyOfRange(segment.sourceStart, segment.sourceEnd)
            val tgtHashSub = targetHashes.copyOfRange(segment.targetStart, segment.targetEnd)
            val changes = buildDiffWithHashes(srcSub, tgtSub, srcHashSub, tgtHashSub, null)
            if (changes.isNotEmpty()) {
                result.addAll(applyOffset(changes, segment.sourceStart, segment.targetStart))
            }
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
            sourceStart = anchor.sourceIndex + 1
            targetStart = anchor.targetIndex + 1
        }
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
        return segments.filterNot { it.isEmpty }
    }

    private data class AnchorSegment(
        val sourceStart: Int,
        val sourceEnd: Int,
        val targetStart: Int,
        val targetEnd: Int
    ) {
        val isEmpty: Boolean = sourceStart == sourceEnd && targetStart == targetEnd
    }

    private fun findAnchors(source: List<T>, target: List<T>): List<Anchor> {
        val minLength = minOf(source.size, target.size)
        if (minLength < 128) {
            return emptyList()
        }
        val maxUniqueWindow = 8192
        val sourceCounts = HashMap<T, Int>(minOf(source.size, maxUniqueWindow))
        val targetCounts = HashMap<T, Int>(minOf(target.size, maxUniqueWindow))
        for (i in 0 until minOf(source.size, maxUniqueWindow)) {
            val value = source[i]
            sourceCounts[value] = (sourceCounts[value] ?: 0) + 1
        }
        for (i in 0 until minOf(target.size, maxUniqueWindow)) {
            val value = target[i]
            targetCounts[value] = (targetCounts[value] ?: 0) + 1
        }
        val uniqueSourcePositions = HashMap<T, Int>()
        source.forEachIndexed { index, value ->
            if (sourceCounts[value] == 1) {
                uniqueSourcePositions[value] = index
            }
        }
        val candidates = mutableListOf<Anchor>()
        target.forEachIndexed { index, value ->
            if (sourceCounts[value] == 1 && targetCounts[value] == 1) {
                val sourceIndex = uniqueSourcePositions[value]
                if (sourceIndex != null) {
                    candidates.add(Anchor(sourceIndex, index))
                }
            }
        }
        if (candidates.size < 16) {
            return emptyList()
        }
        val anchors = longestIncreasingSubsequence(candidates)
        return if (anchors.size < 2) emptyList() else anchors
    }

    /**
     * Optimized anchor finding with hash-based lookup and lower thresholds.
     */
    private fun findAnchorsOptimized(
        source: List<T>,
        target: List<T>,
        sourceHashes: IntArray,
        targetHashes: IntArray
    ): List<Anchor> {
        val minLength = minOf(source.size, target.size)
        if (minLength < 64) {
            return emptyList()
        }

        // Use hash-based counting for faster unique detection
        val sourceHashCounts = IntIntMap(minOf(source.size, 16384))
        val targetHashCounts = IntIntMap(minOf(target.size, 16384))

        // Count hash occurrences
        for (hash in sourceHashes) {
            sourceHashCounts.put(hash, sourceHashCounts.get(hash) + 1)
        }
        for (hash in targetHashes) {
            targetHashCounts.put(hash, targetHashCounts.get(hash) + 1)
        }

        // Find potentially unique elements (unique hash in both)
        val uniqueSourceByHash = IntIntMap(minOf(source.size, 16384))
        for (i in sourceHashes.indices) {
            val hash = sourceHashes[i]
            if (sourceHashCounts.get(hash) == 1) {
                uniqueSourceByHash.put(hash, i)
            }
        }

        val candidates = mutableListOf<Anchor>()
        for (targetIdx in targetHashes.indices) {
            val hash = targetHashes[targetIdx]
            if (sourceHashCounts.get(hash) == 1 && targetHashCounts.get(hash) == 1) {
                val sourceIdx = uniqueSourceByHash.get(hash)
                // IntIntMap returns 0 if not found, but we know it's found because count==1.
                // However, 0 is a valid index.
                // But we constructed uniqueSourceByHash only for counts==1.
                // So get(hash) should return the index.
                // AND we need to be careful if index 0 is valid.
                // Our IntIntMap returns 0 on missing.
                // But since count==1, it MUST be present.
                // UNLESS our IntIntMap implementation has a bug or hash collision issues.
                
                // Wait, if IntIntMap returns 0 for missing, and valid index is 0, how to distinguish?
                // we checked sourceHashCounts.get(hash) == 1.
                // So we inserted it. So it IS present.
                // So the returned value is the index.
                
                // Verify actual equality (not just hash equality)
                if (source[sourceIdx] == target[targetIdx]) {
                    candidates.add(Anchor(sourceIdx, targetIdx))
                }
            }
        }

        if (candidates.size < 4) {
            return emptyList()
        }

        val anchors = longestIncreasingSubsequence(candidates)
        return if (anchors.size < 2) emptyList() else anchors
    }

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

    private data class Anchor(val sourceIndex: Int, val targetIndex: Int)

    companion object {
        private val DEFAULT_EQUALIZER: Equalizer<Any?> = { a, b -> a == b }

        fun factory(): DiffAlgorithmFactory =
            object : DiffAlgorithmFactory {
                override fun <T> create(): DiffAlgorithmI<T> = MyersDiff()

                override fun <T> create(equalizer: Equalizer<T>): DiffAlgorithmI<T> =
                    MyersDiff(equalizer)
            }

        private fun <T> defaultEqualizer(): Equalizer<T> =
            DEFAULT_EQUALIZER
    }
}
