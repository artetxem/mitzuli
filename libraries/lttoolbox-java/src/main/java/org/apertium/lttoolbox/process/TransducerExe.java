package org.apertium.lttoolbox.process;

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
import java.io.File;
import org.apertium.lttoolbox.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.apertium.lttoolbox.Alphabet.IntegerPair;
import org.apertium.utils.IOUtils;

/**
 * Transducer class for execution of lexical processing algorithms. Formerly named TransExe, but its really the runtime
 * parallel to compile.Transducer
 *
 * @author Raah
 */
public class TransducerExe {
  public static boolean DELAYED_NODE_LOADING = false;

  /*
   * nodes 46191
   *
   *
   * alloc 64624
   *
   * public static boolean DELAYED_NODE_LOADING = true;
   * WITH BYTEBUFFER and a direct file after 9 nov 2012
   * alloc 425424
   *
   * WITH BYTEBUFFER and a direct file before 9 nov 2012
   * alloc 2085984
   *
   * public static boolean DELAYED_NODE_LOADING = false;
   * alloc 9556192
   */
  /**
   * Initial state
   */
  private int initial_id;
  /**
   * Node list
   */
  //private Node[] node_list;
  private HashMap<Integer, Node> node_list2;
  /**
   * Node positions in the byteBuffer
   */
  private ByteBuffer byteBufferPositions;
  /**
   * Used for delayed loading
   */
  private ByteBuffer byteBuffer;
  /**
   * Used for delayed loading
   */
  private int number_of_states;
  /**
   * Used for delayed loading
   */
  private Alphabet alphabet;
  /**
   * Set of final node indexes
   */
  private HashSet<Integer> final_ids = new HashSet<Integer>();

  int getInitialId() {
    return initial_id;
  }

  public HashSet<Integer> getFinals() {
    return final_ids;
  }
  /**
   * Pool of TNodeState (with their sequence list), for efficiency
   */
  private final ArrayList<State.TNodeState> nodeStatePool = State.REUSE_OBJECTS ? new ArrayList<State.TNodeState>(50) : null;

  final State.TNodeState nodeStatePool_get() {
    int size = nodeStatePool.size();
    if (size != 0) {
      State.TNodeState tn = nodeStatePool.remove(size - 1);
      tn.sequence.clear();
      //genbrugt++;
      return tn;
    } else {
      State.TNodeState tn = new State.TNodeState(this);
      //oprettet++;
      tn.sequence = new ArrayList<Integer>(State.INITAL_SEQUENCE_ALLOCATION);
      return tn;
    }
  }

  final void nodeStatePool_release(State.TNodeState state_i) {
    nodeStatePool.add(state_i);
    //state_i.transducer = null; // permit GC
  }

  /**
   * Note that this method is neccesarily very slow as the number of nodes increases, as the nodes don't (and for memory
   * usage reasont shouldnt) know their own node number
   */
  public void show_DEBUG(Alphabet a) {
    for (int i = 0; i < number_of_states; i++) {
      Node n = getNode(i);
      //n.show_DEBUG(i, a, node_list);
    }
  }

  Node getNode(int node_no) {
    //Node node = node_list[node_no];
    Node node = node_list2.get(node_no);
    if (node == null) {
      node = loadNode(node_no);
    }
    return node;
  }

  boolean isFinal(int where_node_id) {
    return final_ids.contains(where_node_id);
  }

  Node loadNode(int node_no) {
    //assert check: if (node_list[nodeNo__current_state] != sourceNode) throw new InternalError();
    Node node = new Node();
    //node_list[node_no] = node;
    node_list2.put(node_no, node);

    int byteBufferPosition = byteBufferPositions.getInt(node_no * 4);
    byteBuffer.position(byteBufferPosition); // seek to correct place in file
    int number_of_local_transitions = Compression.multibyte_read(byteBuffer); // typically 20-40, max seen is 694

    node.initTransitions(number_of_local_transitions);
    int tagbase = 0;
    while (number_of_local_transitions > 0) {
      number_of_local_transitions--;
      tagbase += Compression.multibyte_read(byteBuffer);
      int target_nodeNo = (node_no + Compression.multibyte_read(byteBuffer)) % number_of_states;
      IntegerPair pair = alphabet.decode(tagbase);
      int i_symbol = pair.first;
      int o_symbol = pair.second;
      //Node targetNode = node_list[target_nodeNo];
      node.addTransition(i_symbol, o_symbol, target_nodeNo);
    }

    return node;
  }

  public void read(ByteBuffer input, Alphabet alphabet) throws IOException {
    read(input, alphabet, null);
  }

  public void read(ByteBuffer input, Alphabet alphabet, File cachedFile) throws IOException {

    initial_id = Compression.multibyte_read(input);  // 0 for eo-en.dix)
    final int finals_size = Compression.multibyte_read(input); // xx  (5 for eo-en.dix)

    this.alphabet = alphabet;

    // first comes the list of the final nodes
    int[] myfinals = new int[finals_size]; // xx  ([679, 14875, 27426, 27883, 35871] for eo-en.dix)
    int base = 0;
    for (int i = 0; i < finals_size; i++) {
      base += Compression.multibyte_read(input);
      myfinals[i] = base;
    }

    //System.err.println(ant1 + " ettere ud af  " + number_of_states);
    for (int i = 0; i < finals_size; i++) {
      int final_index = myfinals[i];
      final_ids.add(final_index);
    }

    number_of_states = Compression.multibyte_read(input); // xx  (46191 for eo-en.dix)

    //node_list = new Node[number_of_states];
    node_list2 = new HashMap<Integer, Node>(1000);


    // Keep reference to bytebuffer for delayed node loading
    byteBuffer = input;


    int cacheFileSize = number_of_states * 4 + 4; // one extra int to hold index of end of transducer
    byteBufferPositions = IOUtils.mapByteBuffer(cachedFile, cacheFileSize);

    if (FSTProcessor.DEBUG) {
      System.err.println("TransducerExe read states:" + number_of_states + "  cachedFile=" + cachedFile + " " + byteBufferPositions.isReadOnly() + " " + byteBufferPositions);
    }


    if (byteBufferPositions.isReadOnly()) {
      int lastPos = byteBufferPositions.getInt(number_of_states * 4);
      input.position(lastPos); // Skip to end position
      return;
    }

    // No cache. Load and index the nodes
    for (int nodeNo__current_state = 0; nodeNo__current_state < number_of_states; nodeNo__current_state++) {

      byteBufferPositions.putInt(input.position());
      //System.out.println(number_of_states+"  "+nodeNo__current_state+ " "+input.position());
      int number_of_local_transitions = Compression.multibyte_read(input); // typically 20-40, max seen is 694


      if (DELAYED_NODE_LOADING) {
        Compression.multibyte_skip(input, 2 * number_of_local_transitions);
      } else {
        loadNode(nodeNo__current_state); // skips the correct number of positions
      }
    }
    byteBufferPositions.putInt(input.position()); // Remember end position

  }
}
