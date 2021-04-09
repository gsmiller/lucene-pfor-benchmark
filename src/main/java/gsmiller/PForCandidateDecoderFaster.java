package gsmiller;

import java.io.IOException;
import java.nio.ByteBuffer;

public class PForCandidateDecoderFaster extends PForBaselineDecoder {

    PForCandidateDecoderFaster(ForUtil forUtil) {
        super(forUtil);
    }

    @Override
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
                if (bitsPerValue <= 3) {
                    forUtil.decodeTo8(bitsPerValue, in, longs);
                    prefixSum8(longs, base);
//                } else if (bitsPerValue <= 10) {
//                    forUtil.decodeTo16(bitsPerValue, in, longs);
                } else {
                    forUtil.decodeTo32(bitsPerValue, in, longs);
                    prefixSum32(longs, base);
                }
            }
        } else { // we have exceptions
            // pack two values per long so we can apply prefixes two-at-a-time, just like in ForUtil
            if (bitsPerValue == 0) {
                fillSameValue32(longs, sameVal);
            } else {
                forUtil.decodeTo32(bitsPerValue, in, longs);
            }
            prefixSum(bitsPerValue, exceptions.length / 2, exceptions, longs, base);
        }
    }

    protected static void prefixSum8(long[] longs, long base) {
        innerPrefixSum8(longs);
        expand8(longs);
        final long l1 = longs[15];
        final long l2 = longs[31];
        final long l3 = longs[47];
        final long l4 = longs[63];
        final long l5 = longs[79];
        final long l6 = longs[95];
        final long l7 = longs[111];
        for (int i = 16; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l1;
        }
        for (int i = 32; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l2;
        }
        for (int i = 48; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l3;
        }
        for (int i = 64; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l4;
        }
        for (int i = 80; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l5;
        }
        for (int i = 96; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l6;
        }
        for (int i = 112; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += l7;
        }
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += base;
        }
    }

    protected static void expand8(long[] longs) {
        for (int i = 0; i < 16; ++i) {
            long l = longs[i];
            longs[i] = (l >>> 56) & 0xFFL;
            longs[16 + i] = (l >>> 48) & 0xFFL;
            longs[32 + i] = (l >>> 40) & 0xFFL;
            longs[48 + i] = (l >>> 32) & 0xFFL;
            longs[64 + i] = (l >>> 24) & 0xFFL;
            longs[80 + i] = (l >>> 16) & 0xFFL;
            longs[96 + i] = (l >>> 8) & 0xFFL;
            longs[112 + i] = l & 0xFFL;
        }
    }

    protected static void innerPrefixSum8(long[] longs) {
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
    }
}
