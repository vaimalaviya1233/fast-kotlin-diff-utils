package com.bernaferrari.difflib.algorithm.histogram

import com.bernaferrari.difflib.patch.DeltaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistogramDiffTest {

    @Test
    fun testSimpleDiff() {
        val source = listOf("a", "b", "c")
        val target = listOf("a", "c", "d")
        // Unique: a, b (only in source), c (in both), d (only in target).
        // Common unique: a, c.
        // LIS of a, c: a, c.
        // Anchors: a, c.
        // Split:
        // 1. Before 'a': empty vs empty -> empty.
        // 2. Between 'a' and 'c': 'b' vs empty -> delete 'b'.
        // 3. After 'c': empty vs 'd' -> insert 'd'.
        // Expected: Keep a, Delete b, Keep c, Insert d.

        val diff = HistogramDiff<String>().computeDiff(source, target, null)
        
        // Convert to simplified string representation for checking
        val result = diff.joinToString(", ") { "${it.deltaType} [${it.startOriginal}, ${it.endOriginal}) -> [${it.startRevised}, ${it.endRevised})" }
        println(result)
        
        // Expected changes:
        // DELETE 1, 2 (b) -> 1, 1 (at position 1 in revised)
        // INSERT 2, 2 (at position 2 in original) -> 2, 3 (d)
        
        assertEquals(2, diff.size)
        assertEquals(DeltaType.DELETE, diff[0].deltaType)
        assertEquals("b", source[diff[0].startOriginal])
        
        assertEquals(DeltaType.INSERT, diff[1].deltaType)
        assertEquals("d", target[diff[1].startRevised])
    }

    @Test
    fun testPatienceScenario() {
        // A scenario where Histogram (Patience) might behave nicely.
        // Source: A B C A
        // Target: A C B A
        // Unique common: C, B? No, A appears twice. B appears once. C appears once.
        // Unique common: B, C.
        // Indices in Source: B=1, C=2.
        // Indices in Target: C=1, B=2.
        // Candidates (TargetIdx -> SourceIdx): 1->2 (C), 2->1 (B).
        // LIS of Source indices (2, 1):
        // Either {2} (C) or {1} (B). Length 1.
        // Let's say it picks B (index 1).
        // Anchor: B.
        // Split at B.
        // Before B (Source "A", Target "A C"):
        //   Recurse: A vs A C.
        //   Common A matches.
        //   Insert C.
        // After B (Source "C A", Target "A"):
        //   Recurse: C A vs A.
        //   Common A matches.
        //   Delete C.
        // Result: Keep A, Insert C, Keep B, Delete C, Keep A.
        
        // If it picked C (index 2) as anchor:
        // Before C (Source "A B", Target "A"):
        //   Delete B.
        // After C (Source "A", Target "B A"):
        //   Insert B.
        // Result: Keep A, Delete B, Keep C, Insert B, Keep A.
        
        // Both are valid.
        
        val source = listOf("A", "B", "C", "A")
        val target = listOf("A", "C", "B", "A")
        val diff = HistogramDiff<String>().computeDiff(source, target, null)
        
        // It should find a diff.
        assertEquals(true, diff.isNotEmpty())
    }
}