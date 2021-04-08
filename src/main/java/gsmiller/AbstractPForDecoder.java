package gsmiller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

abstract class AbstractPForDecoder {

    private static final int MAX_EXCEPTIONS = 7;
    private static final int HALF_BLOCK_SIZE = ForUtil.BLOCK_SIZE / 2;

    // IDENTITY_PLUS_ONE[i] == i+1
    protected static final long[] IDENTITY_PLUS_ONE = new long[ForUtil.BLOCK_SIZE];

    static {
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
            IDENTITY_PLUS_ONE[i] = i + 1;
        }
    }

    private final ForUtil forUtil;
    // buffer for reading exception data; each exception uses two bytes (pos + high-order bits of the
    // exception)
    private final byte[] exceptionBuff = new byte[MAX_EXCEPTIONS * 2];

    AbstractPForDecoder(ForUtil forUtil) {
        assert ForUtil.BLOCK_SIZE <= 256 : "blocksize must fit in one byte. got " + ForUtil.BLOCK_SIZE;
        this.forUtil = forUtil;
    }

    /** Decode deltas, compute the prefix sum and add {@code base} to all decoded longs. */
    void decodeAndPrefixSum(int bitsPerValue, ByteBuffer in, byte[] exceptions, long sameVal, long base, long[] longs) throws IOException {
        if (exceptions.length == 0) {
            // handle the zero-exception case very similarly to ForDeltaUtil
            if (bitsPerValue == 0) {
                if (sameVal == 1) {
                    prefixSumOfOnes(longs, base);
                } else {
                    prefixSumOf(longs, base, sameVal);
                }
            } else {
                forUtil.decodeAndPrefixSum(bitsPerValue, in, base, longs);
            }
        } else { // we have exceptions
            // pack two values per long so we can apply prefixes two-at-a-time, just like in ForUtil
            if (bitsPerValue == 0) {
                fillSameValue32(longs, sameVal);
            } else {
                forUtil.decodeTo32(bitsPerValue, in, longs);
            }
            applyExceptions32(bitsPerValue, exceptions.length / 2, exceptions, longs);
            prefixSum32(longs, base);
        }
    }

    /**
     * Fill {@code longs} with the final values for the case of all deltas being 1. Note this assumes
     * there are no exceptions to apply.
     */
    private static void prefixSumOfOnes(long[] longs, long base) {
        System.arraycopy(IDENTITY_PLUS_ONE, 0, longs, 0, ForUtil.BLOCK_SIZE);
        // This loop gets auto-vectorized
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += base;
        }
    }

    /**
     * Fill {@code longs} with the final values for the case of all deltas being {@code val}. Note
     * this assumes there are no exceptions to apply.
     */
    protected abstract void prefixSumOf(long[] longs, long base, long val);

    /**
     * Fills the {@code longs} with the provided {@code val}, packed two values per long (using 32
     * bits per value).
     */
    private static void fillSameValue32(long[] longs, long val) {
        final long token = val << 32 | val;
        Arrays.fill(longs, 0, HALF_BLOCK_SIZE, token);
    }

    /** Apply the exceptions where the values are packed two-per-long in {@code longs}. */
    private void applyExceptions32(int bitsPerValue, int numExceptions, byte[] exceptions, long[] longs) {
        System.arraycopy(exceptions, 0, exceptionBuff, 0, exceptions.length);
        for (int i = 0; i < numExceptions; ++i) {
            final int exceptionPos = Byte.toUnsignedInt(exceptionBuff[i * 2]);
            final long exception = Byte.toUnsignedLong(exceptionBuff[i * 2 + 1]);
            // note that we pack two values per long, so the index is [0..63] for 128 values
            final int idx = exceptionPos & 0x3f; // mod 64
            // we need to shift by 1) the bpv, and 2) 32 for positions [0..63] (and no 32 shift for
            // [64..127])
            final int shift = bitsPerValue + ((1 ^ (exceptionPos >>> 6)) << 5);
            longs[idx] |= exception << shift;
        }
    }

    /** Apply prefix sum logic where the values are packed two-per-long in {@code longs}. */
    private static void prefixSum32(long[] longs, long base) {
        longs[0] += base << 32;
        innerPrefixSum32(longs);
        expand32(longs);
        final long l = longs[HALF_BLOCK_SIZE - 1];
        for (int i = HALF_BLOCK_SIZE; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l;
        }
    }

    /**
     * Expand the values packed two-per-long in {@code longs} into 128 individual long values stored
     * back into {@code longs}.
     */
    private static void expand32(long[] longs) {
        for (int i = 0; i < 64; ++i) {
            final long l = longs[i];
            longs[i] = l >>> 32;
            longs[64 + i] = l & 0xFFFFFFFFL;
        }
    }

    /**
     * Unrolled "inner" prefix sum logic where the values are packed two-per-long in {@code longs}.
     * After this method, the final values will be correct for all high-order bits (values [0..63])
     * but a final prefix loop will still need to run to "correct" the values of [64..127] in the
     * low-order bits, which need the 64th value added to all of them.
     */
    private static void innerPrefixSum32(long[] longs) {
        longs[1] += longs[0];
        longs[2] += longs[1];
        longs[3] += longs[2];
        longs[4] += longs[3];
        longs[5] += longs[4];
        longs[6] += longs[5];
        longs[7] += longs[6];
        longs[8] += longs[7];
        longs[9] += longs[8];
        longs[10] += longs[9];
        longs[11] += longs[10];
        longs[12] += longs[11];
        longs[13] += longs[12];
        longs[14] += longs[13];
        longs[15] += longs[14];
        longs[16] += longs[15];
        longs[17] += longs[16];
        longs[18] += longs[17];
        longs[19] += longs[18];
        longs[20] += longs[19];
        longs[21] += longs[20];
        longs[22] += longs[21];
        longs[23] += longs[22];
        longs[24] += longs[23];
        longs[25] += longs[24];
        longs[26] += longs[25];
        longs[27] += longs[26];
        longs[28] += longs[27];
        longs[29] += longs[28];
        longs[30] += longs[29];
        longs[31] += longs[30];
        longs[32] += longs[31];
        longs[33] += longs[32];
        longs[34] += longs[33];
        longs[35] += longs[34];
        longs[36] += longs[35];
        longs[37] += longs[36];
        longs[38] += longs[37];
        longs[39] += longs[38];
        longs[40] += longs[39];
        longs[41] += longs[40];
        longs[42] += longs[41];
        longs[43] += longs[42];
        longs[44] += longs[43];
        longs[45] += longs[44];
        longs[46] += longs[45];
        longs[47] += longs[46];
        longs[48] += longs[47];
        longs[49] += longs[48];
        longs[50] += longs[49];
        longs[51] += longs[50];
        longs[52] += longs[51];
        longs[53] += longs[52];
        longs[54] += longs[53];
        longs[55] += longs[54];
        longs[56] += longs[55];
        longs[57] += longs[56];
        longs[58] += longs[57];
        longs[59] += longs[58];
        longs[60] += longs[59];
        longs[61] += longs[60];
        longs[62] += longs[61];
        longs[63] += longs[62];
    }
}
