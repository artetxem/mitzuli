package org.apertium.lttoolbox;

/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import org.apertium.CommandLineInterface;
import org.apertium.lttoolbox.process.FSTProcessor;
import org.apertium.lttoolbox.process.State;
import org.apertium.utils.IOUtils;
import static org.apertium.utils.IOUtils.*;

// Use GNU Getopt
class MyGetOpt extends Getopt {
  public MyGetOpt(String[] argv, String string) {
    super("lttoolbox", argv, string);
  }

  int getNextOption() {
    return getopt();
  }
}

/**
 *
 * @author Raah
 */
public class LTProc {
  private static HashMap<String, FSTProcessor> cache = new HashMap<String, FSTProcessor>();
  private static boolean cacheEnabled = false;

  public static void setCacheEnabled(boolean enabled) {
    cacheEnabled = enabled;
    if (!enabled)
      clearCache();
  }

  public static void clearCache() {
    cache.clear();
  }

  private static void showHelp(String name) {
    System.out.print(name + CommandLineInterface.PACKAGE_VERSION + ": process a stream with a letter transducer\n"
        + "USAGE: " + name + " [-c] [-a|-g|-n|-d|-b|-p|-s|-t] fst_file [input_file [output_file]]\n"
        + "Options:\n"
        + "  -a:   morphological analysis (default behavior)\n"
        + "  -c:   use the literal case of the incoming characters\n"
        + "  -e:   morphological analysis, with compound analysis on unknown words\n"
        + "  -f:   match flags (experimental)\n"
        + "  -g:   morphological generation\n"
        + "  -n:   morph. generation without unknown word marks\n"
        + "  -d:   morph. generation with all the stuff\n"
        + "  -t:   morph. generation, but retaining part-of-speech\n"
        + "  -p:   post-generation\n"
        + "  -s:   SAO annotation system input processing\n"
        + "  -t:   apply transliteration dictionary\n"
        + "  -z:   flush output on the null character\n"
        + "  -w:   use dictionary case instead of surface case\n"
        + "  -v:   version\n"
        + "  -D:   debug; print diagnostics to stderr\n"
        + "  -S:   show hidden control symbols (for flagmatch and compounding)\n"
        + "  -h:   show this help\n");

  }

  static void checkValidity(FSTProcessor fstp) {
    if (!fstp.valid()) {
      throw new RuntimeException("Validity test for FSTProcessor failed");
    }
  }

  public static void main(String[] argv) throws Exception {
    System.setProperty("file.encoding", "UTF-8");

    doMain(argv, null, null);
  }

  public static void doMain(String[] argv, Reader input, Appendable output) throws IOException {

    if (argv.length == 0) {
      showHelp("LTProc");
      return;
    }

    final int argc = argv.length;

    int cmd = 'a';

    boolean caseSensitiveMode = false;
    boolean flagMatchMode = false;
    boolean showControlSymbols = false;
    boolean dictionaryCase = false;
    boolean nullFlush = false;

    MyGetOpt getopt = new MyGetOpt(argv, "DSabcdefglndpstwzvh");

    while (true) {

      try {
        int c = getopt.getNextOption();
        if (c == -1) {
          break;
        }

        switch (c) {
          case 'c':
            caseSensitiveMode = true;
            break;

          case 'f':
            flagMatchMode = true;
            break;

          case 'S':
            showControlSymbols = true;
            break;

          case 'D':
            FSTProcessor.DEBUG = true;
            State.DEBUG = true;
            break;

          case 'e':
          case 'a':
          case 'b':
          case 'l':
          case 'g':
          case 'n':
          case 'd':
          case 'p':
          case 't':
          case 's':
            cmd = c;
            break;

          case 'w':
            dictionaryCase = true;
            break;

          case 'z':
            nullFlush = true;
            break;

          case 'v':
            System.out.println("org.apertium.lttoolbox.LTProc version " + CommandLineInterface.PACKAGE_VERSION);
            return;

          case 'h':
          default:
            System.err.println("Unregognized parameter: " + (char) c);
            showHelp("LTProc");
            return;
        }

      } catch (Exception e) {
        showHelp("LTProc");
        return;
      }
    }

    boolean pipelineMode = false;

    if (input != null || output != null) {
      /* We are running in pipeline mode and want to ignore any temporary
       * input/output files that may have been specified on the command line
       * in the modes file.
       */
      if (input == null) {
        getStdinReader();
      }
      if (output == null) {
        getStdoutWriter();
      }
      pipelineMode = true;
    } else {
      output = getStdoutWriter();
    }

    int optind = getopt.getOptind() - 1;
    if (optind == (argc - 4) && !pipelineMode) { //Both input and output files specified, and not in pipeline mode
      if ((input = openInFileReader(argv[optind + 2])) == null
          || (output = openOutFileWriter(argv[optind + 3])) == null) {
        showHelp("LTProc");
        return;
      }
    } else if (optind == (argc - 3) && !pipelineMode) { //Only input file specified, and not in pipeline mode
      if ((input = openInFileReader(argv[optind + 2])) == null) {
        showHelp("LTProc");
        return;
      }
    } else { //Neither file specified, or in pipeline mode
      if (input == null)
        input = getStdinReader(); //Only assign if it hasn't been assigned yet
      if (optind != (argc - 2)) {
        showHelp("LTProc");
        return;
      }
    }

    if (IOUtils.timing != null)
      IOUtils.timing.log("");

    FSTProcessor fstp = null;
    final String filename = argv[optind + 1];
    fstp = cache.get(filename);
    if (fstp == null) {
      fstp = new FSTProcessor();
      ByteBuffer in = openFileAsByteBuffer(filename);
      fstp.load(in, filename);
      if (IOUtils.timing != null)
        IOUtils.timing.log("Load transducer " + filename);
      if (cacheEnabled)
        cache.put(filename, fstp);
    }

    fstp.setCaseSensitiveMode(caseSensitiveMode);
    fstp.setFlagMatchMode(flagMatchMode);
    fstp.setShowControlSymbols(showControlSymbols);
    fstp.setDictionaryCase(dictionaryCase);
    fstp.setNullFlush(nullFlush);

    try {
      switch (cmd) {
        case 'n':
          fstp.initGeneration();
          checkValidity(fstp);
          fstp.generation(input, output, FSTProcessor.GenerationMode.gm_clean);
          break;

        case 'g':
          fstp.initGeneration();
          checkValidity(fstp);
          fstp.generation(input, output, FSTProcessor.GenerationMode.gm_unknown);
          break;

        case 'd':
          fstp.initGeneration();
          checkValidity(fstp);
          fstp.generation(input, output, FSTProcessor.GenerationMode.gm_all);

        case 'l':
          fstp.initGeneration();
          checkValidity(fstp);
          fstp.generation(input, output, FSTProcessor.GenerationMode.gm_tagged);

        case 'p':
          fstp.initPostgeneration();
          checkValidity(fstp);
          fstp.postgeneration(input, output);
          break;

        case 's':
          fstp.initAnalysis();
          checkValidity(fstp);
          fstp.SAO(input, output);
          break;

        case 't':
          fstp.initPostgeneration();
          checkValidity(fstp);
          fstp.transliteration(input, output);
          break;

        case 'e':
          fstp.initDecomposition();
          checkValidity(fstp);
          fstp.analysis(input, output);
          break;

        case 'b':
          fstp.initBiltrans();
          checkValidity(fstp);
          fstp.bilingual(input, output);
          break;

        case 'a':
        default:
          fstp.initAnalysis();
          checkValidity(fstp);
          fstp.analysis(input, output);
          break;
      }
      if (IOUtils.timing != null)
        IOUtils.timing.log("Process transducer " + filename);
    } catch (Exception e) {
      System.out.flush();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e1) {
      }
      e.printStackTrace();
      if (fstp.getNullFlush()) {
        output.append('\0');
      }
      //Not JDK 1.5 compliant: throw new IOException(e); // Send to parent
      throw new RuntimeException(e); // Send to parent
    }


    input.close();
    IOUtils.close(output);
  }
}
