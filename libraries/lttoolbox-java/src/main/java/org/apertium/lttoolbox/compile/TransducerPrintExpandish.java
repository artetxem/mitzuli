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
public class TransducerPrintExpandish extends TransducerComp {

  public TransducerPrintExpandish(TransducerComp t) {
    shallowCopy(t);
  }



  /** Helper class for collecting symbols during expanding/traversing */
  private static class TargetStateLR {
    TargetStateLR next = null;
    TargetStateLR last = null;

    String left, right;
    final Integer target_state;

    private TargetStateLR(Integer target_state, String l, String r) {
      left = l;
      right = r;
      this.target_state = target_state;
    }

    private String getLeft() {
      if (next != null) return left+next.getLeft();
      return left;
    }

    private String getRight() {
      if (next != null) return right+next.getRight();
      return right;
    }

    private void addlast(TargetStateLR targetStateLR) {
      if (last == null) next=last=targetStateLR;
      else {
        last.next = targetStateLR;
        last = targetStateLR;
      }
    }
  }

  private void showLtExpandish(Alphabet alphabet, PrintStream out, int state, boolean[] visited, String left, String right) {
    if (finals.contains(state)) {
      out.println(left + ":" + right);
      return;
    }
    try {

      visited[state] = true;

      Map<Integer, IntSet> it = transitions.get(state);
      LinkedHashMap<Integer, TargetStateLR> targetStatesLR = new LinkedHashMap<Integer,TargetStateLR>();

      // first, run thru and collect transitions according to target state
      for (Map.Entry<Integer, IntSet> it2 : it.entrySet()) {
        Integer it2_first = it2.getKey();
        Alphabet.IntegerPair t = alphabet.decode(it2_first);
        String l = alphabet.getSymbol(t.first);
        String r = alphabet.getSymbol(t.second);
        for (Integer target_state : it2.getValue()) {
          TargetStateLR lr = targetStatesLR.get(target_state);
          if (lr == null) targetStatesLR.put(target_state, new TargetStateLR(target_state, l, r));
          else lr.addlast(new TargetStateLR(target_state, l, r));
        }
      }
      // then recurse one time for each target state
      for (TargetStateLR lr : targetStatesLR.values()) {
        //System.out.println(lr.target_state+"   "+left+" "+lr.ls + ":" + right+" "+lr.rs);
        if (visited[lr.target_state]) {
          out.println("__CYCLE__ "+  left+lr.getLeft()+"…" + ":" + right+lr.getRight()+"…");
          return;
        }
        String lettersl = "";
        String lettersr = "";
        Integer target_state = lr.target_state;
        while (lr != null) {
          if (lr.left.length() != 1 || lr.right.length() != 1) {
            // Symbol or empty value. Print out seperately
            showLtExpandish(alphabet, out, lr.target_state, visited, left+lr.left, right+lr.right);
          } else {
            // letter. Collect all of them and show together
            lettersl += lr.left;
            lettersr += lr.right;
          }
          lr = lr.next;
        }
        if (lettersl.length()==1) showLtExpandish(alphabet, out, target_state, visited, left+lettersl, right+lettersr);
        if (lettersl.length()>1) showLtExpandish(alphabet, out, target_state, visited, left+"["+lettersl+"]", right+"["+lettersr+"]");
      }
      visited[state] = false;
    } finally {
      visited[state] = false;
    }
  }

  public void showLtExpandish(Alphabet alphabet, PrintStream out) {
    joinFinals();
    boolean[] visited = new boolean[transitions.size()];
    showLtExpandish(alphabet, out, 0, visited, "", "");
  }



  public static void main(String[] args) throws FileNotFoundException, IOException {
    LTPrint.main(new String[]{"-s", "testdata/trimming3/test-en.bin" });
    //LTPrint.main(new String[]{"-s", "testdata/bilingual/eo-en.autobil.bin" });
  }
}
