Explore more efficient ways to handle the special-case in PFOR decoding of 0 bits-per-value (all deltas are the same) 
but where the "same value" is something other than `1`. The PFOR encoding in Lucene (`PForUtil`) will look for cases
where all deltas in the same block are the same, and encode it with "zero bits-per-value". It will then record the
value once. The common case for this is "dense blocks" where all deltas are `1` (i.e., a very common term that many
docs contain). There can be rare cases though where all deltas are the same but not  `1`. This benchmark tries to
explore more efficient ways to handle this decoding, particularly around trying to get the HotSpot compiler to
auto-vectorize the loops.

The benchmark currently compares a "baseline" decoder to a "candidate" decoder, allowing different implementations of
`prefixSumOf` to be experimented with.

My current results are showing the baseline to be more performant, primarily due to the "multiplication loop" in the
candidate not getting vectorized. Nothing is getting vectorized in the baseline, but it's handling everything in one
loop vs. the candidate needing a loop plus two vectorized instructions (the array copy and the "addition loop").

```
Benchmark                                  (bitsPerValue)  (exceptionCount)  (sameVal)   Mode  Cnt  Score   Error   Units
DecodeBenchmark.pForDeltaBaselineDecoder                0                 0          2  thrpt   10  8.034 ± 0.022  ops/us
DecodeBenchmark.pForDeltaCandidateDecoder               0                 0          2  thrpt   10  6.849 ± 0.116  ops/us
```

NOTE: This is all based on the work done by Adrien Grand over here: [decode-128-ints-benchmark](https://github.com/jpountz/decode-128-ints-benchmark)