package com.huaweitv.solar.coffee;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java ISO/IEC 18004 QR Code Version 3-M (29x29 modules) encoder in Byte mode.
 * Zero external dependencies; 100% compatible with Android 4.4.4 KitKat (API 19).
 */
public final class QrCodeEncoder {

    private static final int SIZE = 29;
    private static final int DATA_CODEWORDS = 44;
    private static final int EC_CODEWORDS = 26;
    private static final int ALIGNMENT_CENTER = 22;

    private static final int[] FORMAT_INFO_M = new int[]{
        0x5412, // Mask 0
        0x5125, // Mask 1
        0x5E7C, // Mask 2
        0x5B4B, // Mask 3
        0x45F9, // Mask 4
        0x40CE, // Mask 5
        0x4F97, // Mask 6
        0x4AA0  // Mask 7
    };

    private static final int[] GF_EXP = new int[256];
    private static final int[] GF_LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = x;
            GF_LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        GF_EXP[255] = GF_EXP[0];
    }

    private QrCodeEncoder() {}

    /**
     * Encodes text into a 29x29 QR boolean matrix (true = dark module, false = light module).
     */
    public static boolean[][] encode(String text) {
        byte[] payload = text.getBytes(Charset.forName("UTF-8"));
        if (payload.length > DATA_CODEWORDS - 2) {
            throw new IllegalArgumentException(
                "Payload too long for QR Version 3-M (" + payload.length + " bytes, max " + (DATA_CODEWORDS - 2) + ")"
            );
        }

        int[] dataCodewords = buildDataCodewords(payload);
        int[] ecCodewords = computeReedSolomon(dataCodewords, EC_CODEWORDS);

        boolean[] allBits = new boolean[DATA_CODEWORDS * 8 + EC_CODEWORDS * 8 + 7];
        int bitIdx = 0;
        for (int cw : dataCodewords) {
            for (int b = 7; b >= 0; b--) {
                allBits[bitIdx++] = ((cw >>> b) & 1) != 0;
            }
        }
        for (int cw : ecCodewords) {
            for (int b = 7; b >= 0; b--) {
                allBits[bitIdx++] = ((cw >>> b) & 1) != 0;
            }
        }

        boolean[][] isFunction = new boolean[SIZE][SIZE];
        boolean[][] baseModules = new boolean[SIZE][SIZE];
        drawFunctionPatterns(baseModules, isFunction);
        placeDataBits(baseModules, isFunction, allBits);

        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] bestGrid = baseModules;

        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = new boolean[SIZE][SIZE];
            for (int r = 0; r < SIZE; r++) {
                System.arraycopy(baseModules[r], 0, candidate[r], 0, SIZE);
            }
            applyMask(candidate, isFunction, mask);
            drawFormatBits(candidate, FORMAT_INFO_M[mask]);
            int penalty = calculatePenalty(candidate);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestGrid = candidate;
            }
        }
        return bestGrid;
    }

    private static int[] buildDataCodewords(byte[] payload) {
        List<Integer> bits = new ArrayList<>(DATA_CODEWORDS * 8);
        appendBits(bits, 0b0100, 4);
        appendBits(bits, payload.length, 8);
        for (byte b : payload) {
            appendBits(bits, b & 0xFF, 8);
        }
        int maxBits = DATA_CODEWORDS * 8;
        int terminatorBits = Math.min(4, maxBits - bits.size());
        for (int i = 0; i < terminatorBits; i++) {
            bits.add(0);
        }
        while (bits.size() % 8 != 0) {
            bits.add(0);
        }

        int[] codewords = new int[DATA_CODEWORDS];
        int byteCount = bits.size() / 8;
        for (int i = 0; i < byteCount; i++) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) {
                value = (value << 1) | bits.get(i * 8 + bit);
            }
            codewords[i] = value;
        }
        int padIndex = 0;
        for (int i = byteCount; i < DATA_CODEWORDS; i++) {
            codewords[i] = (padIndex % 2 == 0) ? 0xEC : 0x11;
            padIndex++;
        }
        return codewords;
    }

    private static void appendBits(List<Integer> dest, int value, int bitCount) {
        for (int i = bitCount - 1; i >= 0; i--) {
            dest.add((value >>> i) & 1);
        }
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[(GF_LOG[a] + GF_LOG[b]) % 255];
    }

    private static int[] computeReedSolomon(int[] data, int ecCount) {
        int[] gen = new int[]{1};
        for (int i = 0; i < ecCount; i++) {
            int[] next = new int[gen.length + 1];
            int factor = GF_EXP[i];
            for (int j = 0; j < gen.length; j++) {
                next[j] ^= gen[j];
                next[j + 1] ^= gfMul(gen[j], factor);
            }
            gen = next;
        }

        int[] rem = new int[ecCount];
        for (int b : data) {
            int lead = b ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, ecCount - 1);
            rem[ecCount - 1] = 0;
            if (lead != 0) {
                for (int j = 0; j < ecCount; j++) {
                    rem[j] ^= gfMul(gen[j + 1], lead);
                }
            }
        }
        return rem;
    }

    private static void drawFunctionPatterns(boolean[][] modules, boolean[][] isFunction) {
        drawFinderPattern(modules, isFunction, 0, 0);
        drawFinderPattern(modules, isFunction, 0, SIZE - 7);
        drawFinderPattern(modules, isFunction, SIZE - 7, 0);

        for (int i = 0; i < 8; i++) {
            markFunction(modules, isFunction, 7, i, false);
            markFunction(modules, isFunction, i, 7, false);

            markFunction(modules, isFunction, 7, SIZE - 1 - i, false);
            markFunction(modules, isFunction, i, SIZE - 8, false);

            markFunction(modules, isFunction, SIZE - 8, i, false);
            markFunction(modules, isFunction, SIZE - 1 - i, 7, false);
        }

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int dist = Math.max(Math.abs(dy), Math.abs(dx));
                markFunction(modules, isFunction, ALIGNMENT_CENTER + dy, ALIGNMENT_CENTER + dx, dist != 1);
            }
        }

        for (int i = 8; i < SIZE - 8; i++) {
            boolean dark = (i % 2 == 0);
            markFunction(modules, isFunction, 6, i, dark);
            markFunction(modules, isFunction, i, 6, dark);
        }

        markFunction(modules, isFunction, SIZE - 8, 8, true);

        for (int i = 0; i < 9; i++) {
            if (!isFunction[8][i]) isFunction[8][i] = true;
            if (!isFunction[i][8]) isFunction[i][8] = true;
        }
        for (int i = SIZE - 8; i < SIZE; i++) {
            isFunction[8][i] = true;
        }
        for (int i = SIZE - 7; i < SIZE; i++) {
            isFunction[i][8] = true;
        }
    }

    private static void drawFinderPattern(boolean[][] modules, boolean[][] isFunction, int topRow, int leftCol) {
        for (int dy = 0; dy < 7; dy++) {
            for (int dx = 0; dx < 7; dx++) {
                int dist = Math.max(Math.abs(dy - 3), Math.abs(dx - 3));
                markFunction(modules, isFunction, topRow + dy, leftCol + dx, dist != 2);
            }
        }
    }

    private static void markFunction(boolean[][] modules, boolean[][] isFunction, int row, int col, boolean dark) {
        modules[row][col] = dark;
        isFunction[row][col] = true;
    }

    private static void placeDataBits(boolean[][] modules, boolean[][] isFunction, boolean[] bits) {
        int bitIdx = 0;
        boolean upward = true;
        int right = SIZE - 1;
        while (right >= 1) {
            if (right == 6) {
                right--;
            }
            for (int step = 0; step < SIZE; step++) {
                int row = upward ? (SIZE - 1 - step) : step;
                for (int dx = 0; dx <= 1; dx++) {
                    int col = right - dx;
                    if (!isFunction[row][col] && bitIdx < bits.length) {
                        modules[row][col] = bits[bitIdx++];
                    }
                }
            }
            upward = !upward;
            right -= 2;
        }
    }

    private static void applyMask(boolean[][] modules, boolean[][] isFunction, int mask) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (isFunction[r][c]) continue;
                boolean invert;
                switch (mask) {
                    case 0: invert = (r + c) % 2 == 0; break;
                    case 1: invert = r % 2 == 0; break;
                    case 2: invert = c % 3 == 0; break;
                    case 3: invert = (r + c) % 3 == 0; break;
                    case 4: invert = (r / 2 + c / 3) % 2 == 0; break;
                    case 5: invert = (r * c) % 2 + (r * c) % 3 == 0; break;
                    case 6: invert = ((r * c) % 2 + (r * c) % 3) % 2 == 0; break;
                    case 7: invert = ((r + c) % 2 + (r * c) % 3) % 2 == 0; break;
                    default: invert = false; break;
                }
                if (invert) {
                    modules[r][c] = !modules[r][c];
                }
            }
        }
    }

    private static void drawFormatBits(boolean[][] modules, int formatWord) {
        for (int i = 0; i < 15; i++) {
            boolean bit = ((formatWord >>> (14 - i)) & 1) != 0;
            if (i < 6) {
                modules[8][i] = bit;
            } else if (i == 6) {
                modules[8][7] = bit;
            } else if (i == 7) {
                modules[8][8] = bit;
            } else if (i == 8) {
                modules[7][8] = bit;
            } else {
                modules[14 - i][8] = bit;
            }

            if (i < 7) {
                modules[SIZE - 1 - i][8] = bit;
            } else {
                modules[8][SIZE - 15 + i] = bit;
            }
        }
    }

    private static int calculatePenalty(boolean[][] modules) {
        int penalty = 0;
        for (int r = 0; r < SIZE; r++) {
            boolean runColor = modules[r][0];
            int runLen = 1;
            for (int c = 1; c < SIZE; c++) {
                if (modules[r][c] == runColor) {
                    runLen++;
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5);
                    runColor = modules[r][c];
                    runLen = 1;
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5);
        }
        for (int c = 0; c < SIZE; c++) {
            boolean runColor = modules[0][c];
            int runLen = 1;
            for (int r = 1; r < SIZE; r++) {
                if (modules[r][c] == runColor) {
                    runLen++;
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5);
                    runColor = modules[r][c];
                    runLen = 1;
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5);
        }

        for (int r = 0; r < SIZE - 1; r++) {
            for (int c = 0; c < SIZE - 1; c++) {
                boolean color = modules[r][c];
                if (modules[r][c + 1] == color && modules[r + 1][c] == color && modules[r + 1][c + 1] == color) {
                    penalty += 3;
                }
            }
        }

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c <= SIZE - 11; c++) {
                if (matchesN3Row(modules, r, c, true) || matchesN3Row(modules, r, c, false)) {
                    penalty += 40;
                }
            }
        }
        for (int c = 0; c < SIZE; c++) {
            for (int r = 0; r <= SIZE - 11; r++) {
                if (matchesN3Col(modules, r, c, true) || matchesN3Col(modules, r, c, false)) {
                    penalty += 40;
                }
            }
        }

        int darkCount = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (modules[r][c]) darkCount++;
            }
        }
        int total = SIZE * SIZE;
        int k = Math.abs(darkCount * 20 - total * 10) / total;
        penalty += k * 10;
        return penalty;
    }

    private static boolean matchesN3Row(boolean[][] m, int r, int c, boolean leadingLight) {
        if (leadingLight) {
            return !m[r][c] && !m[r][c + 1] && !m[r][c + 2] && !m[r][c + 3]
                && m[r][c + 4] && !m[r][c + 5] && m[r][c + 6] && m[r][c + 7] && m[r][c + 8] && !m[r][c + 9] && m[r][c + 10];
        }
        return m[r][c] && !m[r][c + 1] && m[r][c + 2] && m[r][c + 3] && m[r][c + 4] && !m[r][c + 5] && m[r][c + 6]
            && !m[r][c + 7] && !m[r][c + 8] && !m[r][c + 9] && !m[r][c + 10];
    }

    private static boolean matchesN3Col(boolean[][] m, int r, int c, boolean leadingLight) {
        if (leadingLight) {
            return !m[r][c] && !m[r + 1][c] && !m[r + 2][c] && !m[r + 3][c]
                && m[r + 4][c] && !m[r + 5][c] && m[r + 6][c] && m[r + 7][c] && m[r + 8][c] && !m[r + 9][c] && m[r + 10][c];
        }
        return m[r][c] && !m[r + 1][c] && m[r + 2][c] && m[r + 3][c] && m[r + 4][c] && !m[r + 5][c] && m[r + 6][c]
            && !m[r + 7][c] && !m[r + 8][c] && !m[r + 9][c] && !m[r + 10][c];
    }
}
