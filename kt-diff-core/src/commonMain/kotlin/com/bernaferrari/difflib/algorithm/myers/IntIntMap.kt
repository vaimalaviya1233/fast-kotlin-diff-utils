package com.bernaferrari.difflib.algorithm.myers

/**
 * A simple open-addressing hash map for int -> int mappings.
 * Avoids boxing overhead of HashMap<Int, Int>.
 */
internal class IntIntMap(initialCapacity: Int) {
    private var keys: IntArray
    private var values: IntArray
    private var allocated: BooleanArray
    private var size = 0
    private var capacity: Int
    private var threshold: Int

    init {
        capacity = 1
        while (capacity < initialCapacity) {
            capacity = capacity shl 1
        }
        keys = IntArray(capacity)
        values = IntArray(capacity)
        allocated = BooleanArray(capacity)
        threshold = (capacity * 0.75).toInt()
    }

    fun put(key: Int, value: Int) {
        if (size >= threshold) {
            resize()
        }
        
        val idx = findInsertIndex(key)
        if (!allocated[idx]) {
            allocated[idx] = true
            keys[idx] = key
            size++
        }
        values[idx] = value
    }

    fun get(key: Int): Int {
        val idx = findIndex(key)
        if (idx == -1) {
            return 0 // default value
        }
        return values[idx]
    }

    private fun findIndex(key: Int): Int {
        var idx = key and (capacity - 1)
        // We need to stop if we find an empty slot (not allocated)
        // But since we use linear probing, we scan until we find the key or an empty slot
        while (allocated[idx]) {
            if (keys[idx] == key) {
                return idx
            }
            idx = (idx + 1) and (capacity - 1)
        }
        return -1
    }

    private fun findInsertIndex(key: Int): Int {
        var idx = key and (capacity - 1)
        while (allocated[idx]) {
            if (keys[idx] == key) {
                return idx
            }
            idx = (idx + 1) and (capacity - 1)
        }
        return idx
    }

    private fun resize() {
        val oldKeys = keys
        val oldValues = values
        val oldAllocated = allocated
        val oldCapacity = capacity

        capacity = capacity shl 1
        threshold = (capacity * 0.75).toInt()
        keys = IntArray(capacity)
        values = IntArray(capacity)
        allocated = BooleanArray(capacity)
        size = 0

        for (i in 0 until oldCapacity) {
            if (oldAllocated[i]) {
                put(oldKeys[i], oldValues[i])
            }
        }
    }
}
