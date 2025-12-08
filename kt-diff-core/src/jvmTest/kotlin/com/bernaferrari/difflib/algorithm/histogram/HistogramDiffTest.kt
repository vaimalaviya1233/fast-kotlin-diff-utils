package com.bernaferrari.difflib.algorithm.histogram

import com.bernaferrari.difflib.patch.DeltaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistogramDiffTest {

    @Test
    fun testSimpleDiff() {
        // Create a large input to bypass the optimization threshold (< 256)
        // and force the true Histogram logic (anchor splitting) to run.
        val prefix = (0 until 150).map { "prefix_$it" }
        val suffix = (0 until 150).map { "suffix_$it" }
        
        val source = prefix + listOf("b") + suffix
        val target = prefix + suffix + listOf("d")
        
        // Anchors: all prefix and suffix lines are unique and match.
        // The algorithm should split around them.
        // It should isolate "b" (delete) and "d" (insert).
        
        val diff = HistogramDiff<String>().computeDiff(source, target, null)
        
        // We expect specific behavior from Histogram:
        // It should match all prefix/suffix.
        // It should identify 'b' as extra in source (DELETE)
        // It should identify 'd' as extra in target (INSERT)
        // Myers might also do this, but Histogram guarantees it by splitting.
        
        // Filter out any "equal" parts if they were returned (computeDiff shouldn't return equals)
        val changes = diff.filter { it.deltaType != DeltaType.EQUAL }
        
        // Expect: Delete "b" (index 150), Insert "d" (index 300 in target)
        assertEquals(2, changes.size)
        
        val delete = changes.find { it.deltaType == DeltaType.DELETE }
        val insert = changes.find { it.deltaType == DeltaType.INSERT }
        
        org.junit.jupiter.api.Assertions.assertNotNull(delete)
        org.junit.jupiter.api.Assertions.assertNotNull(insert)
        
        assertEquals(150, delete!!.startOriginal)
        assertEquals("b", source[delete.startOriginal])
        
        assertEquals(300, insert!!.startRevised)
        assertEquals("d", target[insert.startRevised])
    }

    @Test
    fun testSmallInputFallback() {
        // Small input (< 256) triggers the Myers fallback.
        // We just want to ensure it produces a valid diff (apply to source == target).
        val source = listOf("a", "b", "c")
        val target = listOf("a", "c", "d")
        
        val diff = HistogramDiff<String>().computeDiff(source, target, null)
        
        // We don't enforce strict "DELETE/INSERT" shape here because Myers might produce "CHANGE".
        // We only check that the diff is valid.
        val applied = com.bernaferrari.difflib.patch.Patch.generate(source, target, diff).applyTo(source)
        assertEquals(target, applied)
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