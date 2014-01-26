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

/**
 * Copy from transfer (old version before july 2012)
 * A list of nodes currently matched.
 *
 * @author Jacob Nordfalk
 */
import java.util.Arrays;

class MatchState {
  /**
   * An array/buffer that keeps track of all the state transitions of this MatchState,
   * up to BUF_LIMIT, then it wraps around and starts over.
   */
  private int[] state;
  /**
   * Index of the first element in the "state" buffer, may be greater or less than
   * "last", as the buffer can wrap around.
   */
  private int first = 0;
  /**
   * Index of the last element in the "state" buffer, may be greater or less than
   * "first", as the buffer can wrap around. This indicates the index of the next
   * entry in the buffer to be (over)written.
   */
  private int last = 0;
  private static int BUF_LIMIT = 1024;
  /** Copy of node list from MatchExe.
   *
   * @see MatchExe
   */
  int[][] node_list;

  public MatchState(MatchExe me) {
    node_list = me.node_list;
    first = last = 0;
    state = new int[BUF_LIMIT];
  }

  public void clear() {
    first = last = 0;
  }

  public int size() {
    return last >= first ? last - first : last + BUF_LIMIT - first;
  }

  public void init(int initial) {
    first = 0;
    last = 1;
    state[0] = initial;
  }

  private void applySymbol(int pnode, int symbol) {
    /* pnode is the node ID.
     * symbol is the input symbol to search for.
     * node pulls out the list from node_list to make it easier to access.
     */
    int[] node = node_list[pnode];

    /* Search through the list of transitions from pnode to the input symbol.
     * The step is 2 because of the format of the node list, see the
     * javadoc for MatchExe.node_list for that specification.
     */
    for (int i = 0; i < node.length - 1; i += 2) { // TODO binary seach - No: only ca. 1%  cpu is used here anyway
      if (node[i] == symbol) { //If the input symbol was found
        int aux = node[i + 1]; //Grab that symbol's node id
        state[last] = aux; //store that node id
        last = (last + 1) % BUF_LIMIT;
        //increment last by 1 up to BUF_LIMIT, then wrap back around to zero.
        break;
      }
    }
  }
  private boolean DEBUG = false;

  public void step(int input) {
    if (DEBUG)
      System.out.println("step " + input);
    //Store the current end of the buffer, as the applySymbol calls will advance it.
    int mylast = last;
    /* Loop through each node in the state buffer from first to the old last
     * looking for transitions from each of those states to the input symbol.
     * By having i != mylast as the test, and using the % BUF_LIMIT, we can start
     * in the middle of the buffer somewhere, go to the end, wrap around to the
     * beginning, and continue searching.
     */
    for (int i = first; i != mylast; i = (i + 1) % BUF_LIMIT) {
      applySymbol(state[i], input);
    }
    //The old end of the buffer is now the beginning of the buffer.
    first = mylast;
  }

  public void step(int input, int alt) {
    if (DEBUG)
      System.out.println("step " + input + " " + alt);
    int mylast = last;
    for (int i = first; i != mylast; i = (i + 1) % BUF_LIMIT) {
      /* This is as above, but has two symbols to look for. The input symbol, and
       * an alternate. Transitions to either one are added to the state buffer.
       */
      applySymbol(state[i], input);
      applySymbol(state[i], alt);
    }
    first = mylast;
  }

  /**
   * Gets the output symbol if there is a final
   *
   * @return the output symbol (In transfer this is a rule number). Returns -1 if there isnt a final state.
   */
  public int classifyFinals() {
    int result = Integer.MAX_VALUE;
    /* This iterates from the first element to the last element, wrapping around
     * from the end of the buffer to the beginning, if necessary.
     */
    for (int i = first; i != last; i = (i + 1) % BUF_LIMIT) {
      int[] node = node_list[state[i]];

      //System.err.println("2node["+i+"] state = " + state[i]);
      /* If this is a final node, it will have an uneven number of entries.
       * Because a final symbol doesn't have a target ID.
       * node.length % 2 will equal 1 when the number of entries is uneven.
       */
      if (node.length % 2 == 1) {
        result = Math.min(result, node[node.length - 1]); // last elem is the value
      }
    }
    result = (result < Integer.MAX_VALUE) ? result : (-1);

    //System.err.println("2result = " + result);
    if (DEBUG)
      System.out.println("classifyFinals " + result);
    return result;
  }

  @Override
  public String toString() {
    //Not JDK 1.5 compliant: return "ms["+first +";"+last+"]=" + Arrays.toString(Arrays.copyOfRange(state, first, last));
    return "ms[" + first + ";" + last + "]=" + Arrays.asList(state).subList(first, last);
  }
}
