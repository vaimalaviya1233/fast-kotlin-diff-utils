package com.bernaferrari.difflib.patch

import com.bernaferrari.difflib.platform.PlatformSerializable

/**
 * Holds the information about the part of text involved in the diff process.
 */
class Chunk<T>(
    val position: Int,
    lines: List<T>,
    changePosition: List<Int>? = null,
    private val copyLines: Boolean = true
) : PlatformSerializable {

    var lines: List<T> = prepareLines(lines)
        set(value) {
            field = prepareLines(value)
        }

    val changePosition: List<Int>? = changePosition?.toList()

    private fun prepareLines(value: List<T>): List<T> =
        when {
            copyLines -> value.toList()
            // Avoid sharing mutable backing storage that could be mutated elsewhere.
            value is MutableList<*> -> value.toList()
            else -> value
        }

    constructor(
        position: Int,
        lines: Array<T>,
        changePosition: List<Int>? = null,
        copyLines: Boolean = true
    ) : this(position, lines.toList(), changePosition, copyLines)

    fun verifyChunk(target: List<T>): VerifyChunk = verifyChunk(target, 0, position)

    fun verifyChunk(target: List<T>, fuzz: Int, position: Int): VerifyChunk {
        val startIndex = fuzz
        val lastIndex = size() - fuzz
        val last = position + size() - 1

        if (position + fuzz > target.size || last - fuzz > target.size) {
            return VerifyChunk.POSITION_OUT_OF_TARGET
        }
        for (i in startIndex until lastIndex) {
            if (target[position + i] != lines[i]) {
                return VerifyChunk.CONTENT_DOES_NOT_MATCH_TARGET
            }
        }
        return VerifyChunk.OK
    }

    fun size(): Int = lines.size

    fun last(): Int = position + size() - 1

    override fun hashCode(): Int = 31 * lines.hashCode() + position

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Chunk<*>) {
            return false
        }
        if (position != other.position) {
            return false
        }
        return lines == other.lines
    }

    override fun toString(): String = "[position: $position, size: ${size()}, lines: $lines]"
}
