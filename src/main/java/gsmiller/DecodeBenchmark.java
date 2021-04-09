package gsmiller;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 8, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DecodeBenchmark {

  @Benchmark
  public void pForDeltaBaselineDecoder(DecodeState state, Blackhole bh) throws  IOException {
    PForBaselineDecoder decoder = new PForBaselineDecoder(new ForUtil());
    decoder.decodeAndPrefixSum(state.bitsPerValue, state.input, state.exceptions, state.sameVal, state.base, state.outputLongs);
    bh.consume(state.outputLongs);
  }

  //@Benchmark
  public void pForDeltaCandidateDecoderSlow(DecodeState state, Blackhole bh) throws  IOException {
    PForCandidateDecoderSlow decoder = new PForCandidateDecoderSlow(new ForUtil());
    decoder.decodeAndPrefixSum(state.bitsPerValue, state.input, state.exceptions, state.sameVal, state.base, state.outputLongs);
    bh.consume(state.outputLongs);
  }

  @Benchmark
  public void pForDeltaCandidateDecoderFaster(DecodeState state, Blackhole bh) throws  IOException {
    PForCandidateDecoderFaster decoder = new PForCandidateDecoderFaster(new ForUtil());
    decoder.decodeAndPrefixSum(state.bitsPerValue, state.input, state.exceptions, state.sameVal, state.base, state.outputLongs);
    bh.consume(state.outputLongs);
  }
}
