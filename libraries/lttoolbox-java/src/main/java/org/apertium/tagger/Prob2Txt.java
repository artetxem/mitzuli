/*
 * Copyright (C) 2004-2006 Felipe Sánchez-Martínez
 * Copyright (C) 2006 Universitat d'Alacant
 * Copyright 2010 Jimmy O'Regan
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

import java.util.Set;
import org.apertium.lttoolbox.Getopt;
import java.io.*;

class LocalGetOpt extends Getopt {
  public LocalGetOpt(String[] argv, String string) {
    super("prob2txt", argv, string);
  }

  int getNextOption() {
    return getopt();
  }
}

/**
 *
 * @author jimregan
 */
public class Prob2Txt {
  static TaggerData td = new TaggerData();
  Integer eos;
  private static String program = "prob2txt";

  static void help() {
    System.err.println("HMM parameters are writen in text format");
    System.err.println();
    System.err.println("Usage: ");
    System.err.println(program + " --file file.prob [--human]");
    System.err.println("Arguments: ");
    System.err.println("-f: To specify the file with the HMM parameter to process");
  }

  static void print_A(boolean human_readable) {
    System.out.println("TRANSITION MATRIX (A)");
    System.out.println("------------------------------");
    for (int i = 0; i < td.getN(); i++)
      for (int j = 0; j < td.getN(); j++) {
        if (human_readable) {
          System.out.print("A[" + td.getArrayTags().get(i) + "][" + td.getArrayTags().get(j) + "] = ");
        } else {
          System.out.print("A[" + i + "][" + j + "] = ");
        }
        System.out.print(td.getA()[i][j] + "\n");
      }
  }

  static void print_B(boolean human_readable) {
    System.out.println("EMISSION MATRIX (B)");
    System.out.println("------------------------------");
    for (int i = 0; i < td.getN(); i++) {
      for (int k = 0; k < td.getM(); k++) {
        if (td.getOutput().get(k).contains(i)) {
          if (human_readable) {
            Set<Integer> tags = td.getOutput().get(k);
            String str = "";

            for (Integer it : tags) {
              if (str.length() > 0) {
                str += ", ";
              }
              str += td.getArrayTags().get(i);
            }
            System.out.print("B[" + td.getArrayTags().get(i) + "][");
            System.out.print(str + "] = ");
          } else {
            System.out.print("B[" + i + "][" + k + "] = ");
          }
          System.out.println(td.getB()[i][k]);
        }
      }
    }
  }

  public static void main(String[] argv) throws IOException {
    if (argv.length == 0) { // for easier testing
      argv = new String[]{"-f", "testdata/tagger/en-es.prob"};
    }
    String file = "";
    int optind = 0;
    boolean human_readable = false;

    System.err.print("Command line: ");
    for (int i = 0; i < argv.length; i++)
      System.err.print(argv[i] + " ");
    System.err.println();

    MyGetOpt getopt = new MyGetOpt(argv, "fu");
    int c = getopt.getNextOption();

    boolean cont = true;
    while (cont) {
      switch (c) {
        case 'f':
          file = argv[getopt.getOptind()];
          cont = false;
          break;

        case 'u':
          human_readable = true;
          break;

        default:
          help();
          cont = false;
          return;
      }
    }

    if ("".equals(file)) {
      System.err.println("Error: You did not provide a file (.prob). Use --file to do that");
      help();
      return;
    }

    System.err.println("File: " + file);
    InputStream fin = new FileInputStream(file);
    BufferedInputStream buf = new BufferedInputStream(fin);

    System.err.println("Reading data");
    td.read(buf);
    buf.close();

    System.err.println("Print A");
    print_A(human_readable);
    print_B(human_readable);
  }
}
