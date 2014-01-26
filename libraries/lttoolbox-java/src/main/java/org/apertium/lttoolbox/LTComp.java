package org.apertium.lttoolbox;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
import org.apertium.CommandLineInterface;
import org.apertium.lttoolbox.compile.Compile;
import java.io.*;

/**
 * XML dictionaries to binaries compiler
 *
 * @author Raah
 */
public class LTComp {
  /**
   * Anormal termination function
   * prints the usage
   *
   * @param name
   */
  static void endProgram(String name) {
    if (name != null) {
      System.out.println(" v" + CommandLineInterface.PACKAGE_VERSION + ": build a letter transducer from a dictionary\n"
          + "USAGE: " + name + " lr | rl dictionary_file output_file [acx_file]\n"
          + "Modes:\n"
          + "  lr:     left-to-right compilation\n"
          + "  rl:     right-to-left compilation\n");
    }
  }

  /**
   * Compiles an XML dictionary into a binary file
   *
   * @param argv the command line arguments
   * @throws java.io.IOException
   */
  public static void main(String[] argv) throws IOException {
    final int argc = argv.length;
    if (argc != 3 && argc != 4) {
      endProgram("LTComp");
      return;
    }
    String opc = argv[0];
    Compile c = new Compile();

    if (opc.equals("lr")) {
      if (argc == 4) {
        c.parseACX(argv[3], Compile.COMPILER_RESTRICTION_LR_VAL);
      }
      c.parse(argv[1], Compile.COMPILER_RESTRICTION_LR_VAL);
    } else if (opc.equals("rl")) {
      c.parse(argv[1], Compile.COMPILER_RESTRICTION_RL_VAL);
    } else {
      endProgram("LTComp");
      return;
    }
    c.write(argv[2]);
  }
}
