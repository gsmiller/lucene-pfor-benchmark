package gsmiller;

/** Utility class to encode sequences of 128 small positive integers. */
class PForCandidateDecoder extends PForBaselineDecoder {

    PForCandidateDecoder(ForUtil forUtil) {
        super(forUtil);
    }

    @Override
    protected void prefixSum(int bitsPerValue, int numExceptions, byte[] exceptions, long[] longs, long base) {
        expand32(longs);
        applyExceptions(bitsPerValue, numExceptions, exceptions, longs);
        applyPrefixSum(longs, base);
    }

    private static void applyExceptions(int bitsPerValue, int numExceptions, byte[] exceptions, long[] longs) {
        for (int i = 0; i < numExceptions; ++i) {
            final int exceptionPos = Byte.toUnsignedInt(exceptions[i * 2]);
            final long exception = Byte.toUnsignedLong(exceptions[i * 2 + 1]);
            longs[exceptionPos] |= exception << bitsPerValue;
        }
    }

    private static void applyPrefixSum(long[] longs, long base) {
        longs[0] += base;
        for (int i = 1; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += longs[i - 1];
        }
    }
}
