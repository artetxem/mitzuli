package org.apertium.lttoolbox.compile;

/*
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
import java.util.ArrayList;

/**
 * This is a "Compiler" helper class, to store the parts of each entry
 * before combining it to build the transducer being "compiled".
 *
 * @author Raah
 */
public class EntryToken {
  /**
   * Type of tokens, inner enum.
   */
  public static int TYPE_paradigm = 1;
  public static int TYPE_single_transduction = 2;
  public static int TYPE_regexp = 3;
  /**
   * Type of this token
   */
  int type;
  /**
   * Name of the paradigm (if it is of 'paradigm' 'type')
   */
  String paradigmName;
  /**
   * Left side of transduction (if 'single_transduction')
   */
  ArrayList<Integer> leftSide;
  /**
   * Right side of transduction (if 'single_transduction')
   */
  ArrayList<Integer> rightSide;
  /**
   * Regular expression (if 'regexp')
   */
  String regexp;

  /**
   * Sets the name of the paradigm.
   *
   * @param np the paradigm name
   */
  void setParadigm(String np) {
    paradigmName = np;
    type = TYPE_paradigm;
  }

  /**
   * Set both parts of a single transduction.
   *
   * @param pi leftSide part
   * @param pd right part
   */
  void setSingleTransduction(ArrayList<Integer> pi, ArrayList<Integer> pd) {
    leftSide = pi;
    rightSide = pd;
    type = TYPE_single_transduction;
  }

  /**
   * Set regular expression.
   *
   * @param r the regular expression specification.
   */
  void setRegexp(String r) {
    regexp = r;
    type = TYPE_regexp;
  }

  /**
   * Test EntryToken to detect if is a paradigm.
   *
   * @return true if it is a paradigm.
   */
  boolean isParadigm() {
    return type == TYPE_paradigm;
  }

  /**
   * Test EntryToken to check if it is a single transduction.
   *
   * @return true if it is a single transduction.
   */
  boolean isSingleTransduction() {
    return type == TYPE_single_transduction;
  }

  /**
   * Test EntryToken to check if it is a single regular expression.
   *
   * @return true if it is a regular expression.
   */
  boolean isRegexp() {
    return type == TYPE_regexp;
  }

  /**
   * Build a string representation of the entryToken
   *
   * @return the representation of the entryToken
   */
  @Override
  public String toString() {
    String res = "";
    if (type == TYPE_paradigm) {
      res += "paradigm name : " + paradigmName;
    } else if (type == TYPE_regexp) {
      res += "regexp : " + regexp;
    } else {
      res += "transduction : left " + leftSide + " right " + rightSide;
    }
    return res;
  }
}
