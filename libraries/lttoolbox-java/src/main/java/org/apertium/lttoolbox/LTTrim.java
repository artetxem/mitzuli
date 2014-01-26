/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox;

import org.apertium.lttoolbox.compile.TransducerCollection;
import java.io.IOException;
import java.util.Map;
import org.apertium.CommandLineInterface;
import org.apertium.lttoolbox.compile.TransducerComp;
import org.apertium.lttoolbox.compile.TransducerTrim;
import org.apertium.lttoolbox.process.BasicFSTProcessor;

/**
 * @author Jacob Nordfalk
 */
public class LTTrim {
  private static void showHelp() {
    System.out.println(" v" + CommandLineInterface.PACKAGE_VERSION + ": trim a transducer to metch input of another transducer"
        + "\nUSAGE: lt-trim [ -v ] automorph.bin autobil.bin trimmed_output_automorph.bin"
        + "\nOptions"
        + "\n  -v:   verbose output"
        );
  }

  public static void main(String[] args) throws IOException {
    if (args.length==3) doTrim(args[0], args[1], args[2]);
    else if (args.length==4 && args[0].equals("-v")) {
      TransducerComp.DEBUG = true; // hack - might give problems in concurrent environments
      doTrim(args[1], args[2], args[3]);
    }
    else showHelp();

  }




  private static void doTrim(String monodixf, String bidixf, String trimmedOutputf) throws IOException {
    TransducerCollection mon = new TransducerCollection();
    mon.read(monodixf);
    
    BasicFSTProcessor bil2 = new BasicFSTProcessor();
    bil2.load(bidixf);

    for (TransducerComp t : mon.sections.values()) {
      new TransducerTrim(t).trim(mon.alphabet, bil2);
      t.minimize();
    }
/*
    for (Map.Entry<String, TransducerComp> e : mon.sections.entrySet()) {
      System.out.println();
      System.out.println("--"+e.getKey());
      TransducerComp t = e.getValue();
      t.show(mon.alphabet, System.out);
      t.showLtExpandish(mon.alphabet, System.out);
    }

    for (TransducerComp t : mon.sections.values()) t.minimize();
*/


    mon.write(trimmedOutputf);
  }
}
