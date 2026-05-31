package com.bernaferrari.difflib.patch

/**
 * Describes the add-delta between original and revised texts.
 */
class InsertDelta<T>(sourceChunk: Chunk<T>, targetChunk: Chunk<T>) :
    AbstractDelta<T>(DeltaType.INSERT, sourceChunk, targetChunk) {

    @Throws(PatchFailedException::class)
    override fun applyTo(target: MutableList<T>) {
        target.addAll(source.position, this.target.lines)
    }

    override fun restore(target: MutableList<T>) {
        val position = this.target.position
        target.subList(position, position + this.target.size()).clear()
    }

    override fun toString(): String =
        "[InsertDelta, position: ${source.position}, lines: ${target.lines}]"

    override fun withChunks(original: Chunk<T>, revised: Chunk<T>): AbstractDelta<T> = InsertDelta(original, revised)
}
