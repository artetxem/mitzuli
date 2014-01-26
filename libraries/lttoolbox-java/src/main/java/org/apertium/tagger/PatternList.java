package org.apertium.tagger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.Compression;
import org.apertium.lttoolbox.collections.Transducer;

/**
 * Created by Nic Cottrell, Jan 27, 2009 5:00:21 PM
 * Used in:
 * morpho_stream.cc: me = td->getPatternList().newMatchExe();
 * morpho_stream.cc: alphabet = td->getPatternList().getAlphabet();
 * morpho_stream.cc: ca_any_char = alphabet(PatternList::ANY_CHAR);
 * morpho_stream.cc: ca_any_tag = alphabet(PatternList::ANY_TAG);
 * Binara dosiero morpho_stream.o kongruas
 * tagger_data.cc:PatternList &
 * tagger_data.cc:TaggerData::getPatternList()
 * tagger_data.cc:TaggerData::setPatternList(PatternList const &pl)
 * tagger_data.h: PatternList plist;
 * tagger_data.h: void setPatternList(PatternList const &pl);
 * tagger_data.h: PatternList & getPatternList();
 * Binara dosiero tagger_data.o kongruas
 * tsx_reader.cc: plist = &(tdata.getPatternList());
 * tsx_reader.h: PatternList *plist;
 *
 */
class PatternList {
  private Alphabet alphabet;

  /* PatternStore is just a typedef in the C++ version for a multimap<int, vector<int>>.
   * The Java standard library doesn't have a multimap, so I'm making a map with Integer
   * keys, and then each entry is an ArrayList of ArrayLists of Integers.
   * This approach was chosen over writing a full PatternStore class, as then I would
   * have had to implement iterators in the PatternStore. This is much simpler.
   */
  private Map<Integer, ArrayList<ArrayList<Integer>>> patterns;
  private boolean sequence;
  private ArrayList<ArrayList<Integer>> sequence_data;
  private Transducer transducer;
  private Map<Integer, Integer> final_type;
  private int sequence_id;
  /**
   * This symbol stands for any char
   */
  static final String ANY_CHAR = "<ANY_CHAR>";
  /**
   * This symbol stands for any tag
   */
  static final String ANY_TAG = "<ANY_TAG>";
  /**
   * This symbol marks a word queue
   */
  static final String QUEUE = "<QUEUE>";

  /**
   * Constructor
   */
  PatternList() {
    sequence = false;
    alphabet = new Alphabet();
    alphabet.includeSymbol(ANY_TAG);
    alphabet.includeSymbol(ANY_CHAR);
    alphabet.includeSymbol(QUEUE);

    final_type = new LinkedHashMap<Integer, Integer>();
  }

  /**
   * Constructor that creates a new PatternList object by copying the passed-in one.
   *
   * @param p - The PatternList object to copy.
   */
  PatternList(PatternList p) {
    _copy(p);
  }

  /**
   * Copies the passed-in PatternList object to this one.
   *
   * @param p - The PatternList object to copy.
   */
  private void _copy(PatternList p) {
    sequence = p.sequence;
    sequence_data = new ArrayList<ArrayList<Integer>>(p.sequence_data);
    patterns = new LinkedHashMap<Integer, ArrayList<ArrayList<Integer>>>(p.patterns);
    alphabet = new Alphabet(p.alphabet);
    transducer = new Transducer(p.transducer);
  }

  /**
   * Private function to handle adding sequences to the patterns list.
   *
   * @param seqId - The key to store this entry under.
   * @param seqData - The actual sequence data to store.
   */
  private void _addPattern(Integer seqId, ArrayList<Integer> seqData) {
    ArrayList<ArrayList<Integer>> patternEntry = patterns.get(seqId);
    if (patternEntry == null) { //Nothing stored under that key yet.
      patternEntry = new ArrayList<ArrayList<Integer>>();
      patterns.put(seqId, patternEntry);
    }
    patternEntry.add(seqData);
  }

  void beginSequence() throws RuntimeException {
    if (sequence) {
      throw new RuntimeException("Error: opening an unended sequence");
    }
    sequence = true;
    sequence_data.clear();
  }

  void endSequence() throws RuntimeException {
    if (!sequence) {
      throw new RuntimeException("Error: ending an unopened sequence");
    }
    sequence = false;

    for (ArrayList<Integer> it : sequence_data) {
      it.add(alphabet.cast(QUEUE));
      _addPattern(sequence_id, it);
    }
  }

  void insertOutOfSequence(String lemma, String tags,
      ArrayList<Integer> result) {
    if (lemma.equals("")) {
      result.add(alphabet.cast(ANY_CHAR));
    } else {
      for (int i = 0, limit = lemma.length(); i < limit; i++) {
        if (lemma.charAt(i) == '*') {
          result.add(alphabet.cast(ANY_CHAR));
        } else {
          result.add((int) lemma.charAt(i));
        }
      }
    }
    if (tags.equals("")) {
      result.add(alphabet.cast(ANY_TAG));
    } else {
      for (int i = 0, limit = tagCount(tags); i < limit; i++) {
        String tag = "<" + tagAt(tags, i) + ">";

        if (tag.equals("<*>")) {
          result.add(alphabet.cast(ANY_TAG));
        } else {
          alphabet.includeSymbol(tag);
          result.add(alphabet.cast(tag));
        }
      }
    }
  }

  void insertIntoSequence(int id, String lemma,
      String tags) {
    sequence_id = id;

    if (sequence_data.size() == 0) {
      ArrayList<Integer> new_vector = new ArrayList<Integer>();
      insertOutOfSequence(lemma, tags, new_vector);
      sequence_data.add(new_vector);
    } else {
      for (ArrayList<Integer> it : sequence_data) {
        it.add((int) '+');
        insertOutOfSequence(lemma, tags, it);
      }
    }
  }

  void insert(int id, String lemma, String tags) {
    if (!sequence) {
      ArrayList<Integer> local = new ArrayList<Integer>();
      insertOutOfSequence(lemma, tags, local);
      local.add(alphabet.cast(QUEUE));
      _addPattern(id, local);
    } else {
      insertIntoSequence(id, lemma, tags);
    }
  }

  void insert(int id, int otherid) {
    if (!sequence) {
      throw new RuntimeException("Error: using labels outside of a sequence");
    }

    sequence_id = id;

    if (sequence_data.size() == 0) {
      final Collection<ArrayList<Integer>> p2 = patterns.get(otherid);
      for (ArrayList<Integer> pSecond : p2) {
        sequence_data.add(pSecond);
      }
    } else {
      ArrayList<ArrayList<Integer>> new_sequence_data =
          new ArrayList<ArrayList<Integer>>();

      for (ArrayList<Integer> it : sequence_data) {
        for (ArrayList<Integer> p : patterns.get(otherid)) {
          ArrayList<Integer> temp = new ArrayList<Integer>(it);
          temp.add((int) '+');
          temp.addAll(p);
          new_sequence_data.add(temp);
        }
      }

      sequence_data = new_sequence_data;
    }
  }

  int tagCount(String tags) {
    int count = 0;

    for (int i = 0, limit = tags.length(); i < limit; i++) {
      if (i == 0) {
        count++;
      } else if (tags.charAt(i) == '.') {
        count++;
      }
    }

    return count;
  }

  String tagAt(String tags, int index) {
    int start = 0;
    int end = 0;
    int count = 0;

    for (int i = 0, limit = tags.length(); i < limit; i++) {
      if (tags.charAt(i) == '.') {
        count++;
        if (end == 0) {
          start = 0;
        } else {
          start = end + 1;
        }
        end = i;
      }
      if (count == index + 1) {
        return tags.substring(start, end);
      }
    }

    if (index > count) {
      return "";
    }
    if (end != 0) {
      return tags.substring(end + 1);
    } else {
      return tags.substring(end);
    }
  }

  Map<Integer, ArrayList<ArrayList<Integer>>> getPatterns() {
    return patterns;
  }

  void buildTransducer() {

    for (int itFirst = 0; itFirst < sequence_data.size(); itFirst++) {
      ArrayList<Integer> itSecond = sequence_data.get(itFirst);

      int state = transducer.getInitial();
      int prevstate = -1;
      for (int i = 0, limit2 = itSecond.size(); i != limit2; i++) {
        int val = itSecond.get(i);
        if (alphabet.cast(ANY_CHAR) == val || alphabet.cast(ANY_TAG) == val) {
          state = transducer.insertSingleTransduction(val, state);
          if (prevstate != -1) {
            transducer.linkStates(prevstate, state, val);
            prevstate = -1;
          }
          transducer.linkStates(state, state, val);
        } else if (alphabet.cast(QUEUE) == val) {
          if (prevstate != -1) {
            // ignore second (and next) possible consecutive queues
            continue;
          }

          // optional queue
          prevstate = state;
          state = transducer.insertSingleTransduction((int) ('_'), state);
          transducer.linkStates(prevstate, state, (int) (' '));
          transducer.linkStates(prevstate, state, (int) ('#'));
          transducer.linkStates(state, state, alphabet.cast(ANY_CHAR));
        } else {
          state = transducer.insertSingleTransduction(val, state);
          if (prevstate != -1) {
            transducer.linkStates(prevstate, state, val);
            prevstate = -1;
          }
        }
      }
      if (prevstate != -1) {
        if (!transducer.isFinal(prevstate)) {
          transducer.setFinal(prevstate);
          final_type.put(prevstate, itFirst);
        }
        prevstate = -1;
      }
      if (!transducer.isFinal(state)) {
        transducer.setFinal(state);
        final_type.put(state, itFirst);
      }
    }
  }

  void write(OutputStream output) throws IOException {
    alphabet.write(output);
    final String tagger_name = "tagger";

    Compression.multibyte_write(1, output);
    Compression.String_write(tagger_name, output);
    transducer.write(output, alphabet.size());

    Compression.multibyte_write(final_type.size(), output);

    for (Map.Entry<Integer, Integer> it : final_type.entrySet()) {
      output.write(it.getKey());
      output.write(it.getValue());
    }
  }

  void read(InputStream input) throws IOException {

    sequence = false;
    final_type.clear();

    alphabet = Alphabet.read(input);
    if (Compression.multibyte_read(input) == 1) {
      String myStr = Compression.String_read(input);  // string (value "tagger") must be read/skipped
      transducer = Transducer.createRead(input, alphabet.size());

      int finalSize = Compression.multibyte_read(input);
      for (; finalSize != 0; finalSize--) {
        int key = Compression.multibyte_read(input);
        final_type.put(key, Compression.multibyte_read(input));
      }
    }
  }

  /* This function is calling a constructor that has been annotated as deprecated.
   * This is intentional, as that's what the C++ version is using.
   */
  @SuppressWarnings("deprecation")
  MatchExe newMatchExe() {
    return new MatchExe(transducer, final_type);
  }

  Alphabet getAlphabet() {
    return alphabet;
  }
}
