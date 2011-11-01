package org.apache.hama.bsp;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;

public class LineRecordReader implements RecordReader<LongWritable, Text> {
  private static final Log LOG = LogFactory.getLog(LineRecordReader.class
      .getName());

  private CompressionCodecFactory compressionCodecs = null;
  private long start;
  private long pos;
  private long end;
  private LineReader in;
  int maxLineLength;

  /**
   * A class that provides a line reader from an input stream.
   */
  public static class LineReader extends org.apache.hadoop.util.LineReader {
    LineReader(InputStream in) {
      super(in);
    }

    LineReader(InputStream in, int bufferSize) {
      super(in, bufferSize);
    }

    public LineReader(InputStream in, Configuration conf) throws IOException {
      super(in, conf);
    }
  }

  public LineRecordReader(Configuration job, FileSplit split)
      throws IOException {
    this.maxLineLength = job.getInt("bsp.linerecordreader.maxlength",
        Integer.MAX_VALUE);
    start = split.getStart();
    end = start + split.getLength();
    final Path file = split.getPath();
    compressionCodecs = new CompressionCodecFactory(job);
    final CompressionCodec codec = compressionCodecs.getCodec(file);

    // open the file and seek to the start of the split
    FileSystem fs = file.getFileSystem(job);
    FSDataInputStream fileIn = fs.open(split.getPath());
    boolean skipFirstLine = false;
    if (codec != null) {
      in = new LineReader(codec.createInputStream(fileIn), job);
      end = Long.MAX_VALUE;
    } else {
      if (start != 0) {
        skipFirstLine = true;
        --start;
        fileIn.seek(start);
      }
      in = new LineReader(fileIn, job);
    }
    if (skipFirstLine) { // skip first line and re-establish "start".
      start += in.readLine(new Text(), 0, (int) Math.min(
          (long) Integer.MAX_VALUE, end - start));
    }
    this.pos = start;
  }

  public LineRecordReader(InputStream in, long offset, long endOffset,
      int maxLineLength) {
    this.maxLineLength = maxLineLength;
    this.in = new LineReader(in);
    this.start = offset;
    this.pos = offset;
    this.end = endOffset;
  }

  public LineRecordReader(InputStream in, long offset, long endOffset,
      Configuration job) throws IOException {
    this.maxLineLength = job.getInt("bsp.linerecordreader.maxlength",
        Integer.MAX_VALUE);
    this.in = new LineReader(in, job);
    this.start = offset;
    this.pos = offset;
    this.end = endOffset;
  }

  public LongWritable createKey() {
    return new LongWritable();
  }

  public Text createValue() {
    return new Text();
  }

  /** Read a line. */
  public synchronized boolean next(LongWritable key, Text value)
      throws IOException {

    while (pos < end) {
      key.set(pos);

      int newSize = in.readLine(value, maxLineLength, Math.max((int) Math.min(
          Integer.MAX_VALUE, end - pos), maxLineLength));
      if (newSize == 0) {
        return false;
      }
      pos += newSize;
      if (newSize < maxLineLength) {
        return true;
      }

      // line too long. try again
      LOG
          .info("Skipped line of size " + newSize + " at pos "
              + (pos - newSize));
    }

    return false;
  }

  /**
   * Get the progress within the split
   */
  public float getProgress() {
    if (start == end) {
      return 0.0f;
    } else {
      return Math.min(1.0f, (pos - start) / (float) (end - start));
    }
  }

  public synchronized long getPos() throws IOException {
    return pos;
  }

  public synchronized void close() throws IOException {
    if (in != null) {
      in.close();
    }
  }
}
