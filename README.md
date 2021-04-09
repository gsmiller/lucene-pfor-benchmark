NOTE: This is all based on the work done by Adrien Grand over here: [decode-128-ints-benchmark](https://github.com/jpountz/decode-128-ints-benchmark)

DISCLAIMER: I'm doing my best to stumble through some decompiled assembly here. I'm not particularly experienced with this,
so please verify results for yourself :)

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

When looking at the decompiled assembly the HostSpot appears to be creating, here's the main loop for the baseline:
```
  1.23%   ↗  0x00007f9607a02910: movslq %r10d,%rbx
  1.19%   │  0x00007f9607a02913: mov    %rbx,%rdi
  0.90%   │  0x00007f9607a02916: add    $0x1,%rdi
  1.49%   │  0x00007f9607a0291a: imul   %r9,%rdi
  1.34%   │  0x00007f9607a0291e: add    %rcx,%rdi
  1.45%   │  0x00007f9607a02921: mov    %rdi,0x10(%r8,%rbx,8)
  1.53%   │  0x00007f9607a02926: mov    %rbx,%rdi
  1.45%   │  0x00007f9607a02929: add    $0x4,%rdi
  1.42%   │  0x00007f9607a0292d: imul   %r9,%rdi
  1.31%   │  0x00007f9607a02931: add    %rcx,%rdi
  1.49%   │  0x00007f9607a02934: mov    %rbx,%rdx
  1.42%   │  0x00007f9607a02937: add    $0x3,%rdx
  1.38%   │  0x00007f9607a0293b: imul   %r9,%rdx
  1.16%   │  0x00007f9607a0293f: add    %rcx,%rdx
  1.75%   │  0x00007f9607a02942: mov    %rbx,%rsi
  1.16%   │  0x00007f9607a02945: add    $0x2,%rsi
  1.49%   │  0x00007f9607a02949: imul   %r9,%rsi
  3.92%   │  0x00007f9607a0294d: add    %rcx,%rsi
  2.83%   │  0x00007f9607a02950: mov    %rsi,0x18(%r8,%rbx,8)
  2.65%   │  0x00007f9607a02955: mov    %rdx,0x20(%r8,%rbx,8)
  1.72%   │  0x00007f9607a0295a: mov    %rdi,0x28(%r8,%rbx,8)  ;*lastore {reexecute=0 rethrow=0 return_oop=0}
          │                                                ; - gsmiller.PForBaselineDecoder::prefixSumOf@24 (line 12)
          │                                                ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
          │                                                ; - gsmiller.DecodeBenchmark::pForDeltaBaselineDecoder@42 (line 22)
          │                                                ; - gsmiller.generated.DecodeBenchmark_pForDeltaBaselineDecoder_jmhTest::pForDeltaBaselineDecoder_thrpt_jmhStub@151 (line 240)
  1.53%   │  0x00007f9607a0295f: add    $0x4,%r10d         ;*iadd {reexecute=0 rethrow=0 return_oop=0}
          │                                                ; - gsmiller.PForBaselineDecoder::prefixSumOf@17 (line 12)
          │                                                ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
          │                                                ; - gsmiller.DecodeBenchmark::pForDeltaBaselineDecoder@42 (line 22)
          │                                                ; - gsmiller.generated.DecodeBenchmark_pForDeltaBaselineDecoder_jmhTest::pForDeltaBaselineDecoder_thrpt_jmhStub@151 (line 240)
  2.01%   │  0x00007f9607a02963: cmp    $0x7d,%r10d
          ╰  0x00007f9607a02967: jl     0x00007f9607a02910  ;*if_icmpge {reexecute=0 rethrow=0 return_oop=0}
                                                           ; - gsmiller.PForBaselineDecoder::prefixSumOf@8 (line 11)
                                                           ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
                                                           ; - gsmiller.DecodeBenchmark::pForDeltaBaselineDecoder@42 (line 22)
                                                           ; - gsmiller.generated.DecodeBenchmark_pForDeltaBaselineDecoder_jmhTest::pForDeltaBaselineDecoder_thrpt_jmhStub@151 (line 240)
```

Compare this to the three main operations in the candidate (array copy, multiplication loop and add loop):
Looks like the array copy (which is getting vectorized):
```
  0.04%       0x00007f132fa052b9: mov    %r10,0x98(%rsp)
  0.04%       0x00007f132fa052c1: mov    0x90(%rsp),%rdi
              0x00007f132fa052c9: mov    %r10,%rsi
              0x00007f132fa052cc: mov    $0x80,%edx
              0x00007f132fa052d1: vzeroupper 
  0.23%       0x00007f132fa052d4: movabs $0x7f1327dd0f60,%r10
  0.12%       0x00007f132fa052de: callq  *%r10              ;*invokestatic arraycopy {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@9 (line 12)
                                                            ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
                                                            ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
                                                            ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
```
Looks like the main "multiplication loop" (looks pretty non-vectorized)
```
  0.58%   ↗│  0x00007f132fa05350: mov    %r13,%r10
  1.81%   ││  0x00007f132fa05353: imul   0x10(%rbp,%r9,8),%r10
  0.96%   ││  0x00007f132fa05359: mov    %r10,0x10(%rbp,%r9,8)
  2.69%   ││  0x00007f132fa0535e: mov    %r13,%r10
  0.19%   ││  0x00007f132fa05361: imul   0x18(%rbp,%r9,8),%r10
  1.23%   ││  0x00007f132fa05367: mov    %r10,0x18(%rbp,%r9,8)
  1.54%   ││  0x00007f132fa0536c: mov    %r13,%r10
  1.77%   ││  0x00007f132fa0536f: imul   0x20(%rbp,%r9,8),%r10
  0.19%   ││  0x00007f132fa05375: mov    %r10,0x20(%rbp,%r9,8)
  2.54%   ││  0x00007f132fa0537a: mov    %r13,%r10
  0.54%   ││  0x00007f132fa0537d: imul   0x28(%rbp,%r9,8),%r10
  1.42%   ││  0x00007f132fa05383: mov    %r10,0x28(%rbp,%r9,8)  ;*lastore {reexecute=0 rethrow=0 return_oop=0}
          ││                                                ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@30 (line 14)
          ││                                                ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
          ││                                                ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
          ││                                                ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
  1.27%   ││  0x00007f132fa05388: add    $0x4,%r9d          ;*iinc {reexecute=0 rethrow=0 return_oop=0}
          ││                                                ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@31 (line 13)
          ││                                                ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
          ││                                                ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
          ││                                                ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
  2.46%   ││  0x00007f132fa0538c: cmp    %r11d,%r9d
          ╰│  0x00007f132fa0538f: jl     0x00007f132fa05350  ;*putfield tmp {reexecute=0 rethrow=0 return_oop=0}
           │                                                ; - gsmiller.ForUtil::&lt;init&gt;@9 (line 186)
           │                                                ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@8 (line 28)
           │                                                ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
```
And the "addition loop" (which appears vectorized)
```
  0.23%  ↗    0x00007f132fa04ec0: vpaddq 0x10(%rbp,%r11,8),%ymm0,%ymm1
  0.69%  │    0x00007f132fa04ec7: vmovdqu %ymm1,0x10(%rbp,%r11,8)  ;*lastore {reexecute=0 rethrow=0 return_oop=0}
         │                                                  ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@54 (line 17)
         │                                                  ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
         │                                                  ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
         │                                                  ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
  0.35%  │    0x00007f132fa04ece: add    $0x4,%r11d         ;*iinc {reexecute=0 rethrow=0 return_oop=0}
         │                                                  ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@55 (line 16)
         │                                                  ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
         │                                                  ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
         │                                                  ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
  0.08%  │    0x00007f132fa04ed2: cmp    %ecx,%r11d
         ╰    0x00007f132fa04ed5: jl     0x00007f132fa04ec0  ;*if_icmpge {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - gsmiller.PForCandidateDecoderSlow::prefixSumOf@44 (line 16)
                                                            ; - gsmiller.PForBaselineDecoder::decodeAndPrefixSum@33 (line 39)
                                                            ; - gsmiller.DecodeBenchmark::pForDeltaCandidateDecoder@42 (line 29)
                                                            ; - gsmiller.generated.DecodeBenchmark_pForDeltaCandidateDecoder_jmhTest::pForDeltaCandidateDecoder_thrpt_jmhStub@151 (line 240)
```

