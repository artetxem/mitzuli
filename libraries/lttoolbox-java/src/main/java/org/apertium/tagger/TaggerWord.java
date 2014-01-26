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

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import org.apertium.transfer.ApertiumRE;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Class TaggerWord.
 * It stores the superficial form and all possible tags that it can receive.
 * It has the fine tags delivered by the morphological analyzer and the coarse
 * ones used by the PoS tagger.
 *
 * @author jimregan
 */
public class TaggerWord {
  private String superficial_form;
  private Set<Integer> tags;
  private Map<Integer, String> lexical_forms;
  private String ignored_string;
  /**
   * Flag to distinguish the way in which the word was ended.
   * If it was done by '$' its value should be false
   * If it was done by '+' its value should be true
   */
  private boolean plus_cut;
  /**
   * Flag to distinguish the way in which the
   * previous word was ended. It has the same
   * plus_cut meaning
   */
  private boolean previous_plus_cut;
  /**
   * Show the superficial form in the output
   */
  private boolean show_sf;
  //TODO: check and make sure these really need to be static variables
  /**
   * Will be set to true if the -m (mark disambiguated words) option was used
   * on the command-line.
   */
  private static boolean generate_marks = false;
  public static ArrayList<String> array_tags;
  public static boolean show_ignored_string = true;
  private static Map<String, ApertiumRE> patterns = new LinkedHashMap<String, ApertiumRE>();
  private static boolean DEBUG = false;

  public TaggerWord(boolean prev_plus_cut) {
    ignored_string = "";
    plus_cut = false;
    previous_plus_cut = prev_plus_cut;
    tags = new LinkedHashSet<Integer>();
    lexical_forms = new LinkedHashMap<Integer, String>();
  }

  public TaggerWord() {
    this(false);
  }

  /**
   * Sets the static generate_marks flag.
   * Keep in mind when doing testing that the state of this flag may be retained
   * across tests.
   *
   * @param genMarks
   */
  public static void setGenerateMarks(boolean genMarks) {
    generate_marks = genMarks;
  }

  /**
   * Sets the flag that determines if superficial forms will be output
   * along with the lexical forms.
   *
   * @param sf
   */
  public void set_show_sf(boolean sf) {
    this.show_sf = sf;
  }

  public boolean get_show_sf() {
    return this.show_sf;
  }

  /**
   * Set the superficial form of the word.
   *
   * @param s the superficial form
   */
  public void set_superficial_form(String sf) {
    this.superficial_form = sf;
  }

  /**
   * Get the superficial form of the word
   */
  public String get_superficial_form() {
    return this.superficial_form;
  }

  /*
   * TODO: When optimizing, take into account that Java strings have built-in
   * regex support, while C++ strings do not. The list of patterns isn't even
   * used.
   */
  private static boolean match(String s, String pattern) {

    if (DEBUG) {
      System.out.println("TaggerWord.match(" + s + ", " + pattern + ")");
    }
    //Map<String, ApertiumRE>.Iterator it = patterns.find(pattern);
    if (!patterns.containsKey(pattern)) {
      String regexp = pattern;

      if (regexp.contains("<*>")) {
        regexp.replaceAll("<*>", "(<[^>]+>)+");
      }

      patterns.put(pattern, new ApertiumRE(regexp));
      //return "".equals(patterns.get(pattern).match(s));
            /* The line above makes no sense and produces different behavior
       * depending on if the pattern existed or not, also this seems to be an artifact
       * of porting from a language that doesn't have built-in regex support to one that
       * does. Replaced it with the same line that's run if the pattern does exist.
       * This fixed the issue of this method wrongly matching on input that didn't match.
       * (Such as matching prefer tags when it shouldn't have.)
       */
      return pattern.matches(s);

    } else {
      return pattern.matches(s);
    }
  }

  /**
   * Add a new tag to the set of all possible tags of the word.
   *
   * @param t the coarse tag
   * @param lf the lexical form (fine tag)
   */
  public void add_tag(int t, String lf, List<String> prefer_rules) {

    if (DEBUG)
      System.out.println("add_tag(" + t + ", " + lf + ", " + prefer_rules + ")");

    //Tag is added only is it is not present yet
    //Sometime one word can have more than one lexical form assigned to the same tag
    try {
      if (!tags.contains(t)) {
        tags.add(t);
        lexical_forms.put(t, lf);
      } else {
        for (int i = 0; i < prefer_rules.size(); i++) {
          if (match(lf, prefer_rules.get(i))) {
            if (DEBUG) {
              System.out.println("TaggerWord.add_tag -- prefer rules matched.");
            }
            lexical_forms.put(t, lf);
            break;
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Get the set of tags of this word.
   *
   * @return set of tags.
   */
  public Set<Integer> get_tags() {
    return tags;
  }

  public boolean isAmbiguous() {
    return tags.size() > 1;
  }

  /**
   * Get a string with the set of tags
   */
  public String get_string_tags() {
    String st = "{";

    if (tags.size() > 0) {
      st += array_tags.get(0);
    }
    for (int i = 1; i < tags.size(); i++) {
      st += ",";
      st += array_tags.get(i);
    }
    st += "}";

    return st;
  }

  /**
   * Get the lexical form (fine tag) for a given tag (coarse one)
   *
   * @param t the tag
   * @return the lexical form of tag t
   */
  public String get_lexical_form(int t, int TAG_kEOF) {
    String ret = "";

    if (show_ignored_string) {
      ret += ignored_string;
    }

    if (t == TAG_kEOF) {
      return ret;
    }

    if (!this.previous_plus_cut) {
      if (generate_marks && isAmbiguous()) {
        ret += "^=";
      } else {
        ret += "^";
      }

      if (get_show_sf()) {
        ret += superficial_form;
        ret += '/';
      }
    }

    if (lexical_forms.size() == 0) {
      ret += '*';
      ret += superficial_form;
    } else if (lexical_forms.get(0) != null && lexical_forms.get(0).startsWith("*")) {
      ret += '*';
      ret += superficial_form;
    } else if (lexical_forms.size() > 1) {
      ret += lexical_forms.get(t);
    } else {
      ret += lexical_forms.get(t);
    }

    if (!ret.equals(ignored_string)) {
      if (this.plus_cut) {
        ret += '+';
      } else {
        ret += '$';
      }
    }
    if (DEBUG) {
      System.out.println("TaggerWord.get_lexical_form -- returning: " + ret);
    }
    return ret;
  }

  public String get_all_chosen_tag_first(Integer t, int TAG_kEOF) {
    String ret = "";
    if (show_ignored_string)
      ret += ignored_string;

    if (t == TAG_kEOF)
      return ret;

    if (!previous_plus_cut) {
      if (generate_marks && isAmbiguous()) {
        ret += "^=";
      } else {
        ret += "^";
      }

      ret += superficial_form;

      if (lexical_forms.size() == 0) { // This is an UNKNOWN WORD
        ret += "/*";
        ret += superficial_form;
      } else {
        ret += "/";
        ret += lexical_forms.get(t);
        if (lexical_forms.size() > 1) {
          for (Integer it : tags) {
            /* Make sure we're not adding the tag at 't' twice(?)
             */
            if (it != t) {
              ret += "/";
              ret += lexical_forms.get(it);
            }
          }
        }
      }
    }

    if (!ignored_string.equals(ret)) {
      if (plus_cut)
        ret += "+";
      else
        ret += "$";
    }

    return ret;
  }

  /**
   * Add text to the ignored string
   */
  public void add_ignored_string(String s) {
    ignored_string += s;
  }

  /**
   * Set the flag plus_cut to a certain value. If this flag is set to true means
   * that there were a '+' between this word and the next one
   */
  public void set_plus_cut(boolean c) {
    plus_cut = c;
  }

  public boolean get_plus_cut() {
    return plus_cut;
  }

  public void setArrayTags(ArrayList<String> at) {
    array_tags = at;
  }

  /**
   * Prints a string representation of this TaggerWord to stdout.
   */
  public void print() {
    //This functionality was reworked into the toString() method
    System.out.println(toString());
  }

  @Override
  public String toString() {
    String tempString = new String();
    tempString += "[#" + superficial_form + "# ";
    for (Integer f : tags) {
      tempString += "(" + f + " " + lexical_forms.get(f) + ") ";
    }
    tempString += "\b]";
    return tempString;
  }

  public void outputOriginal(Appendable out2) throws IOException {
    String s = this.superficial_form;

    for (String form : lexical_forms.values()) {
      if (form.length() > 0) {
        s += "/";
        s += form;
      }
    }

    String out = "";
    if (s.length() > 0) {
      out = "^" + s + "$\n";
    }

    //Assuming writer is already setup for UTF-8
    out2.append(out);

  }

  public void discardOnAmbiguity(String tags) {
    if (isAmbiguous()) {
      Iterator<Map.Entry<Integer, String>> it = lexical_forms.entrySet().iterator();

      Set<Integer> newsettag = new LinkedHashSet<Integer>();
      while (it.hasNext()) {
        Map.Entry<Integer, String> cur = it.next();

        if (match(cur.getValue(), tags)) {
          /* The following call is the only safe way to remove an item
           * from a collection while an iteration is in progress.
           */
          it.remove();
          continue;
        } else {
          newsettag.add(cur.getKey());
        }

        if (lexical_forms.size() == 1) {
          /* Insert the key of that singular remaining form into
           * newsettag.
           */
          newsettag.add(lexical_forms.keySet().iterator().next());
          break;
        }
      }

      if (tags.length() != newsettag.size()) {
        this.tags = newsettag;
      }
    }
  }
}
