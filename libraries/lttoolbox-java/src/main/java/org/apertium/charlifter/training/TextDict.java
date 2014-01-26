/*
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
package org.apertium.charlifter.training;

import org.apertium.charlifter.Data;
import org.apertium.charlifter.Process;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 *
 * @author jimregan
 */
public class TextDict {
  /**
   * hash of asciifications of xx-clean.txt words. Not saved for -r or -e;
   * just used to avoid keeping "prettyclean" or corpus words for dictionary
   * lookup if their asciification coincides with asciification of a truly
   * clean word, e.g. ac\'u, etc.
   */
  HashMap<String, Integer> clean;
  /**
   * redundant info, used for faster lookup (no toascii req)
   */
  HashMap<Character, Integer> ambigchars;
  TrainData td;

  void TextDict() {
    clean = new HashMap<String, Integer>();
    ambigchars = new HashMap<Character, Integer>();
    td = new TrainData();
  }

  void read_clean_dict(String file) {
    String[] words;
    String lwr = "";
    try {
      words = Wordlist.read(file);
      for (String word : words) {
        lwr = word.toLowerCase();
        String asc = Asciify.toascii(lwr);
        if (Process.isOkina() && asc.contains("'")) {
          String stripped = asc;
          stripped.replaceAll("'", "");
          clean_increment(stripped);
          tableref_increment(stripped, lwr);
        }
        clean_increment(asc);
        tableref_increment(asc, lwr);
      }
      for (int i = 0; i < lwr.length() - 1; i++) {
        String next = "";
        next += lwr.charAt(i + 1);
        if (next.matches("[" + Process.diacritics + "]")) {
          String cur = "";
          cur += lwr.charAt(i);
          if (!cur.matches("^[A-Za-z]$") && !cur.equals(Asciify.toascii(cur))) {
            String asc = Asciify.toascii(cur);
            charsref_increment(cur, asc);
            ambigref_increment(cur);
            ambigref_increment(asc);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void clean_increment(String stripped) {
    if (!clean.containsKey(stripped)) {
      clean.put(stripped, 1);
    } else {
      int num = clean.get(stripped) + 1;
      clean.put(stripped, num);
    }
  }

  private void tableref_increment(String stripped, String lwr) {
    HashMap<String, Integer> e = new HashMap<String, Integer>();
    if (td.tableref.containsKey(stripped)) {
      e = td.tableref.get(stripped);
      if (e.containsKey(lwr)) {
        int i = e.get(lwr) + 1;
        e.put(lwr, i);
      } else {
        e.put(lwr, 1);
      }
    } else {
      e.put(lwr, 1);
    }
    td.tableref.put(stripped, e);
  }

  private void charsref_increment(String character, String ascii) {
    Character chr = character.charAt(0);
    Character asc = ascii.charAt(0);
    HashMap<Character, Integer> e = td.charsref.get(asc);
    if (e == null)
      td.charsref.put(asc, e = new HashMap<Character, Integer>());
    Integer i1 = e.get(chr);
    e.put(chr, i1 == null ? 1 : i1 + 1);
    Integer i2 = e.get(asc);
    e.put(asc, i2 == null ? 1 : i2 + 1);
  }

  private void ambigref_increment(String character) {
    Character c = character.charAt(0);
    if (!ambigchars.containsKey(c)) {
      ambigchars.put(c, 1);
    } else {
      int num = ambigchars.get(c) + 1;
      ambigchars.put(c, num);
    }
  }
}
