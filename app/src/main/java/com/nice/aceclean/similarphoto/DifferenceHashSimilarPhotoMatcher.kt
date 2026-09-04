package com.nice.aceclean.similarphoto

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt

/** Perceptual matching logic migrated from CleanBoxPro's similar-photo feature. */
class DifferenceHashSimilarPhotoMatcher(
    private val resolver: ContentResolver,
    private val hashThreshold: Int = 32,
) {
    fun findSimilarGroups(items: List<SimilarPhotoItem>): List<List<SimilarPhotoItem>> {
        val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val fingerprints = runBlocking {
            buildList {
                items.chunked(parallelism * 4).forEach { batch ->
                    addAll(batch.map { item -> async(Dispatchers.IO) { fingerprint(item) } }.awaitAll().filterNotNull())
                }
            }
        }
        if (fingerprints.size < 2) return emptyList()

        val parents = IntArray(fingerprints.size) { it }
        fun find(value: Int): Int {
            var current = value
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }
        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) parents[rightRoot] = leftRoot
        }

        val tree = HammingTree()
        fingerprints.forEachIndexed { index, fingerprint ->
            tree.search(fingerprint.hash, hashThreshold + RECALL_MARGIN).forEach { otherIndex ->
                val other = fingerprints[otherIndex]
                val distance = hammingDistance(fingerprint.hash, other.hash)
                val matches = if (distance <= hashThreshold) {
                    verifyCloseMatch(fingerprint, other)
                } else {
                    verifyRecallMatch(fingerprint, other)
                }
                if (matches) union(index, otherIndex)
            }
            tree.add(fingerprint.hash, index)
        }

        return fingerprints.indices
            .groupBy(::find)
            .values
            .filter { it.size >= 2 }
            .map { indexes ->
                val representative = fingerprints[indexes.first()]
                indexes.map { fingerprints[it] }
                    .sortedWith(
                        compareByDescending<Fingerprint> { it.item.sizeBytes }
                            .thenBy { hammingDistance(it.hash, representative.hash) }
                            .thenByDescending { it.item.dateTaken },
                    )
                    .map { it.item }
            }
    }

    private fun verifyCloseMatch(first: Fingerprint, second: Fingerprint): Boolean {
        if (!first.lowDetail && !second.lowDetail) return true
        if (first.std < BLANK_STD || second.std < BLANK_STD) return false
        return first.gray.indices.sumOf { kotlin.math.abs(first.gray[it] - second.gray[it]).toLong() } /
            first.gray.size <= MAX_MEAN_DIFFERENCE
    }

    private fun verifyRecallMatch(first: Fingerprint, second: Fingerprint): Boolean {
        if (first.lowDetail || second.lowDetail || first.std < MIN_RECALL_STD || second.std < MIN_RECALL_STD) return false
        return correlation(first.gray, second.gray) >= MIN_CORRELATION
    }

    private fun correlation(first: IntArray, second: IntArray): Double {
        val firstMean = first.average()
        val secondMean = second.average()
        var covariance = 0.0
        var firstVariance = 0.0
        var secondVariance = 0.0
        first.indices.forEach { index ->
            val a = first[index] - firstMean
            val b = second[index] - secondMean
            covariance += a * b
            firstVariance += a * a
            secondVariance += b * b
        }
        val divisor = sqrt(firstVariance * secondVariance)
        return if (divisor == 0.0) 0.0 else covariance / divisor
    }

    private fun fingerprint(item: SimilarPhotoItem): Fingerprint? {
        val bitmap = decode(item) ?: return null
        return try {
            val hashBitmap = Bitmap.createScaledBitmap(bitmap, HASH_SIZE + 1, HASH_SIZE, true)
            val grayBitmap = Bitmap.createScaledBitmap(bitmap, GRAY_SIZE, GRAY_SIZE, true)
            try {
                val hash = extractHash(hashBitmap)
                val gray = extractGray(grayBitmap)
                Fingerprint(item, hash, gray.values, gray.lowDetail, gray.std)
            } finally {
                hashBitmap.recycle()
                grayBitmap.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(item: SimilarPhotoItem): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = sample
        }
        return resolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun extractHash(bitmap: Bitmap): LongArray {
        val width = HASH_SIZE + 1
        val pixels = IntArray(width * HASH_SIZE)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, HASH_SIZE)
        val result = LongArray(4)
        var bit = 0
        repeat(HASH_SIZE) { y ->
            repeat(HASH_SIZE) { x ->
                if (luminance(pixels[y * width + x]) > luminance(pixels[y * width + x + 1])) {
                    result[bit ushr 6] = result[bit ushr 6] or (1L shl (bit and 63))
                }
                bit++
            }
        }
        return result
    }

    private fun extractGray(bitmap: Bitmap): GrayValues {
        val pixels = IntArray(GRAY_SIZE * GRAY_SIZE)
        bitmap.getPixels(pixels, 0, GRAY_SIZE, 0, 0, GRAY_SIZE, GRAY_SIZE)
        val luminance = IntArray(pixels.size) { luminance(pixels[it]) }
        val mean = luminance.average()
        val std = sqrt(luminance.sumOf { (it - mean) * (it - mean) } / luminance.size)
        val min = luminance.minOrNull() ?: 0
        val range = ((luminance.maxOrNull() ?: 0) - min).coerceAtLeast(1)
        return GrayValues(
            IntArray(luminance.size) { (luminance[it] - min) * 255 / range },
            mean >= WHITE_MEAN && std <= FLAT_STD,
            std,
        )
    }

    private fun luminance(color: Int): Int {
        val red = color shr 16 and 0xff
        val green = color shr 8 and 0xff
        val blue = color and 0xff
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private data class GrayValues(val values: IntArray, val lowDetail: Boolean, val std: Double)
    private data class Fingerprint(
        val item: SimilarPhotoItem,
        val hash: LongArray,
        val gray: IntArray,
        val lowDetail: Boolean,
        val std: Double,
    )

    private class HammingTree {
        private data class Node(val hash: LongArray, val index: Int, val children: MutableMap<Int, Node> = linkedMapOf())
        private var root: Node? = null

        fun add(hash: LongArray, index: Int) {
            val newNode = Node(hash, index)
            var current = root ?: run { root = newNode; return }
            while (true) {
                val distance = hammingDistance(hash, current.hash)
                current = current.children[distance] ?: run { current.children[distance] = newNode; return }
            }
        }

        fun search(hash: LongArray, threshold: Int): List<Int> = buildList { search(root, hash, threshold, this) }

        private fun search(node: Node?, hash: LongArray, threshold: Int, output: MutableList<Int>) {
            node ?: return
            val distance = hammingDistance(hash, node.hash)
            if (distance <= threshold) output += node.index
            node.children.forEach { (edge, child) ->
                if (edge in (distance - threshold)..(distance + threshold)) search(child, hash, threshold, output)
            }
        }
    }

    private companion object {
        const val HASH_SIZE = 16
        const val GRAY_SIZE = 32
        const val RECALL_MARGIN = 60
        const val WHITE_MEAN = 170.0
        const val FLAT_STD = 42.0
        const val BLANK_STD = 10.0
        const val MIN_RECALL_STD = 12.0
        const val MAX_MEAN_DIFFERENCE = 12L
        const val MIN_CORRELATION = 0.8

        fun hammingDistance(first: LongArray, second: LongArray): Int =
            first.indices.sumOf { java.lang.Long.bitCount(first[it] xor second[it]) }
    }
}
