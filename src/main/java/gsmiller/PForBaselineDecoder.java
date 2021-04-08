package gsmiller;

final class PForBaselineDecoder extends AbstractPForDecoder {

    PForBaselineDecoder(ForUtil forUtil) {
        super(forUtil);
    }

    @Override
    protected void prefixSumOf(long[] longs, long base, long val) {
        for (int i = 0; i < ForUtil.BLOCK_SIZE; i++) {
            longs[i] = (i + 1) * val + base;
        }
    }
}
