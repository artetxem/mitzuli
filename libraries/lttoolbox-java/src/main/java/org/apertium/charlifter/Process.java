/*
 # This file is part of Charlifter.
 # Copyright 2008-2009 Kevin P. Scannell <kscanne at gmail dot com>
 #
 #     Charlifter is free software: you can redistribute it and/or modify
 #     it under the terms of the GNU General Public License as published by
 #     the Free Software Foundation, either version 3 of the License, or
 #     (at your option) any later version.
 #
 #     Charlifter is distributed in the hope that it will be useful,
 #     but WITHOUT ANY WARRANTY; without even the implied warranty of
 #     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 #     GNU General Public License for more details.
 #
 #     You should have received a copy of the GNU General Public License
 #     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.apertium.charlifter;

import java.util.List;
import java.util.LinkedList;

/**
 *
 * @author jimregan
 */
public class Process {
  private double[][] features;
  public static String diacritics = "\\p{M}|[\\x{A8}\\x{B4}\\x{B8}\\x{02B9}-\\x{02DD}]";
  private String ints;
  private String extrabd;
  private static boolean okina;

  public static boolean isOkina() {
    return okina;
  }

  public static void setOkina(boolean b) {
    okina = true;
  }

  void init() {
    this.ints = "´'’-";
    this.extrabd = "a";
    setOkina(false);
  }

  void init(String lang) {
    if (lang.equals("sm") || lang.equals("haw")) {
      this.ints = "-";
      this.extrabd = "'’‘";
      setOkina(true);
    } else {
      init();
    }
  }

  void process_char(String c, List<String> context) {
    if (c.matches("[\\x{200C}\\x{200D}]|\\p{L}|\\p{M}|[" + ints + "]|[" + extrabd + "]")) {
      String tmp = context.get(3) + c;
      context.set(3, tmp);
    } else {
      if (!"".equals(context.get(3))) {
      }
    }
  }

  double consider_candidate(String todo, String pos, String cand) {
    double foo = 0.0;

    return foo;
  }

  String dictionary_lookup(String prev, String cur, String next, boolean context_ok) {
    String ret = "";

    return ret;
  }
}
