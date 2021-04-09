package gsmiller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class DecodeState {

  private FileChannel channel;
  ByteBuffer input;
  int base;
  byte[] exceptions;

  long[] outputLongs;

  @Param({ "0", "2", "4", "8", "16" })
  int bitsPerValue;

  @Param({ "0", "1", "2", "3", "4", "5", "6", "7" })
  int exceptionCount;

  @Param({ "1" })
  int sameVal;

  @Setup(Level.Trial)
  public void setupTrial() throws IOException {
    Path path = Files.createTempFile("DecodeState", ".bench");
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
      byte[] data = new byte[128 * Integer.BYTES];
      exceptions = new byte[exceptionCount * 2];
      Random random = new Random(0);
      random.nextBytes(data);
      base = random.nextInt(32000); // make sure this is small enough we won't overflow
      if (exceptionCount > 0) {
        byte[] exceptionBytes = new byte[exceptionCount];
        random.nextBytes(exceptionBytes);
        for (int i = 0; i < exceptionCount; i++) {
          byte pos = (byte) random.nextInt(128);
          exceptions[i * 2] = pos;
          exceptions[i * 2 + 1] = exceptionBytes[i];
        }
      }
      channel.write(ByteBuffer.wrap(data));
    }
    channel = FileChannel.open(path, StandardOpenOption.READ);
    input = channel.map(MapMode.READ_ONLY, 0, 128 * Integer.BYTES);
    input.order(ByteOrder.LITTLE_ENDIAN);

    outputLongs = new long[129]; // in Lucene, this is 129 to store a marker "no more docs" record at the end
  }

  @Setup(Level.Invocation)
  public void setupInvocation() {
    // Reset the position of the buffer
    input.position(0);
  }

  @TearDown(Level.Trial)
  public void tearDownTrial() throws IOException {
    input = null;
    if (channel != null) {
      channel.close();
    }
  }

}
