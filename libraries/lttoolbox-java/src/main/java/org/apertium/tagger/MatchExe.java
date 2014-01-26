package org.apertium.tagger;
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */

import java.util.ArrayList;
import java.util.Map;
import org.apertium.lttoolbox.collections.Transducer;
import org.apertium.lttoolbox.collections.IntSet;

/**
 * Copy from transfer (old version before july 2012)
 * The container object that contains all states (and transitions betweem them)
 *
 * @author Jacob Nordfalk
 */
class MatchExe {
  /**
   * Initial state
   */
  private int initial_id;
  /**
   * MatchNode list
   * Schema:
   * - node_list[node_id] gives transitions.for node_id
   * - node_list[node_id][0] gives 1st input symbol
   * - node_list[node_id][1] gives 1st input symbol's target node_id
   * - node_list[node_id][2] gives 2nd input symbol
   * - node_list[node_id][3] gives 2nd input symbol's target node_id
   * ...
   * if there is an UNEVEN number of elements in node_list[node_id], then its a FINAL node, and
   * - node_list[node_id][node_list[node_id].length-1] gives the finalnumber=output symbol=rule number
   *
   * SO: Set of final nodes is those which 2nd index of node_list is uneven
   */
  int[][] node_list;

  public MatchExe(MatchExe te) {
    _copy(te);
  }

  // Slow
  @Deprecated
  public MatchExe(Transducer t, Map<Integer, Integer> final_type) {
    // System.err.println("final_type = " + new TreeMap<Integer, Integer>(final_type));
    // approx every 7th value is set. For en-ca (big pair)
    // final_type = {14=1, 41=2, 48=2, 55=2, 62=2, 69=2, 76=2, 83=2, 90=2, 97=2, 103=90, 106=90, 109=90,
    // ...
    // 420739=211, 420741=213, 420743=215, 420745=215, 420747=215, 420749=216}

    // set up initial node
    initial_id = t.getInitial();

    int limit = t.transitions.size();

    // memory allocation
    node_list = new int[limit][];

    // set up the transitions
    for (int node_id = 0; node_id < limit; node_id++) { //Loop through node_id's
        /* Each entry in the ArrayList Transducer.transitions, is a state.
       * These states correspond to the node_id's which make up the first
       * level of the node_list array.
       *
       * Now, for the Maps that are the entries in the ArrayList.
       * The key is the input symbol, and the value is a list of target node_id's.
       *
       */
      final Map<Integer, IntSet> second = t.transitions.get(node_id);

      /* Using an ArrayList because we don't know how many elements we'll have
       * up front, and it's not worth trying to calculate ahead of time.
       */
      ArrayList<Integer> currArray = new ArrayList<Integer>();
      for (Integer it2First : second.keySet()) { //Loop through input symbols
        IntSet it2Second = second.get(it2First);
        for (Integer integer : it2Second) { //Loop through targets
          //mynode.addTransition(it2First, my_node_list[integer]);
          currArray.add(it2First);
          currArray.add(integer);
        }
      }
      /* Check if there is a final for this node_id, if so, add it.
       * Since we're iterating through all possible nodes, no need to worry
       * about missing any of the finals.
       */
      Integer final_symbol;
      if ((final_symbol = final_type.get(node_id)) != null) {
        currArray.add(final_symbol);
      }
      //Add temporary array to node_list
      int[] curr_node_list = node_list[node_id] = new int[currArray.size()];
      Integer[] currArrayArray = currArray.toArray(new Integer[1]);
      //Can't directly cast from Integer[] to int[]
      for (int i = 0; i < currArray.size(); i++) {
        curr_node_list[i] = currArrayArray[i];
      }
    }

  }

  private void _copy(MatchExe te) {
    initial_id = te.initial_id;
    node_list = te.node_list;
  }

  public int getInitial() {
    return initial_id;
  }
}