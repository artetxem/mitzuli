package org.apertium.transfer;

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
import org.apertium.CommandLineInterface;
import static org.apertium.utils.IOUtils.getStdinReader;
import static org.apertium.utils.IOUtils.getStdoutWriter;
import static org.apertium.utils.IOUtils.openInFileReader;
import static org.apertium.utils.IOUtils.openOutFileWriter;

import org.apertium.lttoolbox.*;
import org.apertium.lttoolbox.process.FSTProcessor;
import java.io.*;
import java.util.HashMap;

import org.apertium.lttoolbox.process.State;
import org.apertium.utils.IOUtils;

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
public class ApertiumTransfer {
  private static HashMap<String, Transfer> cache = new HashMap<String, Transfer>();
  private static boolean cacheEnabled = false;

  public static void setCacheEnabled(boolean enabled) {
    cacheEnabled = enabled;
    if (!enabled)
      clearCache();
  }

  public static void clearCache() {
    cache.clear();
  }

  static void showHelp(String name) {
    System.out.print(name + CommandLineInterface.PACKAGE_VERSION + ": \n"
        + "USAGE: " + name + " trules-class preproc biltrans [input [output]]\n"
        + "       " + name + " -b trules preproc [input [output]]\n"
        + "       " + name + " -n trules-class preproc [input [output]]\n"
        + //"       "+name+" -x extended trules preproc biltrans [input [output]]\n" +
        "       " + name + " -c trules-class  preproc biltrans [input [output]]\n"
        + "  trules-class  Java bytecode compiled transfer rules (.class file)\n"
        + "                or XML transfer rules (.t1x file)\n"
        + "  preproc       result of preprocess trules (.bin file)\n"
        + "  biltrans      bilingual letter transducer file\n"
        + "  input         input file, standard input by default\n"
        + "  output        output file, standard output by default\n"
        + "  -b            input from lexical transfer (single level transfer only)\n"
        + "  -n            don't use bilingual dictionary\n"
        + //"  -x bindix  extended mode with user dictionary\n" +
        "  -c         case-sensitiveness while accessing bilingual dictionary\n"
        + "  -z            null-flushing output on '\n"
        + "  -h            shows this message\n"
        + "");
  }

  public static void main(String[] argv) throws Exception {
    System.setProperty("file.encoding", "UTF-8");

    doMain(argv, null, null);
  }

  @SuppressWarnings("unchecked")
  public static void doMain(String[] argv, Reader input, Appendable output) throws Exception {

    boolean useBD = true;

    if (argv.length == 0) {
      showHelp("apertium-transfer-j");
      return;
    }

    boolean caseSensitiveMode = false;
    boolean nullFlush = false;
    boolean preBilingual = false;
    boolean useBilingual = true;

    MyGetOpt getopt = new MyGetOpt(argv, "cvbnzhD");

    int optind = -1;
    while (true) {
      try {

        int c = getopt.getNextOption();
        if (c == -1) {
          break;
        }
        optind++;
        switch (c) {
          case 'c':
            caseSensitiveMode = true;
            break;

          case 'D':
            FSTProcessor.DEBUG = true;
            State.DEBUG = true;
            AbstractTransfer.DEBUG = true;
            break;

          case 'z':
            nullFlush = true;
            break;

          case 'b':
            preBilingual = true;
            useBD = false;
            break;

          case 'n':
            useBilingual = false;
            useBD = false;
            break;

          case 'v':
            System.out.println(CommandLineInterface.PACKAGE_VERSION);
            return;

          case 'h':
          default:
            showHelp(argv[0]);
            return;
        }
      } catch (Exception e) {
        e.printStackTrace();
        showHelp(null);
      }
    }

    /* If we're not using the billingual dictionary, we don't need the
     * biltrans argument.
     * This number should be one less than the number of args present, minus
     * command-line switches.
     */
    int minArgs = (useBD ? optind + 3 : optind + 2);

    if (argv.length <= minArgs) {
      showHelp(null);
      return;
    }

    /* Split out into explicit variables for readability and because
     * tRulesClass originally was going to be tweaked here, but that
     * was split off into a separate method so that it could be used
     * in Interchunk and Postchunk as well.
     */

    /* Now, heres a dilemma: We might have been invoked with an XML file
     * (t1x, t2x, t3x) instead of a bytecode .class file!
     * This is because apertium-j is interpreting the .mode files in a fully
     * ignorant way with no check of argument suffices etc and letting the
     * stages themselves decide what to do. (This is a good thing!)
     * C++ way is: apertium-transfer apertium-eo-en.eo-en.t1x eo-en.t1x.bin eo-en.autobil.bin
     * expected is: apertium-transfer-j eo-en.t1x.class eo-en.t1x.bin eo-en.autobil.bin
     * see also http://wiki.apertium.org/wiki/Bytecode_for_transfer
     */
    Transfer t = null;
    String tRulesOrClassString = argv[optind + 1];
    String preProc = argv[optind + 2];
    String bilTrans = useBD ? argv[optind + 3] : null;
    String key = tRulesOrClassString + "; " + preProc + "; " + bilTrans;
    t = cache.get(key);
    if (t == null) {
      Class tRulesClass = TransferClassLoader.loadTxClass(tRulesOrClassString, preProc);
      t = new Transfer();
      t.read(tRulesClass, preProc, bilTrans);
      if (cacheEnabled)
        cache.put(key, t);
    }
    t.setNullFlush(nullFlush);
    t.setPreBilingual(preBilingual);
    t.setUseBilingual(useBilingual);
    //setCaseSensitiveMode is not implemented yet at Transfer, so we comment the following line
    //t.setCaseSensitiveMode(caseSensitiveMode);
    t.transferObject.debug = Transfer.DEBUG;

    if (input != null || output != null) {
      /* If either is supplied, ignore command-line input/output files,
       * as we are in inter-jvm pipeline mode, and if the modes file
       * is supplying input/ouput files, we don't want to use them,
       * as we are keeping everything in memory inside the jvm.
       */
      if (input == null) {
        input = getStdinReader();
      }
      if (output == null) {
        output = getStdoutWriter();
      }
    } else {
      /* If we aren't using the billingual dictionary, then there
       * will be one less argument on the command line than if we are.
       * And input and output files are the last two arguments on the
       * command line
       */
      int inputIndex = (useBD ? optind + 4 : optind + 3);
      if (argv.length > inputIndex) {
        input = openInFileReader(argv[optind + 4]);
      } else {
        input = getStdinReader();
      }
      int outputIndex = (useBD ? optind + 5 : optind + 4);
      if (argv.length > outputIndex) {
        output = openOutFileWriter(argv[optind + 5]);
      } else {
        output = getStdoutWriter();
      }
    }

    try {
      t.process(input, output);
      input.close();
      IOUtils.close(output);
    } catch (Exception e) {
      IOUtils.flush(output);
      System.out.flush();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e1) {
      }
      e.printStackTrace();
      if (t.getNullFlush()) {
        output.append('\0');
      }
      // Not JDK 1.5 compliant: throw new IOException(e);
      throw new Exception(e);
    }

  }
}
