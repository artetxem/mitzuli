/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium;

import org.apertium.pipeline.ApertiumMain;
import java.io.File;
import org.apertium.lttoolbox.*;
import java.util.Arrays;
import javax.swing.UIManager;
import org.apertium.formatter.TextFormatter;
import org.apertium.interchunk.ApertiumInterchunk;
import org.apertium.postchunk.ApertiumPostchunk;
import org.apertium.pretransfer.PreTransfer;
import org.apertium.tagger.Tagger;
import org.apertium.transfer.ApertiumTransfer;
import org.apertium.transfer.generation.TransferBytecode;

/**
 *
 * @author Jacob Nordfalk
 */
public class CommandLineInterface {
  public static final String PACKAGE_VERSION = "3.2j";

  private static void showHelp(String invocationCommand) {
    String bareCommand = "";
    if (invocationCommand == null) {
      String jar = System.getProperty("java.class.path");
      if (jar.contains(":") || !jar.endsWith("lttoolbox.jar"))
        jar = "lttoolbox.jar";
      bareCommand = "java -jar " + jar;
      invocationCommand = bareCommand + " [task]";
    }
    /*
     * System.out.println(System.getProperty("java.class.path"));
     * System.out.println(System.getProperties());
     * System.out.println(System.getenv());
     * System.out.println(CommandLineInterface.class.getResource("/x"));
     * System.out.println(CommandLineInterface.class.getResource("."));
     */
    System.err.println("lttoolbox-java: A java port of the apertium machine translation architecture\n"
        + "USAGE: " + invocationCommand + "\n"
        + "Examples:\n"
        + " " + bareCommand + " gui                         start a simple Graphical User Interface\n"
        + " " + bareCommand + " apertium en-eo              use the translator\n"
        + " " + bareCommand + " lt-expand dictionary.dix    expand a dictionary\n"
        + " " + bareCommand + " lt-validate  dic.dix        validate a  dictionary\n"
        + " " + bareCommand + " lt-comp lr dic.dix dic.bin  compile a dictionary\n"
        + " " + bareCommand + " lt-proc dic.bin             morphological analysis/generation\n"
        + " " + bareCommand + " apertium-tagger             tagging/disambigation\n"
        + " " + bareCommand + " apertium-transfer           lexical transfer\n"
        + " " + bareCommand + " apertium-destxt             lexical transfer\n"
        + "For more help on a task, run it without parameters, like: " + bareCommand + " lt-proc\n"
        + "Note: Not all tasks shown, the above are just examples of Apertium commandt.\n"
        + "See also http://wiki.apertium.org/wiki/Lttoolbox-java");
  }

  public static void main(String[] argv) throws Exception {
    if (argv.length == 0)
      try {
        showGui();
        System.err.println("No arguments given, assuming 'gui'. Invoke with -h for help.");
        return;
      } catch (Exception e) {
        // GUI failed. Show help.
        System.err.println("No arguments given and no pair to show. Assuming '-h' for help.");
        argv = new String[]{"-h"};
      }

    // strip evt path
    String task = new File(argv[0]).getName().trim();

    String[] restOfArgs = new String[argv.length - 1]; //Not used for JDK 1.5 compatibility: Arrays.copyOfRange(argv, 1 , argv.length);
    System.arraycopy(argv, 1, restOfArgs, 0, argv.length - 1);
    if (task.startsWith("lt-proc"))
      LTProc.main(restOfArgs);
    else if (task.equals("apertium") || task.equals("apertium-j"))
      ApertiumMain.main(restOfArgs);
    else if (task.startsWith("apertium-transfer"))
      ApertiumTransfer.main(restOfArgs);
    else if (task.startsWith("apertium-interchunk"))
      ApertiumInterchunk.main(restOfArgs);
    else if (task.startsWith("apertium-postchunk"))
      ApertiumPostchunk.main(restOfArgs);
    else if (task.startsWith("apertium-tagger"))
      Tagger.main(restOfArgs);
    else if (task.startsWith("apertium-pretransfer"))
      PreTransfer.main(restOfArgs);
    else if (task.startsWith("apertium-destxt")) {
      // Hack: Here should be
      // String[] restOfArgs = Arrays.copyOfRange(argv, 0 , argv.length);
      // restOfArgs[0] = "-d";
      // TextFormatter.main(restOfArgs);
      // Hack: but in this case we can just reuse argv:
      argv[0] = "-d";
      TextFormatter.main(argv);
    } else if (task.startsWith("apertium-retxt")) {
      //TextFormatter.main(restOfArgs);
      argv[0] = "-r";
      TextFormatter.main(argv);
    } else if (task.startsWith("lt-expand"))
      LTExpand.main(restOfArgs);
    else if (task.startsWith("lt-comp"))
      LTComp.main(restOfArgs);
    else if (task.startsWith("lt-print"))
      LTPrint.main(restOfArgs);
    else if (task.startsWith("lt-trim"))
      LTTrim.main(restOfArgs);
    else if (task.startsWith("lt-validate"))
      LTValidate.main(restOfArgs);
    else if (task.startsWith("apertium-preprocess-transfer-bytecode"))
      TransferBytecode.main(restOfArgs);
    else if (task.equals("gui"))
      showGui();
    else if (task.equals("-h"))
      showHelp(task);
    else {
      ApertiumMain.main(argv);
      //System.err.println("Command not recognized: "+task); // Arrays.toString(argv).replaceAll(", ", " ")
      //showHelp(null);
    }
  }

  private static void showGui() throws Exception {
    ApertiumGUI.prepare();
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
    }
    ApertiumGUI gui = new ApertiumGUI();
    gui.setVisible(true);
  }
}
