/*
 * Copyright (C) 2010 Stephen Tigner
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
package org.apertium.pipeline;

import static org.apertium.utils.IOUtils.getStdinReader;
import static org.apertium.utils.IOUtils.getStdoutWriter;
import static org.apertium.utils.IOUtils.openInFileReader;
import static org.apertium.utils.IOUtils.openOutFileWriter;
import static org.apertium.utils.IOUtils.listFilesInDir;
import static org.apertium.utils.IOUtils.addTrailingSlash;
import java.io.PrintStream;
import java.io.Reader;

import org.apertium.Translator;
import org.apertium.formatter.FormatterRegistry;
import org.apertium.lttoolbox.Getopt;
import org.apertium.utils.IOUtils;

/**
 * @author Stephen Tigner
 *
 */
public class ApertiumMain {
  private static final boolean DEBUG = false;

  private static class CommandLineParams {
    //Directory to look for modes files in
    String dataDir = null;
    //What mode to use
    String direction;
    //Display ambiguity, defaults to false
    boolean dispAmb = false;
    //Display marks '*' for unknown words, defaults to true
    boolean dispMarks = true;
    /* List translation directions in the given data dir.
     * Done as a flag because we need to make sure the datadir
     * has been parsed from the command line before we try and read
     * the files in it.
     */
    boolean listModes = false;
    Program deformatter;
    Program reformatter;
    Reader extInput = null;
    Appendable extOutput = null;
  }

  private static void _displayHelp() {
    PrintStream p = System.err;
    /* XXX Made datadir non-optional for now to solve the issue of not really
     * having a way to configure a "default dir" for the Java runtime, at the moment.
     */
    p.println("USAGE: Apertium -d datadir [-f format] [-u] <direction> [in [out]]");
    p.println(" -d datadir     directory of linguistic data");
    p.println(" -f format      input format, only txt available at this time,");
    p.println("                and is the default format.");
    p.println(" -a             display ambiguity");
    p.println(" -u             don't display marks '*' for unknown words.");
    /* The java code doesn't support translation memories right now.
     * p.println(" -m memory.tmx use a translation memory to recycle translations");
     * p.println(" -o direction translation direction using the translation memory,");
     * p.println(" by default 'direction' is used instead");
     */
    p.println(" -l             lists the available translation directions and exits");
    p.println(" direction      typically, LANG1-LANG2, but see modes.xml in language");
    p.println("                data");
    p.println(" in             input file (stdin by default)");
    p.println(" out            output file (stdout by default)");
  }

  private static void _setFormatter(String formatterName,
      CommandLineParams clp) {
    String deFormatProgName = "apertium-des" + formatterName;
    String reFormatProgName = "apertium-re" + formatterName;

    clp.deformatter = new Program(deFormatProgName);
    clp.reformatter = new Program(reFormatProgName);
  }

  private static boolean _parseCommandLine(String[] args, CommandLineParams clp) throws Exception {

    if (args.length == 0) {
      _displayHelp();
      return false;
    }

    Getopt getopt = new Getopt("Apertium", args, "d:f:ual");

    while (true) {
      int c = getopt.getopt();
      if (c == -1) {
        break;
      }
      switch (c) {
        case 'd':
          clp.dataDir = getopt.getOptarg();
          break;
        case 'u':
          clp.dispMarks = false;
          break;
        case 'a':
          clp.dispAmb = true;
          break;
        case 'l':
          clp.listModes = true;
        case 'f':
          String formatterName = getopt.getOptarg();
          if (FormatterRegistry.isRegistered(formatterName)) {
            _setFormatter(formatterName, clp);
            break;
          }
        /* If not registered, will fall through to bottom of switch and
         * call displayHelpAndExit()
         */
        case 'h':
        default:
          _displayHelp();
          return false;
      }
    }

    if (clp.deformatter == null || clp.reformatter == null) {
      //Formatters weren't set on command-line, set default of txt
      _setFormatter("txt", clp);
    }

    //Setup external input and output
    int optIndex = getopt.getOptind();
    switch (args.length - optIndex) { //number of non-option args
            /* This avoids code duplication by allowing cases to "fall through."
       * The higher cases just add extra lines to the top of the lower cases,
       * so by allowing the code to fall through to the lower cases (instead of
       * breaking), we don't need to duplicate the same code several times.
       */
      case 3:
        clp.extOutput = openOutFileWriter(args[optIndex + 2]);
      case 2:
        clp.extInput = openInFileReader(args[optIndex + 1]);
      case 1:
        clp.direction = args[optIndex];
      default:
        break;
    }
    if (clp.extInput == null) {
      clp.extInput = getStdinReader();
    }
    if (clp.extOutput == null) {
      clp.extOutput = getStdoutWriter();
    }
    return true;
  }

  private static void _listModes(CommandLineParams clp) {
    String[] modeList = listFilesInDir(clp.dataDir + "modes/", "mode");

    if (modeList == null) {
      System.out.println("No translation directions in the specified directory.");
    } else {
      for (String mode : modeList) {
        System.out.println(mode.replaceAll("\\.mode", ""));
      }
    }
  }

  /**
   * @param args
   * @throws Exception
   */
  public static int main(String[] args) throws Exception {
    //Ensure we are using UTF-8 encoding by default
    System.setProperty("file.encoding", "UTF-8");

    CommandLineParams clp = new CommandLineParams();

    if (!_parseCommandLine(args, clp)) {
      return 1;
    }

    if (clp.listModes) {
      _listModes(clp);
    }

    if (clp.dataDir == null)
      Translator.setJarAsBase();
    else if (clp.dataDir.endsWith(".jar") || clp.dataDir.endsWith(".zip"))
      Translator.setBase(clp.dataDir);
    else if (clp.direction != null)
      Translator.setBase(addTrailingSlash(clp.dataDir) + "modes/" + clp.direction + ".mode");
    else {
      _displayHelp();
      return 1;
    }

    if (clp.direction != null)
      Translator.setMode(clp.direction);
    else if (clp.direction == null && Translator.getAvailableModes().length > 1) {
      System.out.println("Several mode files found. Run again specifying one of them:");
      for (String mode : Translator.getAvailableModes())
        System.out.println("\t" + mode);
      return 1;
    }

    //Parallel processing is buggy right now, so we disable it
    Translator.setParallelProcessingEnabled(false);

    Translator.setDisplayMarks(clp.dispMarks);
    Translator.setDisplayAmbiguity(clp.dispAmb);
    Translator.translate(clp.extInput, clp.extOutput, clp.deformatter, clp.reformatter);
    IOUtils.flush(clp.extOutput);
    IOUtils.close(clp.extOutput);
    return 0;
  }
}
