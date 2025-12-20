package com.bernaferrari.difflib.patch

fun interface ConflictOutput<T> {
    @Throws(PatchFailedException::class)
    fun processConflict(verifyChunk: VerifyChunk, delta: AbstractDelta<T>, result: MutableList<T>)
}
