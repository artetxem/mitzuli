/*
 * Copyright (C) 2005 Universitat d'Alacant / Universidad de Alicante
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
package org.apertium.tagger;

import static org.apertium.utils.IOUtils.getStdinReader;
import static org.apertium.utils.IOUtils.getStdoutWriter;
import static org.apertium.utils.IOUtils.openInFileReader;
import static org.apertium.utils.IOUtils.openInFileStream;
import static org.apertium.utils.IOUtils.openOutFileWriter;

import java.util.List;
import java.util.ArrayList;
import java.io.*;
import java.util.HashMap;

import org.apertium.lttoolbox.Getopt;
import org.apertium.utils.IOUtils;

// Use GNU Getopt
class MyGetOpt extends Getopt {
  public MyGetOpt(String[] argv, String string) {
    super("apertium-tagger", argv, string);
  }

  int getNextOption() {
    return getopt();
  }
}

/**
 *
 * @author jimregan
 */
public class Tagger {
  boolean showSF;
  boolean null_flush;
  private static final int UNKNOWN_MODE = 0;
  private static final int TRAIN_MODE = 1;
  private static final int TAGGER_MODE = 2;
  private static final int RETRAIN_MODE = 3;
  private static final int TAGGER_SUPERVISED_MODE = 4;
  private static final int TRAIN_SUPERVISED_MODE = 5;
  private static final int RETRAIN_SUPERVISED_MODE = 6;
  private static final int TAGGER_EVAL_MODE = 7;
  private static final int TAGGER_FIRST_MODE = 8;
  static boolean debug;
  List<String> filenames;
  private static String name;
  //Low-level dev debugging
  private static boolean DEBUG = false;
  private static HashMap<String, TaggerData> cache = new HashMap<String, TaggerData>();
  private static boolean cacheEnabled = false;

  public static void setCacheEnabled(boolean enabled) {
    cacheEnabled = enabled;
    if (!enabled)
      clearCache();
  }

  public static void clearCache() {
    cache.clear();
  }

  Tagger() {
    debug = false;
    showSF = false;
    null_flush = false;
    filenames = new ArrayList<String>();
    name = new ClassName().getName();
  }

  void setShowSF(boolean val) {
    showSF = val;
  }

  boolean getShowSF() {
    return showSF;
  }

  int getMode(String[] argv) {
    int mode = UNKNOWN_MODE;

    MyGetOpt getopt = new MyGetOpt(argv, "mdtsrgpefhz");

    /* Reset TaggerWord.generate_marks to false in case a previous invocation set it
     * to true. This shows up when doing tests, as the static variables retain their
     * states between tests. This also may show up when in pipeline mode, if tagger
     * is called more than once for some reason.
     */
    TaggerWord.setGenerateMarks(false);

    while (true) {
      try {
        int c = getopt.getNextOption();
        if (c == -1) {
          break;
        }

        switch (c) {

          case 'd':
            debug = true;
            break;

          case 'e':
            if (mode == TAGGER_MODE) {
              mode = TAGGER_EVAL_MODE;
            } else {
              System.err.println("Error: -e optional argument should only appear after -t argument");
              help();
            }
            break;

          case 'f':
            if (mode == TAGGER_MODE) {
              mode = TAGGER_FIRST_MODE;
            } else {
              System.err.println("Error: -f optional argument should only appear after -t argument");
              help();
            }
            break;

          case 'g':
            mode = TAGGER_MODE;
            break;

          case 'h':
            help();
            break;

          case 'm':
            TaggerWord.setGenerateMarks(true);
            break;

          case 'p':
            setShowSF(true);
            break;

          case 'r':
          case 's':
          case 't':
            throw new IllegalArgumentException("Training not supported");

          case 'z':
            null_flush = true;
            break;

          default:
            help();
            break;
        }

        if (mode == UNKNOWN_MODE) {
          System.err.println("Error: Arguments missing");
          help();
        }


      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (DEBUG) {
      System.out.println("Tagger.getMode -- argv.length: " + argv.length
          + ", getopt.getOptind: " + getopt.getOptind()
          + ", mode: " + mode);
    }
    switch (argv.length - getopt.getOptind()) {
      case 6:
        if (mode != TRAIN_SUPERVISED_MODE) {
          help();
        }
        break;

      case 4:
        if (mode != TRAIN_MODE) {
          help();
        }
        break;

      case 3:
        if ((mode != TAGGER_MODE) && (mode != TAGGER_FIRST_MODE)) {
          help();
        }
        break;

      case 2:
        if ((mode != RETRAIN_MODE) && (mode != TAGGER_MODE)) {
          help();
        }
        break;

      case 1:
        if ((mode != TAGGER_MODE) && (mode != TAGGER_FIRST_MODE)) {
          help();
        }
        break;

      default:
        help();
        break;
    }

    for (int i = getopt.getOptind(); i != argv.length; i++) {
      filenames.add(argv[i]);
    }

    return mode;
  }

  static void help() {


    System.out.println(name + ": HMM part-of-speech tagging and training program");
    System.out.println("GENERIC USAGE: " + name + "[-d] <OPTION>=[PARAM] [FILES]");
    System.out.println("USAGE: " + name + "[-d] -t=n DIC CRP TSX TAGGER_DATA");
    System.out.println("       " + name + "[-d] -s=n DIC CRP TSX TAGGER_DATA HTAG UNTAG");
    System.out.println("       " + name + "[-d] -r=n CRP TAGGER_DATA");
    System.out.println("       " + name + "[-d] -g [-f] TAGGER_DATA [INPUT [OUTPUT]] \n");
    System.out.println("Where OPTIONS are:");
    System.out.println("  -t, --train=n:       performs n iterations of the Baum-Welch training");
    System.out.println("                       algorithm (unsupervised)");
    System.out.println("  -s, --supervised=n:  initializes parameters against a hand-tagged text");
    System.out.println("                       (supervised), and trains it with n iterations");
    System.out.println("  -r, --retrain=n:     retrains the model with n aditional Baum-Welch");
    System.out.println("                       iterations (unsupervised)");
    System.out.println("  -g, --tagger:        tags input text by means of Viterbi algorithm");
    System.out.println("  -p, --show-superficial: ");
    System.out.println("                       show superficial forms in the output stream");
    System.out.println("  -f, --first:         used in conjuntion with -g (--tagger) makes the tagger");
    System.out.println("                       give all lexical forms of each word, with the chosen");
    System.out.println("                       one in the first place (after the lemma)");
    System.out.println("  -d, --debug:         print error mesages when tagging input text");
    System.out.println("  -m, --mark:          generate marks of solved ambiguities");
    System.out.println("  -z, --null-flush:    flush output stream when reading '\\0' characters \n");
    System.out.println("And FILES are:");
    System.out.println("  DIC:         full expanded dictionary file");
    System.out.println("  CRP:         training text corpus file");
    System.out.println("  TSX:         tagger specification file, in XML format");
    System.out.println("  TAGGER_DATA: tagger data file, built in the training and used while");
    System.out.println("               tagging");
    System.out.println("  HTAG:        hand-tagged text corpus");
    System.out.println("  UNTAG:       untagged text corpus, morphological analysis of HTAG");
    System.out.println("               corpus to use both jointly with -s option");
    System.out.println("  INPUT:       input file, stdin by default");
    System.out.println(" OUTPUT:      output file, stdout by default");


  }
  /* Class to get name of program since arg[0] in java dosen't provide program */

  static class ClassName {
    String getName() {
      return getClass().getDeclaringClass().getSimpleName();
    }
  }

  public static void taggerDispatch(String[] args) {
    taggerDispatch(args, null, null);
  }

  public static void taggerDispatch(String[] args, Reader input, Appendable output) {
    Tagger t = new Tagger();
    int mode = t.getMode(args);
    switch (mode) {
      case TRAIN_MODE:
        //train();
        break;

      case TRAIN_SUPERVISED_MODE:
        //trainSupervised();
        break;

      case RETRAIN_MODE:
        //retrain();
        break;

      case TAGGER_MODE:
        try {
          if (input != null || output != null) {
            t.tagger(input, output);
          } else {
            t.tagger();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;

      case TAGGER_FIRST_MODE:
        try {
          if (input != null || output != null) {
            t.tagger(true, input, output);
          } else {
            t.tagger(true);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        break;

      default:
        System.err.println("Error: unknown mode");
        help();
        break;
    }
  }

  public static void main(String[] argv) {
    taggerDispatch(argv);
  }

  void tagger(boolean mode_first, Reader input, Appendable output)
      throws IOException {

    if (IOUtils.timing != null)
      IOUtils.timing.log("");

    TaggerData td = cache.get(filenames.get(0));
    if (td == null) {
      InputStream ftdata = openInFileStream(filenames.get(0));
      td = new TaggerData();
      td.read(ftdata);
      ftdata.close();
      if (cacheEnabled)
        cache.put(filenames.get(0), td);
      if (IOUtils.timing != null)
        IOUtils.timing.log("Load tagger " + filenames.get(0));
    }

    HMM hmm = new HMM(td);

    hmm.set_show_sf(showSF);
    hmm.setNullFlush(null_flush);

    Reader sysInReader = getStdinReader();
    Appendable sysOutWriter = getStdoutWriter();

    //If input or output provided, ignore input/output files on command line
    if (input != null || output != null) {
      if (input == null) {
        input = sysInReader;
      }
      if (output == null) {
        output = sysOutWriter;
      }
      hmm.tagger(input, output, mode_first);
    } else if (filenames.size() == 1) {
      hmm.tagger(sysInReader, sysOutWriter, mode_first);

    } else {
      Reader fReader = openInFileReader(filenames.get(1));
      if (filenames.size() == 2) {
        hmm.tagger(fReader, sysOutWriter, mode_first);

      } else {
        Writer fWriter = openOutFileWriter(filenames.get(2));
        hmm.tagger(fReader, fWriter, mode_first);
        fWriter.close();
      }
      fReader.close();

    }
    if (IOUtils.timing != null)
      IOUtils.timing.log("Process tagger " + filenames.get(0));

  }

  void tagger() throws IOException {
    tagger(false);
  }

  void tagger(boolean mode_first) throws IOException {
    tagger(mode_first, null, null);
  }

  void tagger(Reader input, Appendable output) throws IOException {
    tagger(false, input, output);
  }
}
