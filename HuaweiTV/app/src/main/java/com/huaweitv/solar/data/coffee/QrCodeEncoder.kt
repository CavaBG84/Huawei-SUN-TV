package com.huaweitv.solar.data.coffee

import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * Pure Kotlin ISO/IEC 18004 QR Code Version 3-M (29x29 modules) encoder in Byte mode.
 * Zero external dependencies; encodes up to 42 UTF-8 bytes cleanly.
 */
object QrCodeEncoder {

    private const val SIZE = 29
    private const val DATA_CODEWORDS = 44
    private const val EC_CODEWORDS = 26
    private const val ALIGNMENT_CENTER = 22

    // Format information bits for Error Correction Level M (00) + Masks 0..7 (BCH + 0x5412 mask)
    private val FORMAT_INFO_M = intArrayOf(
        0x5412, // Mask 0
        0x5125, // Mask 1
        0x5E7C, // Mask 2
        0x5B4B, // Mask 3
        0x45F9, // Mask 4
        0x40CE, // Mask 5
        0x4F97, // Mask 6
        0x4AA0  // Mask 7
    )

    private val GF_EXP = IntArray(256)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        GF_EXP[255] = GF_EXP[0]
    }

    /**
     * Encodes [text] into a 29x29 QR boolean matrix (`true` = dark module, `false` = light module).
     */
    fun encode(text: String): Array<BooleanArray> {
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        require(payload.size <= DATA_CODEWORDS - 2) {
            "Payload too long for QR Version 3-M (${payload.size} bytes, max ${DATA_CODEWORDS - 2})"
        }

        val dataCodewords = buildDataCodewords(payload)
        val ecCodewords = computeReedSolomon(dataCodewords, EC_CODEWORDS)

        val allBits = BooleanArray(DATA_CODEWORDS * 8 + EC_CODEWORDS * 8 + 7)
        var bitIdx = 0
        for (cw in dataCodewords) {
            for (b in 7 downTo 0) {
                allBits[bitIdx++] = ((cw ushr b) and 1) != 0
            }
        }
        for (cw in ecCodewords) {
            for (b in 7 downTo 0) {
                allBits[bitIdx++] = ((cw ushr b) and 1) != 0
            }
        }
        // Remaining 7 bits stay false (0)

        val isFunction = Array(SIZE) { BooleanArray(SIZE) }
        val baseModules = Array(SIZE) { BooleanArray(SIZE) }
        drawFunctionPatterns(baseModules, isFunction)
        placeDataBits(baseModules, isFunction, allBits)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = baseModules

        for (mask in 0 until 8) {
            val candidate = Array(SIZE) { r -> baseModules[r].clone() }
            applyMask(candidate, isFunction, mask)
            drawFormatBits(candidate, FORMAT_INFO_M[mask])
            val penalty = calculatePenalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }
        // Ensure format bits on bestGrid match bestMask (already applied inside loop)
        @Suppress("UNUSED_VARIABLE")
        val selectedMask = bestMask
        return bestGrid
    }

    private fun buildDataCodewords(payload: ByteArray): IntArray {
        val bits = ArrayList<Int>(DATA_CODEWORDS * 8)
        // Byte mode = 0100
        appendBits(bits, 0b0100, 4)
        // Char count (8 bits for Version 3)
        appendBits(bits, payload.size, 8)
        // Data bytes
        for (b in payload) {
            appendBits(bits, b.toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeros)
        val maxBits = DATA_CODEWORDS * 8
        val terminatorBits = minOf(4, maxBits - bits.size)
        repeat(terminatorBits) { bits.add(0) }
        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        val codewords = IntArray(DATA_CODEWORDS)
        val byteCount = bits.size / 8
        for (i in 0 until byteCount) {
            var value = 0
            for (bit in 0 until 8) {
                value = (value shl 1) or bits[i * 8 + bit]
            }
            codewords[i] = value
        }
        var padIndex = 0
        for (i in byteCount until DATA_CODEWORDS) {
            codewords[i] = if (padIndex % 2 == 0) 0xEC else 0x11
            padIndex++
        }
        return codewords
    }

    private fun appendBits(dest: MutableList<Int>, value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            dest.add((value ushr i) and 1)
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[(GF_LOG[a] + GF_LOG[b]) % 255]
    }

    private fun computeReedSolomon(data: IntArray, ecCount: Int): IntArray {
        // Build generator polynomial of degree ecCount: prod_{i=0..ecCount-1} (x - alpha^i)
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val next = IntArray(gen.size + 1)
            val factor = GF_EXP[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], factor)
            }
            gen = next
        }

        val rem = IntArray(ecCount)
        for (b in data) {
            val lead = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecCount - 1)
            rem[ecCount - 1] = 0
            if (lead != 0) {
                for (j in 0 until ecCount) {
                    rem[j] = rem[j] xor gfMul(gen[j + 1], lead)
                }
            }
        }
        return rem
    }

    private fun drawFunctionPatterns(modules: Array<BooleanArray>, isFunction: Array<BooleanArray>) {
        // Finder patterns + 1-module separators
        drawFinderPattern(modules, isFunction, 0, 0)
        drawFinderPattern(modules, isFunction, 0, SIZE - 7)
        drawFinderPattern(modules, isFunction, SIZE - 7, 0)

        // Separators
        for (i in 0 until 8) {
            markFunction(modules, isFunction, 7, i, false)
            markFunction(modules, isFunction, i, 7, false)

            markFunction(modules, isFunction, 7, SIZE - 1 - i, false)
            markFunction(modules, isFunction, i, SIZE - 8, false)

            markFunction(modules, isFunction, SIZE - 8, i, false)
            markFunction(modules, isFunction, SIZE - 1 - i, 7, false)
        }

        // Alignment pattern centered at (22, 22)
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(abs(dy), abs(dx))
                markFunction(modules, isFunction, ALIGNMENT_CENTER + dy, ALIGNMENT_CENTER + dx, dist != 1)
            }
        }

        // Timing patterns
        for (i in 8 until SIZE - 8) {
            val dark = (i % 2 == 0)
            markFunction(modules, isFunction, 6, i, dark)
            markFunction(modules, isFunction, i, 6, dark)
        }

        // Dark module (4*V + 9, 8) = (21, 8)
        markFunction(modules, isFunction, SIZE - 8, 8, true)

        // Reserve format info cells (values filled later per mask)
        for (i in 0 until 9) {
            if (!isFunction[8][i]) isFunction[8][i] = true
            if (!isFunction[i][8]) isFunction[i][8] = true
        }
        for (i in SIZE - 8 until SIZE) {
            isFunction[8][i] = true
        }
        for (i in SIZE - 7 until SIZE) {
            isFunction[i][8] = true
        }
    }

    private fun drawFinderPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        topRow: Int,
        leftCol: Int
    ) {
        for (dy in 0 until 7) {
            for (dx in 0 until 7) {
                val dist = maxOf(abs(dy - 3), abs(dx - 3))
                markFunction(modules, isFunction, topRow + dy, leftCol + dx, dist != 2)
            }
        }
    }

    private fun markFunction(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        row: Int,
        col: Int,
        dark: Boolean
    ) {
        modules[row][col] = dark
        isFunction[row][col] = true
    }

    private fun placeDataBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        bits: BooleanArray
    ) {
        var bitIdx = 0
        var upward = true
        var right = SIZE - 1
        while (right >= 1) {
            if (right == 6) {
                right--
            }
            for (step in 0 until SIZE) {
                val row = if (upward) SIZE - 1 - step else step
                for (dx in 0..1) {
                    val col = right - dx
                    if (!isFunction[row][col] && bitIdx < bits.size) {
                        modules[row][col] = bits[bitIdx++]
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(modules: Array<BooleanArray>, isFunction: Array<BooleanArray>, mask: Int) {
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (isFunction[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> (r / 2 + c / 3) % 2 == 0
                    5 -> (r * c) % 2 + (r * c) % 3 == 0
                    6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                    7 -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                    else -> false
                }
                if (invert) {
                    modules[r][c] = !modules[r][c]
                }
            }
        }
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, formatWord: Int) {
        for (i in 0 until 15) {
            val bit = ((formatWord ushr (14 - i)) and 1) != 0
            // Copy 1: top-left around finder
            when {
                i < 6 -> modules[8][i] = bit
                i == 6 -> modules[8][7] = bit
                i == 7 -> modules[8][8] = bit
                i == 8 -> modules[7][8] = bit
                else -> modules[14 - i][8] = bit
            }
            // Copy 2: bottom-left & top-right
            if (i < 7) {
                modules[SIZE - 1 - i][8] = bit
            } else {
                modules[8][SIZE - 15 + i] = bit
            }
        }
    }

    private fun calculatePenalty(modules: Array<BooleanArray>): Int {
        var penalty = 0
        // N1: adjacent same-color runs >= 5
        for (r in 0 until SIZE) {
            var runColor = modules[r][0]
            var runLen = 1
            for (c in 1 until SIZE) {
                if (modules[r][c] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (c in 0 until SIZE) {
            var runColor = modules[0][c]
            var runLen = 1
            for (r in 1 until SIZE) {
                if (modules[r][c] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }

        // N2: 2x2 blocks of same color
        for (r in 0 until SIZE - 1) {
            for (c in 0 until SIZE - 1) {
                val color = modules[r][c]
                if (modules[r][c + 1] == color && modules[r + 1][c] == color && modules[r + 1][c + 1] == color) {
                    penalty += 3
                }
            }
        }

        // N3: finder-like 1:1:3:1:1 patterns
        for (r in 0 until SIZE) {
            for (c in 0..SIZE - 11) {
                if (matchesN3Row(modules, r, c, true) || matchesN3Row(modules, r, c, false)) {
                    penalty += 40
                }
            }
        }
        for (c in 0 until SIZE) {
            for (r in 0..SIZE - 11) {
                if (matchesN3Col(modules, r, c, true) || matchesN3Col(modules, r, c, false)) {
                    penalty += 40
                }
            }
        }

        // N4: dark module ratio deviation from 50%
        var darkCount = 0
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (modules[r][c]) darkCount++
            }
        }
        val total = SIZE * SIZE
        val k = abs(darkCount * 20 - total * 10) / total
        penalty += k * 10
        return penalty
    }

    private fun matchesN3Row(m: Array<BooleanArray>, r: Int, c: Int, leadingLight: Boolean): Boolean {
        return if (leadingLight) {
            !m[r][c] && !m[r][c + 1] && !m[r][c + 2] && !m[r][c + 3] &&
                m[r][c + 4] && !m[r][c + 5] && m[r][c + 6] && m[r][c + 7] && m[r][c + 8] && !m[r][c + 9] && m[r][c + 10]
        } else {
            m[r][c] && !m[r][c + 1] && m[r][c + 2] && m[r][c + 3] && m[r][c + 4] && !m[r][c + 5] && m[r][c + 6] &&
                !m[r][c + 7] && !m[r][c + 8] && !m[r][c + 9] && !m[r][c + 10]
        }
    }

    private fun matchesN3Col(m: Array<BooleanArray>, r: Int, c: Int, leadingLight: Boolean): Boolean {
        return if (leadingLight) {
            !m[r][c] && !m[r + 1][c] && !m[r + 2][c] && !m[r + 3][c] &&
                m[r + 4][c] && !m[r + 5][c] && m[r + 6][c] && m[r + 7][c] && m[r + 8][c] && !m[r + 9][c] && m[r + 10][c]
        } else {
            m[r][c] && !m[r + 1][c] && m[r + 2][c] && m[r + 3][c] && m[r + 4][c] && !m[r + 5][c] && m[r + 6][c] &&
                !m[r + 7][c] && !m[r + 8][c] && !m[r + 9][c] && !m[r + 10][c]
        }
    }
}
