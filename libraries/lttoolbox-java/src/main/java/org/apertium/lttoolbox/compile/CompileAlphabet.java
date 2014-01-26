/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.compile;

import java.util.HashMap;
import org.apertium.lttoolbox.Alphabet;

/**
 * ONLY USED IN COMPILATION, therefore not initialized in Alphabet
 *
 * @author j
 */
class CompileAlphabet extends Alphabet {
  /**
   * pair object -> pair index mapping.
   * example {<0,0>=0, <w,w>=1, <o,o>=2, <u,u>=3, <n,n>=4, <d,d>=5, <0,-3>=6, <0,-4>=7, <s,-3>=8, <0,-5>=9, <o,i>=10, <u,n>=11, <n,d>=12, <d,-1>=13, <0,-2>=14}
   */
  private final HashMap<IntegerPair, Integer> spair = new HashMap<IntegerPair, Integer>();
  /** Non-threadsafe temp variable */
  IntegerPair tmp = new IntegerPair(0, 0);

  /**
   * Get/create an unique code for a pair of characters
   *
   * @param c1 left symbol
   * @param c2 right symbol
   * @return the code for (c1, c2)
   */
  public int cast(int c1, int c2) {
    tmp.first = c1;
    tmp.second = c2;
    Integer res = spair.get(tmp);
    if (res == null) {
      int spair_size = spair.size();
      spair.put(tmp, spair_size);
      spairinv.add(tmp);
      // for use next time
      tmp = new IntegerPair(0, 0);
      return spair_size;
    }
    return res;
  }
}
