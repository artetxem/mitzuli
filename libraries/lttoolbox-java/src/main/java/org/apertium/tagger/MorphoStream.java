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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apertium.lttoolbox.Alphabet;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author jimregan
 */
/** Class MorphoStream.
 * This class processes the FST, and
 * builds the TaggerWord objects managed by the tagger
 */
public class MorphoStream {
  private boolean foundEOF;
  //Normal debugging (-d parameter)
  private boolean debug = false;
  private String last_string_tag;
  private int ca_any_char;
  private int ca_any_tag;
  private int ca_kignorar;
  private int ca_kbarra;
  private int ca_kdollar;
  private int ca_kbegin;
  private int ca_kmot;
  private int ca_kmas;
  private int ca_kunknown;
  private int ca_tag_keof;
  private int ca_tag_kundef;
  private ArrayList<TaggerWord> vwords;
  private InputStream input;
  /**
   * This exists to fix the character encoding issues that crop up by reading
   * directly byte-by-byte from the InputStream. ALL reading from the input stream
   * should be done through this reader instead of the InputStream directly.
   */
  private Reader inputReader;
  private MatchExe me;
  private TaggerData td;
  private Alphabet alphabet;
  private MatchState ms;
  private boolean end_of_file;
  private boolean null_flush;
  private Map<String, Integer> tag_index;
  private ConstantManager constants;
  //Deep-level dev debugging
  private boolean DEBUG = false;

//    MorphoStream() {
//    }
  /**
   * Constructor
   *
   * @param ftxt the input stream.
   */
  MorphoStream(Reader ftxt, boolean d, TaggerData t) throws UnsupportedEncodingException {
    // this();
    foundEOF = false;
    debug = d;
    this.td = t;
    alphabet = td.getPatternList().getAlphabet();
    ca_any_char = alphabet.cast(PatternList.ANY_CHAR);
    ca_any_tag = alphabet.cast(PatternList.ANY_TAG);

    if (DEBUG) {
      System.out.println("ca_any_char = " + ca_any_char);
      System.out.println("ca_any_tag = " + ca_any_tag);
    }

    null_flush = false;
    this.inputReader = ftxt;
    this.end_of_file = false;
    this.me = this.td.getPatternList().newMatchExe();
    ms = new MatchState(me);

    this.constants = td.getConstants();
    this.ca_kignorar = constants.getConstant("kIGNORAR");
    this.ca_kbarra = constants.getConstant("kBARRA");
    this.ca_kdollar = constants.getConstant("kDOLLAR");
    this.ca_kbegin = constants.getConstant("kBEGIN");
    this.ca_kmot = constants.getConstant("kMOT");
    this.ca_kmas = constants.getConstant("kMAS");
    this.ca_kunknown = constants.getConstant("kUNKNOWN");

    this.tag_index = td.getTagIndex();
    this.ca_tag_keof = tag_index.get("TAG_kEOF");
    this.ca_tag_kundef = tag_index.get("TAG_kUNDEF");
    this.vwords = new ArrayList<TaggerWord>();
  }

  /**
   * Get next word in the input stream.
   * Expected is an input word, like ^can/can<n><sg>/can<vaux><pres>$
   *
   * @return The next word in the input stream
   */
  TaggerWord get_next_word() throws IOException {
    if (DEBUG) {
      System.out.println("MorphoStream.getNextWord -- vwords: " + vwords);
    }

    if (vwords.size() != 0) {
      TaggerWord word = vwords.get(0);
      vwords.remove(0);

      if (word.isAmbiguous()) {
        List<String> ref = td.getDiscardRules();
        for (int i = 0; i < ref.size(); i++) {
          word.discardOnAmbiguity(ref.get(i));
        }
      }

      //if (DEBUG) System.out.println("get_next_word " + word.get_superficial_form()+" "+ word.get_string_tags());
      if (DEBUG)
        System.out.println("get_next_word " + word.get_superficial_form());
      return word;
    }

    int symbol = inputReader.read();
    /* The mark() function takes an int argument that is the number of bytes
     * that can be read before the mark is invalidated. Supplying an argument of 0
     * is pretty much useless, in other words. ^^; That really should be 1, because
     * we're only going to try and read one byte before calling reset().
     *
     * However, that's a moot point though, because of a line in the documentation
     * for reset(): "The method reset for class InputStream does nothing except throw
     * an IOException." In other words, expecting the reset() method to work on a
     * generic InputStream object is not type-safe, and will likely fail. Even though
     * there's a "markSupported()" method, the program logic at that point depends on
     * being able to mark and reset the pointer like that.
     *
     * Thus the while() loop below was slightly changed to remove the need to reset
     * the read pointer.
     *
     * However, the issue is with trying to duplicate the behavior of the code in
     * the C++ version, which uses feof() to check for end of file.
     *
     * The issue is that all it does is check to see if the eof flag has been set
     * on the stream yet, which would only be set once a read call that reaches eof
     * has been made. Which means that the next read call would set eof, but feof
     * would still return false because that hasn't happened yet.
     *
     * Testing for end_of_file && symbol == -1 does keep it from exiting prematurely,
     * but sticks us in an infinit loop, so an additional test needs to be added. The
     * member variable "foundEOF" wasn't actually being used for anything, so I
     * co-opted it for this use.
     */
    if (end_of_file || (symbol == -1 && foundEOF)) {
      if (DEBUG)
        System.out.println("MorphoStream.get_next_word: EOF reached, returning NULL.");
      return null;
    }

    /* Set this after that test above so that it doesn't exit prematurely, but we
     * aren't stuck in an infinite loop, either.
     */
    if (symbol == -1) {
      foundEOF = true;
    }

    // no word in the buffer, so read from input
    int ivwords = 0;
    vwords.add(new TaggerWord());

    while (true) {

      //int symbol = input.read();
      if (symbol == -1 || (null_flush && symbol == (int) '\0')) {
        this.end_of_file = true;
        if (DEBUG) {
          System.out.println("End of file add_tag in get_next_word()");
        }
        vwords.get(ivwords).add_tag(ca_tag_keof, "", td.getPreferRules());
        // word read, use above code to return it
        return get_next_word();
      }
      if (symbol == (int) '^') {
        readRestOfWord(ivwords);
        // word read, use above code to return it
        return get_next_word();
      } else {
        String str = "";
        if (symbol == (int) '\\') {
          symbol = inputReader.read();
          str += '\\';
          str += (char) symbol;
          symbol = (int) '\\';
        } else {
          str += (char) symbol;

        }
        while (symbol != (int) '^') {
          symbol = inputReader.read();
          if (symbol == -1 || (null_flush && symbol == '\0')) {
            end_of_file = true;
            vwords.get(ivwords).add_ignored_string(str);
            vwords.get(ivwords).add_tag(ca_tag_keof, "", td.getPreferRules());
            // word read, use above code to return it
            return get_next_word();
          } else if (symbol == (int) '\\') {
            str += '\\';
            symbol = inputReader.read();
            if (symbol == -1 || (null_flush && symbol == '\0')) {
              end_of_file = true;
              vwords.get(ivwords).add_ignored_string(str);
              vwords.get(ivwords).add_tag(ca_tag_keof, "", td.getPreferRules());
              // word read, use above code to return it
              return get_next_word();
            }
            str += (char) symbol;
            symbol = (int) '\\';
          } else if (symbol == (int) '^') {
            if (str.length() > 0) {
              vwords.get(ivwords).add_ignored_string(str);
            }
            readRestOfWord(ivwords);
            return get_next_word();
          } else {
            str += (char) symbol;
          }
        }

      }
      /* Moved this down to here, to allow for read before initial run of loop.
       * Will effectively still be run in the same order as before, just won't be
       * called at the beginning of the first iteration of the loop.
       */
      symbol = inputReader.read();
    }

  }

  /**
   * Reads rest of an input word, like ^can/can<n><sg>/can<vaux><pres>$.
   * The first ^ has been processed, so something like can/can<n><sg>/can<vaux><pres>$. is expected now.
   */
  void readRestOfWord(int ivwords) throws IOException {
    String str = "";
    while (true) {
      int symbol = inputReader.read();
      if (symbol == -1 || (null_flush && symbol == (int) '\0')) {
        end_of_file = true;
        if (str.length() > 0) {
          vwords.get(ivwords).add_ignored_string(str);

          System.err.println("Warning (internal): kIGNORE was returned while reading a word");
          System.err.println("Word being read: " + vwords.get(ivwords).get_superficial_form());
          System.err.println("Debug: " + str);
        }
        vwords.get(ivwords).add_tag(ca_tag_keof, "", td.getPreferRules());
        return;
      } else if (symbol == (int) '\\') {
        symbol = inputReader.read();
        str += '\\';
        str += (char) symbol;
      } else if (symbol == (int) '/') {
        vwords.get(ivwords).set_superficial_form(str);
        str = "";
        break;
      } else if (symbol == (int) '$') {
        vwords.get(ivwords).set_superficial_form(str);
        vwords.get(ivwords).add_ignored_string("$");
        break;
      } else {
        str += (char) symbol;
      }
    }

    while (true) {
      int symbol = inputReader.read();
      if (symbol == -1 || (null_flush && symbol == '\0')) {
        end_of_file = true;
        if (str.length() > 0) {
          vwords.get(ivwords).add_ignored_string(str);
          System.err.println("Warning (internal): kIGNORE was returned while reading a word");
          System.err.println("Word being read: " + vwords.get(ivwords).get_superficial_form());
          System.err.println("Debug: " + str);
        }
        vwords.get(ivwords).add_tag(ca_tag_keof, "", td.getPreferRules());
        return;
      } else if (symbol == (int) '\\') {
        symbol = inputReader.read();
        str += '\\';
        str += (char) symbol;
        symbol = '\\';
      } else if (symbol == (int) '/') {
        lrlmClassify(str, ivwords);
        str = "";
        ivwords = 0;
        continue;
      } else if (symbol == (int) '$') {
        if (str.charAt(0) != '*') {
          lrlmClassify(str, ivwords);
        }
        return;
      } else {
        str += (char) symbol;
      }

    }
  }

  /**
   * lrlm = left-right longest match (parse from left to right, matching the longest you can, like "greedy" matching)
   * We need to find the coarse tag categories
   *
   * @param str An input word, somethink like "can<n><sg>" (Jacob thinks)
   * @param ivwords
   */
  void lrlmClassify(String str, int ivwords) {
    if (DEBUG) {
      System.out.println("Starting lrlmClassify -- str: >>" + str + "<<");
      System.out.println("MorphoStream.lrlmClassify -- vwords: " + vwords);
    }
    int floor = 0;
    int last_type = -1; // coarse tag ID.
    int last_pos = 0;
    TaggerWord tw;

    ms.init(me.getInitial());
    for (int i = 0, limit = str.length(); i != limit; i++) {
      if (str.charAt(i) != '<') {
        if (str.charAt(i) == '+') {
          int val = ms.classifyFinals();
          if (val != -1) {
            last_pos = i - 1;
            last_type = val;
          }
        }
        ms.step(str.toLowerCase().charAt(i), ca_any_char);
      } else {
        String tag = "";
        for (int j = i + 1; j != limit; j++) {
          if (str.charAt(j) == '\\') {
            j++;
          } else if (str.charAt(j) == '>') {
            tag = str.substring(i, j + 1);

            if (DEBUG) {
              System.out.println("tag = " + tag);
            }
            i = j;
            break;
          }
        }

        /* The C++ version has the () operator overloaded for Alphabet, when
         * passing in a single string argument. Java version doesn't.
         */
        int symbol = alphabet.cast(tag);
        if (symbol != 0) {
          ms.step(symbol, ca_any_tag);
        } else {
          ms.step(ca_any_tag);
        }
      }
      if (ms.size() == 0) {
        if (last_pos != floor) {
          if (DEBUG) {
            System.out.println("MorphoStream.lrlmclassify -- floor: "
                + floor);
            System.out.println("MorphoStream.lrlmclassify -- last_pos: "
                + last_pos);
          }
          vwords.get(ivwords).add_tag(last_type,
              str.substring(floor, last_pos + 1),
              td.getPreferRules());
          if (str.charAt(last_pos + 1) == '+' && last_pos + 1 < limit) {
            if (DEBUG) {
              System.out.println(
                  "MorphoStream.lrlmClassify -- plus cut, word added: "
                  + str.substring(floor, last_pos + 1));
            }
            floor = last_pos + 1;
            last_pos = floor;
            vwords.get(ivwords).set_plus_cut(true);
            if (vwords.size() <= (ivwords + 1)) {
              vwords.add(new TaggerWord(true));
            }
            ivwords++;
            ms.init(me.getInitial());
          }
          i = floor++;
          if (DEBUG) {
            System.out.println(
                "MorphoStream.lrlmClassify -- floor post-increment assignment to i:");
            System.out.println("-- i: " + i + ", floor: " + floor);
          }
        } else {
          if (debug) {
            System.err.println("Warning: There is no coarse tag for the fine tag '" + str.substring(floor) + "'");
            System.err.println("         This is because of an incomplete tagset definition or a dictionary error");
          }
          tw = vwords.get(ivwords);
          tw.add_tag(ca_tag_kundef, str.substring(floor), td.getPreferRules());
          vwords.set(ivwords, tw);
          return;
        }
      } else if (i == (limit - 1)) {
        if (ms.classifyFinals() == -1) {
          if (last_pos != floor) {
            vwords.get(ivwords).add_tag(last_type,
                str.substring(floor, last_pos), td.getPreferRules());
            if (str.charAt(last_pos + 1) == '+' && last_pos + 1 < limit) {
              floor = last_pos + 1;
              last_pos = floor;
              vwords.get(ivwords).set_plus_cut(true);
              if (vwords.size() <= (ivwords + 1)) {
                vwords.add(new TaggerWord(true));
              }
              ivwords++;
              ms.init(me.getInitial());
            }
            i = floor++;
          } else {
            if (debug) {
              System.err.println("Warning: There is no coarse tag for the fine tag '" + str.substring(floor) + "'");
              System.err.println("         This is because of an incomplete tageset definition or a dictionary error");
            }
            vwords.get(ivwords).add_tag(ca_tag_kundef,
                str.substring(floor), td.getPreferRules());
            return;
          }
        }
      }
    }

    int val = ms.classifyFinals();
    if (val == -1) {
      val = ca_tag_kundef;
      if (debug) {
        System.err.println("Warning: There is no coarse tag for the fine tag '" + str.substring(floor) + "'");
        System.err.println("         This is because of an incomplete tagset definition or a dictionary error");
      }
    }

    // this line was missing -- no, it was just misplaced
    //   vwords[ivwords]->add_tag(val, str.substr(floor), td->getPreferRules());
    tw = vwords.get(ivwords);
    if (DEBUG) {
      System.out.println("add_tag called at the end of lrlmClassify.");
      System.out.println("end of lrlmClassify -- floor: " + floor);
    }
    if (DEBUG) {
      System.out.println("MorphoStream.lrlmClassify before last add_tag -- vwords: " + vwords);
    }
    tw.add_tag(val, str.substring(floor), td.getPreferRules());
    if (DEBUG) {
      System.out.println("MorphoStream.lrlmClassify after last add_tag -- vwords: " + vwords);
    }
  }

  /**
   * Set up the flag to detect '\0' characters
   *
   * @param nf the null_flush value
   */
  void setNullFlush(boolean nf) {
    null_flush = nf;
  }

  /**
   * Return true if the last reading is end of file of '\0' when null_flush
   * is true
   *
   * @returns the value of end_of_file
   */
  boolean getEndOfFile() {
    return end_of_file;
  }

  /**
   * Sets a new value for the end_of_file_flag
   *
   * @param eof the new value for end_of_file
   */
  void setEndOfFile(boolean eof) {
    end_of_file = eof;
  }
}
