/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.process;

import java.util.Arrays;
import java.util.HashSet;

/**
 *
 * @author Jacob Nordfalk
 */
public final class SetOfCharacters {
  /** Limit on when to fall back on a slower HashSet<Character> */
  private static final int LIMIT = 255;
  final boolean[] set = new boolean[LIMIT];
  HashSet<Character> otherChars = new HashSet<Character>();

  // Probe
  //public static int max_encountered = 0;
  final public void add(char c) {
    //max_encountered = Math.max(max_encountered, c);
    if (c < LIMIT)
      set[c] = true;
    else
      otherChars.add(c);
  }

  final void clear() {
    Arrays.fill(set, false);
    otherChars.clear();
  }

  public final boolean contains(char c) {
    if (c == ((char) -1))
      return false; // EOF
    if (c < LIMIT)
      return set[c];
    //if (c>max_encountered) return false;
    return otherChars.contains(c);
  }
}
