package org.apertium.lttoolbox.process;

/*
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.apertium.lttoolbox.Alphabet;

public class Node {
  /**
   * A linked list of transitions.
   * Experiments show that 95 % nodes have only 1 transition, and the rest have less than 8 transitions
   *
   * @author Jacob Nordfalk
   */
  static class Transition {
    /** The output symbol (character/tag) sent when making this transition */
    int output_symbol;
    /** Destination node when makine this transition */
    int node_dest;
    /** Next transition in the linked list */
    Transition next;
  }

  /**
   * The outgoing transitions of this node. Schema: (input symbol, (output symbol, destination node))
   */
  private Map<Integer, Transition> transitions;

  public void initTransitions(int number_of_local_transitions) {
    transitions = new HashMap<Integer, Transition>();//number_of_local_transitions);
  }
  private static final boolean FAST_BUT_REVERSE_ORDER = false;

  /**
   * Making a link between this node and another
   *
   * @param ins input symbol
   * @param outs output symbol
   * @param node_dest destination
   */
  public void addTransition(int ins, int outs, int node_dest) {

    Transition newTransition = new Transition();
    newTransition.output_symbol = outs;
    newTransition.node_dest = node_dest;

    if (FAST_BUT_REVERSE_ORDER) {
      Transition oldTransition = transitions.put(ins, newTransition);
      // if there was already a transition it is putted behind the new one in a linked list structure
      newTransition.next = oldTransition;
    } else {
      Transition oldTransition = transitions.get(ins);
      if (oldTransition == null) {
        transitions.put(ins, newTransition);
      } else {
        while (oldTransition.next != null) {
          oldTransition = oldTransition.next;
        }
        oldTransition.next = newTransition;
      }
    }
  }

  static Transition transitions_getIterator(TransducerExe transducer, int node_no, int input_symbol) {
    Node node = transducer.getNode(node_no);
    Transition tr = node.transitions.get(input_symbol);
    return tr;
  }

  @Override
  public String toString() {
    return "Node{" + this.transitions + "}@" + hashCode();
  }

  /** Note that this method is neccesarily very slow as the number of nodes increases,
   * as the nodes don't (and for memory usage reasont shouldnt) know their own node number */
  void show_DEBUG(int n, Alphabet a, Node[] node_list) {
    // TreeSet is used to get the list sorted
    for (Integer i : new TreeSet<Integer>(transitions.keySet())) {
      Transition t = transitions.get(i);
      while (t != null) {
        /*
         * if (t.node_dest.nodeLoadInfo!=null) t.node_dest.load();
         * int dest_node_no = -1;
         * for (int j=0; j<node_list.length; j++) {
         * if (node_list[j]==t.node_dest) dest_node_no = j;
         * }
         * System.err.println(dest_node_no + "\t" + n + "\t'" + a.getSymbol(i)+"'"+i+"\t'"+a.getSymbol(t.output_symbol)+"'"+t.output_symbol);
         * t = t.next;
         */
      }
    }
  }

  public static class TransitionIterator {
    private Transition transition;

    public TransitionIterator() {
    }

    boolean hasNext() {
      return transition != null;
    }

    int node_dest() {
      return transition.node_dest;
    }

    int output_symbol() {
      return transition.output_symbol;
    }

    void next() {
      transition = transition.next;
    }
  }

}
