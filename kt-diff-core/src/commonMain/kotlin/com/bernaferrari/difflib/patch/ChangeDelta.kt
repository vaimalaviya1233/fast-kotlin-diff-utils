package com.bernaferrari.difflib.patch

/**
 * Describes the change-delta between original and revised texts.
 */
class ChangeDelta<T>(source: Chunk<T>, target: Chunk<T>) : AbstractDelta<T>(DeltaType.CHANGE, source, target) {

    @Throws(PatchFailedException::class)
    override fun applyTo(target: MutableList<T>) {
        val position = source.position
        target.subList(position, position + source.size()).clear()
        target.addAll(position, this.target.lines)
    }

    override fun restore(target: MutableList<T>) {
        val position = this.target.position
        target.subList(position, position + this.target.size()).clear()
        target.addAll(position, source.lines)
    }

    @Throws(PatchFailedException::class)
    override fun applyFuzzyToAt(target: MutableList<T>, fuzz: Int, position: Int) {
        val size = source.size()
        target.subList(position + fuzz, position + size - fuzz).clear()
        target.addAll(position + fuzz, this.target.lines.subList(fuzz, this.target.size() - fuzz))
    }

    override fun toString(): String =
        "[ChangeDelta, position: ${source.position}, lines: ${source.lines} to ${target.lines}]"

    override fun withChunks(original: Chunk<T>, revised: Chunk<T>): AbstractDelta<T> = ChangeDelta(original, revised)
}
