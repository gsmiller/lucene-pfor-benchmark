package gsmiller;

/** Utility class to encode sequences of 128 small positive integers. */
class PForCandidateDecoder extends AbstractPForDecoder {

    PForCandidateDecoder(ForUtil forUtil) {
        super(forUtil);
    }

    @Override
    protected void prefixSumOf(long[] longs, long base, long val) {
        System.arraycopy(IDENTITY_PLUS_ONE, 0, longs, 0, ForUtil.BLOCK_SIZE);
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] *= val;
        }
        for (int i = 0; i < ForUtil.BLOCK_SIZE; ++i) {
            longs[i] += base;
        }
    }
}
