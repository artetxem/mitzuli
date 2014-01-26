/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox;

import org.apertium.lttoolbox.compile.TransducerCollection;
import java.io.IOException;
import java.io.PrintStream;
import org.apertium.CommandLineInterface;
import org.apertium.lttoolbox.compile.TransducerComp;
import org.apertium.lttoolbox.compile.TransducerPrint;
import org.apertium.lttoolbox.compile.TransducerPrintExpandish;
import org.apertium.lttoolbox.process.BasicFSTProcessor;

/**
 * @author Jacob Nordfalk
 */
public class LTPrint {
  private static void showHelp() {
    System.out.println(" v" + CommandLineInterface.PACKAGE_VERSION + ": dump a transducer to text"
        + "\nUSAGE: lt-print [ -a | -s ] bin_file"
        + "\nOptions"
        + "\n  -a:   dump to text in ATT format (default)"
        + "\n  -s:   print strings in a format similar to lt-expand"
        );
  }

  public static void main(String[] args) throws IOException {
    if (args.length==1) doPrintATT(args[0], System.out);
    else if (args.length==2 && args[0].equals("-a")) doPrintATT(args[1], System.out);
    else if (args.length==2 && args[0].equals("-s")) doPrintLtExpandish(args[1], System.out);
    else showHelp();
  }


  private static void doPrintLtExpandish(String file, PrintStream out) throws IOException {

    TransducerCollection tc = new TransducerCollection();
    tc.read(file);
    for (TransducerComp t : tc.sections.values()) {
      new TransducerPrintExpandish(t).showLtExpandish(tc.alphabet, out);
    }
  }



  private static void doPrintATT(String file, PrintStream out) throws IOException {

    TransducerCollection tc = new TransducerCollection();
    tc.read(file);
    boolean first = true;
    for (TransducerComp t : tc.sections.values()) {
      new TransducerPrint(t).show(tc.alphabet, out);
      if (!first) {
        //System.out.println("-- "+name);
        System.out.println("--");
      }
      first = false;
    }
  }
}
