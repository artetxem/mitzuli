/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.compile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.LTPrint;
import org.apertium.lttoolbox.collections.IntSet;

/**
 *
 * @author j
 */
public class TransducerPrint extends TransducerComp {

  public TransducerPrint(TransducerComp t) {
    shallowCopy(t);
  }

  public void show(Alphabet alphabet, PrintStream out) {
    joinFinals();

    for (int state=0; state<transitions.size(); state++) {
      Map<Integer, IntSet> it = transitions.get(state);

      for (Map.Entry<Integer, IntSet> it2 : it.entrySet()) {
        Integer it2_first = it2.getKey();
        Alphabet.IntegerPair t = alphabet.decode(it2_first);
        for (Integer target_state : it2.getValue()) {
          out.printf("%d\t"+"%d\t", state, target_state);
          String l = alphabet.getSymbol(t.first);
          if (l=="") { // If we find an epsilon
            out.print("ε\t");
          } else {
            out.print(l+"\t");
          }
          String r = alphabet.getSymbol(t.second);
          if (r=="") { // If we find an epsilon
            out.print("ε\t");
          } else {
            out.print(r+"\t");
          }
          out.println();
        }
      }
    }
    for (Integer it3 : finals) {
      out.println(it3);
    }
  }


  public static void main(String[] args) throws FileNotFoundException, IOException {
    //LTPrint.main(new String[]{"-a", "testdata/trimming/test-en.bin" });
    LTPrint.main(new String[]{"-a", "testdata/bilingual/eo-en.autobil.bin" });
  }
}
