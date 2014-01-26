package org.apertium.lex;

/*
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import org.apertium.CommandLineInterface;
import org.apertium.lttoolbox.Getopt;
import org.apertium.lttoolbox.process.FSTProcessor;
import static org.apertium.utils.IOUtils.*;

/**
 *
 * @author Jacob Nordfalk
 */
public class LRXProc {
  private static LRXProcessor lrxp;

  public static void main(String[] argv) throws Exception {
    System.setProperty("file.encoding", "UTF-8");
    Reader input = null;
    Appendable output = null;

    final int argc = argv.length;

    Getopt getopt = new Getopt("apertium-lex-tools", argv, "td");

    while (true) {
      int c = getopt.getopt();
      if (c == -1) {
        break;
      }

      switch (c) {
        case 't':
          lrxp.setTraceMode(true);
          break;
        case 'd':
          lrxp.setDebugMode(true);
          break;
        default:
          System.err.println("Unregognized parameter: " + (char) c);
          break;
      }
    }


    int optind = getopt.getOptind();
    if (getopt.getOptind() == argc - 2) { //Both input and output files specified, and not in pipeline mode
      input = openInFileReader(argv[optind + 2]);
      output = openOutFileWriter(argv[optind + 3]);
    } else if (getopt.getOptind() == argc - 1) { //Only input file specified, and not in pipeline mode
      input = openInFileReader(argv[optind + 2]);
    } else { //Neither file specified, or in pipeline mode
      input = getStdinReader();
      output = getStdoutWriter();
    }


    final String filename = argv[optind + 1];
    ByteBuffer in = openFileAsByteBuffer(filename);
    //never happens if (in == null) endProgram("LTProc");
    lrxp = new LRXProcessor();
    lrxp.load(in);

    lrxp.process(input, output);

    input.close();
    ((Writer) output).close();

  }
}
