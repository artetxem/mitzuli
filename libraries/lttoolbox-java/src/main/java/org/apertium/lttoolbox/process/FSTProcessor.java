package org.apertium.lttoolbox.process;
/*
 * Xerox:
 *
 * @P.feature.value@ When a @P.feature.value@ Flag Diacritic is encountered, the value of the indicated feature is simply
 * set or reset to the indicated value.
 *
 * @N.feature.value@ When an @N.feature.value@ Flag Diacritic is encountered, the value of feature is set or reset to the
 * negation or complement of value.
 *
 * @R.feature.value@ When an @R.feature.value@Flag Diacritic is encountered, a test is performed; this test succeeds if
 * and only if feature is currently set to value.
 *
 * @R.feature@ When an @R.feature@ Flag Diacritic is encountered, the test succeeds if and only if feature is currently
 * set to some value other than neutral.
 *
 * @D.feature.value@ When a @D.feature.value@Flag Diacritic is encountered, the test succeeds if and only if feature is
 * currently neutral or is set to a value that is incompatible with value.
 *
 * @D.feature@ When a @D.feature@ Flag Diacritic is encountered, the test succeeds if and only if feature is currently
 * neutral (unset).
 *
 * @C.feature@ When a @C.feature@ Flag Diacritic is encountered, the value of feature is reset to neutral.
 *
 * @U.feature.value@ If feature is currently neutral, then encountering @U.feature.value@ simply causes feature to be set
 * to value.
 *
 * ------------
 *
 * <jacobEo> we have 4 combinations of words: <jacobEo> LR : can be both <jacobEo> L : root only <jacobEo> R ending only
 * <jacobEo> - cannot be part of a compound
 *
 * LR: <if (!cmp) fail><if (R) fail><if L set R=on><set L=on> L : <if (!cmp) fail><set L=on> R : <if (!cmp) fail><if (R)
 * fail><set R=on> - : <if (cmp) fail>
 *
 *
 * when entering compounding prepend: @P.cmp.yes@ R : @R.cmp.yes@ @R.right.no@ @P.right.yes@ L : @R.cmp.yes@ - :
 * @R.cmp.no@ add after last compound word: @R.right.yes@
 *
 *
 *
 * when entering compounding prepend: @set:cmp=yes@ R : @assert:cmp==yes@ @assert:right!=yes@ @set:right=yes@ L :
 * @assert:cmp==yes@ - : @assert:cmp!=yes@ add after last compound word: @assert:right==yes@
 *
 */


/*
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, butflagvalues = WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
import java.io.*;
import org.apertium.lttoolbox.*;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.apertium.utils.IOUtils;

public class FSTProcessor extends BasicFSTProcessor {
  private boolean isLastBlankTM;
  private boolean showControlSymbols = false;
  private boolean biltransSurfaceForms;

  private void initDecompositionSymbols() {
    if ((compoundOnlyLSymbol = alphabet.cast("<:co:only-L>")) == 0) {
      if ((compoundOnlyLSymbol = alphabet.cast("<:compound:only-L>")) == 0) {
        if ((compoundOnlyLSymbol = alphabet.cast("<@co:only-L>")) == 0) {
          if ((compoundOnlyLSymbol = alphabet.cast("<@compound:only-L>")) == 0) {
            if ((compoundOnlyLSymbol = alphabet.cast("<compound-only-L>")) == 0) {
              System.err.println("Warning: Decomposition symbol <:compound:only-L> not found");
            } else {
              if (!showControlSymbols) {
                alphabet.setSymbol(compoundOnlyLSymbol, "");
              }
            }
          }
        }
      }
    }

    if ((compoundRSymbol = alphabet.cast("<:co:R>")) == 0) {
      if ((compoundRSymbol = alphabet.cast("<:compound:R>")) == 0) {
        if ((compoundRSymbol = alphabet.cast("<@co:R>")) == 0) {
          if ((compoundRSymbol = alphabet.cast("<@compound:R>")) == 0) {
            if ((compoundRSymbol = alphabet.cast("<compound-R>")) == 0) {
              System.err.println("Warning: Decomposition symbol <:compound:R> not found");
            } else {
              if (!showControlSymbols) {
                alphabet.setSymbol(compoundRSymbol, "");
              }
            }
          }
        }
      }
    }
  }

  /**
   * @param dictionaryCase the dictionaryCase to set
   */
  public void setDictionaryCase(boolean dictionaryCase) {
    this.dictionaryCase = dictionaryCase;
  }

  /**
   * @param showControlSymbols the showHiddenSymbols to set
   */
  public void setShowControlSymbols(boolean showControlSymbols) {
    this.showControlSymbols = showControlSymbols;
  }

  public enum GenerationMode {
    gm_clean, // clear all
    gm_unknown, // display unknown words, clear transfer and generation tags
    gm_all, // display all
    gm_tagged   // tagged generation
  }
  //private Collator myCollator = Collator.getInstance();
  /**
   * Set incoditional sections in the dictionaries
   */
  private Set<TransducerExe> inconditional = new HashSet<TransducerExe>();
  /**
   * Set standard sections in the dictionaries
   */
  private Set<TransducerExe> standard = new HashSet<TransducerExe>();
  /**
   * Set postblank sections in the dictionaries
   */
  private Set<TransducerExe> postblank = new HashSet<TransducerExe>();
  /**
   * Set preblank sections in the dictionaries
   */
  private Set<TransducerExe> preblank = new HashSet<TransducerExe>();
  /**
   * Queue of blanks, used in reading methods
   */
  // Not JDK1.5 compliant: private ArrayDeque<String> blankqueue = new ArrayDeque<String>();
  private LinkedList<String> blankqueue = new LinkedList<String>();
  /**
   * Set of characters to escape with a backslash
   */
  private SetOfCharacters escaped_chars = new SetOfCharacters();
  /**
   * Input buffer
   */
  private Buffer input_buffer = new Buffer(2048);
  /**
   * true if the position of input stream is out of a word
   */
  private boolean outOfWord;
  /**
   * if true, makes always difference between uppercase and lowercase characters
   */
  private boolean caseSensitive = false;
  /**
   * if true, makes always difference between uppercase and lowercase characters
   */
  private boolean dictionaryCase = false;
  /**
   * if true, flush the output when the null character is found
   */
  private boolean nullFlush = false;
  private ArrayList<String> tmNumbers;

  public FSTProcessor() {
    // escaped_chars chars
    escaped_chars.add('[');
    escaped_chars.add(']');
    escaped_chars.add('{');
    escaped_chars.add('}');
    escaped_chars.add('^');
    escaped_chars.add('$');
    escaped_chars.add('/');
    escaped_chars.add('\\');
    escaped_chars.add('@');
    escaped_chars.add('<');
    escaped_chars.add('>');
    //not really elegant, but the Pool attribute is static,
    //thus shared by all the instances of the class
    //and it needs to be initialized somewhere
    // JACOB initial_state.poolInit();
  }

  private void streamError() {
    throw new RuntimeException("Error: Malformed input stream.");
  }

  private void streamError(char val) {
    throw new RuntimeException("Error: Illegal input stream char: " + (char) val);
  }

  private char readEscaped(Reader input) throws IOException {
    int val = input.read();
    if (val == EOF) {
      streamError();
    }

    if (!escaped_chars.contains((char) val)) {
      streamError();
    }

    return (char) val;
  }

  private String readFullBlock(Reader input, char delim1, char delim2) throws IOException {
    StringBuilder result = new StringBuilder();
    result.append(delim1);

    int c = delim1;

    while (c != delim2) {
      c = input.read();
      if (c == EOF) {
        break;
      }
      result.append((char) c);
      if (c == '\\') {
        result.append(readEscaped(input));
      }
    }

    if (c != delim2) {
      streamError((char) c);
    }

    return result.toString();
  }
  private static final char EOF = (char) -1;

  private char readAnalysis(Reader input) throws IOException {
    if (!input_buffer.isEmpty()) {
      return input_buffer.next();
    }

    char val = (char) input.read();
    if (val == EOF) {
      return (char) 0;
    }


    if (escaped_chars.contains(val)) {
      switch (val) {
        case '<':
          char altval = (char) (alphabet.cast(readFullBlock(input, '<', '>')));
          input_buffer.add(altval);
          return altval;

        case '[':
          blankqueue.addLast(readFullBlock(input, '[', ']'));
          input_buffer.add((' '));
          return (' ');

        case '\\':
          val = (char) input.read();
          if (!escaped_chars.contains(val)) {
            streamError(val);
          }
          input_buffer.add(val);
          return val;

        default:
          streamError(val);
      }
    }

    input_buffer.add((char) val);
    return (char) val;
  }

  private char readTMAnalysis(Reader input) throws IOException {
    isLastBlankTM = false;
    if (!input_buffer.isEmpty()) {
      return input_buffer.next();
    }
    char val = (char) input.read();
    if (val == EOF) {
      return (char) 0;
    }
    char altval = (char) 0;

    if (escaped_chars.contains(val) || iswdigit(val)) {
      switch (val) {
        case '<':
          altval = (char) (alphabet.cast(readFullBlock(input, '<', '>')));
          input_buffer.add(altval);
          return altval;

        case '[':
          blankqueue.addLast(readFullBlock(input, '[', ']'));
          input_buffer.add((' '));
          isLastBlankTM = true;
          return (' ');

        case '\\':
          val = (char) input.read();
          if (!escaped_chars.contains(val)) {
            streamError(val);
          }
          input_buffer.add((val));
          return val;

        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          String s = "";
          if (!input.markSupported()) {
            throw new RuntimeException("The inpput stream doesn't support marks."
                + "There will be a problem to find an equivalent to the ungetwc function");
          }
          do {
            s += val;
            input.mark(3);
            val = (char) input.read();
          } while (iswdigit(val));
          input.reset();
          input_buffer.add((char) alphabet.cast("<n>"));
          tmNumbers.add(s);
          return (char) alphabet.cast(s);
        default:
          streamError(val);
      }
    }

    input_buffer.add(val);
    return val;
  }

  private char readPostgeneration(Reader input) throws IOException {
    if (!input_buffer.isEmpty()) {
      return input_buffer.next();
    }

    char val = (char) input.read();
    if (val == EOF) {
      return (char) 0;
    }

    char altval = (char) 0;

    switch (val) {
      case '<':
        altval = (char) (alphabet.cast(readFullBlock(input, '<', '>')));
        input_buffer.add(altval);
        return altval;

      case '[':
        blankqueue.addLast(readFullBlock(input, '[', ']'));
        input_buffer.add((' '));
        return (' ');

      case '\\':
        val = (char) input.read();
        if (!escaped_chars.contains(val)) {
          streamError(val);
        }
        input_buffer.add((val));
        return val;

      default:
        input_buffer.add(val);
        return val;
    }
  }

  private void skipUntil(Reader input, Appendable output, char character) throws IOException {
    while (true) {
      char val = (char) input.read();

      if (val == character) {
        return;
      } else if (val == '\\') {
        val = (char) input.read();
        if (val == EOF) {
          return;
        }

        output.append('\\');
        output.append(val);
      } else if (val == EOF) {
        return;
      } else {
        output.append(val);
      }
    }
  }

  private int readGeneration(Reader input, Appendable output) throws IOException {

    char val = (char) input.read();
    if (val == EOF) {
      return 0x7fffffff;
    }


    if (outOfWord) {
      if (val == '^') {
        val = (char) input.read();
        if (val == EOF) {
          return 0x7fffffff;
        }

      } else if (val == '\\') {
        output.append(val);
        val = (char) input.read();
        if (val == EOF) {
          return 0x7fffffff;
        }

        output.append(val);
        skipUntil(input, output, '^');
        val = (char) input.read();
        if (val == EOF) {
          return 0x7fffffff;
        }

      } else {
        output.append(val);
        skipUntil(input, output, '^');
        val = (char) input.read();
        if (val == EOF) {
          return 0x7fffffff;
        }

      }
      outOfWord = false;
    }

    if (val == '\\') {
      val = (char) input.read(); // XXX
      return (int) (val);
    } else if (val == '$') {
      outOfWord = true;
      return (int) ('$');
    } else if (val == '<') {
      String cad = "" + val;
      val = (char) input.read();
      if (val == EOF) {
        streamError(val);
      }

      while (val != '>') {
        cad += val;
        val = (char) input.read();
        if (val == EOF) {
          streamError(val);
        }
      }
      cad += val;
      return alphabet.cast(cad);
    } else if (val == '[') {
      output.append(readFullBlock(input, '[', ']'));
      return readGeneration(input, output);
    } else {
      return (int) (val);
    }

    // return 0x7fffffff;
  }

  public Pair<String, Integer> readBilingual(Reader input, Appendable output) throws IOException {
    int val = input.read();
    //System.err.println("read '"+((char) val)+"' : "+val );
    String symbol = "";
    if (val == EOF) {
      return new Pair<String, Integer>(symbol, 0x7fffffff);
    }

    if (outOfWord) {
      if (val == '^') {
        val = (char) input.read();
        if (val == EOF) {
          return new Pair<String, Integer>(symbol, 0x7fffffff);
        }
      } else if (val == '\\') {
        output.append((char) val);
        val = (char) input.read();
        if (val == EOF) {
          return new Pair<String, Integer>(symbol, 0x7fffffff);
        }
        output.append((char) val);
        skipUntil(input, output, '^');
        val = (char) input.read();
        if (val == EOF) {
          return new Pair<String, Integer>(symbol, 0x7fffffff);
        }
      } else {
        output.append((char) val);
        skipUntil(input, output, '^');
        val = (char) input.read();
        if (val == EOF) {
          return new Pair<String, Integer>(symbol, 0x7fffffff);
        }
      }
      outOfWord = false;
    }

    if (val == '\\') {
      val = (char) input.read();
      return new Pair<String, Integer>(symbol, 0x7fffffff);
    } else if (val == '$') {
      outOfWord = true;
      return new Pair<String, Integer>(symbol, (int) '$');
    } else if (val == '<') {
      String cad = "<";
      val = (char) input.read();
      if (val == EOF) {
        streamError();
      }
      while (val != '>') {
        cad += (char) val;
        val = input.read();
        if (val == EOF) {
          streamError();
        }
      }
      cad += (char) val;
      int res = alphabet.cast(cad);
      if (res == 0) {
        symbol = cad;
      }
      return new Pair<String, Integer>(symbol, res);
    } else if (val == '[') {
      output.append(readFullBlock(input, '[', ']'));
      return readBilingual(input, output);
    } else {
      return new Pair<String, Integer>(symbol, val);
    }
  }

  private void flushBlanks(Appendable output) throws IOException {
    for (int i = blankqueue.size(); i > 0; i--) {
      output.append(blankqueue.getFirst());
      blankqueue.removeFirst();
    }
  }

  private boolean endsWith(String str, String suffix) {
    return str.endsWith(suffix);
    /*
     * if (str.length() < suffix.length()) { return false; } else { return str.substring(str.length() -
     * suffix.length()).equals(suffix);
     * }
     */
  }

  private void classifyFinals() {
    for (String name : transducers.keySet()) {
      final TransducerExe transducer = transducers.get(name);
      if (endsWith(name, "@inconditional")) {
        inconditional.add(transducer);
      } else if (endsWith(name, "@standard")) {
        standard.add(transducer);
      } else if (endsWith(name, "@postblank")) {
        postblank.add(transducer);
      } else if (endsWith(name, "@preblank")) {
        preblank.add(transducer);
      } else {
        throw new RuntimeException("Error: Unsupported transducer type for '" + name + "'.");
      }
    }
  }

  private void writeEscaped(CharSequence str, Appendable output) throws IOException {
    //int len = str.length();
    //System.err.println("writeEscaped( str.length() = " + str.length());
    for (int i = 0, limit = str.length(); i < limit; i++) {
      char ch = str.charAt(i);
      if (escaped_chars.contains(ch)) {
        output.append('\\');
      }
      output.append(ch);
    }
  }

  private void printWord(String surfaceForm, String lexicalForm, Appendable output) throws IOException {
    output.append('^');
    writeEscaped(surfaceForm, output);
    output.append(lexicalForm);
    output.append('$');
  }

  private void printWordBilingual(String surfaceForm, String lexicalForm, Appendable output) throws IOException {
    output.append('^');
    output.append(surfaceForm);
    output.append(lexicalForm);
    output.append('$');
  }

  private void printUnknownWord(String surfaceForm, Appendable output) throws IOException {
    output.append('^');
    writeEscaped(surfaceForm, output);
    output.append('/');
    output.append('*');
    writeEscaped(surfaceForm, output);
    output.append('$');
  }

  private int lastBlank(CharSequence str) {
    for (int i = str.length() - 1; i >= 0; i--) {
      if (!alphabetic_chars.contains(str.charAt(i))) {
        return (i);
      }
    }
    return 0;
  }

  void printSpace(char val, Appendable output) throws IOException {
    if (blankqueue.size() > 0) {
      flushBlanks(output);
    } else {
      output.append(val);
    }
  }

  private boolean isEscaped(char c) {
    return escaped_chars.contains(c);
  }

  private boolean isAlphabetic(char c) {
    return alphabetic_chars.contains(c);
  }

  public void initAnalysis() {
    calc_initial_state();
    classifyFinals();
    if (do_flagMatch) {
      initFlagMatch();
    }

    if (!showControlSymbols) {
      hideControlSymbols();
    }
  }
  private boolean do_decomposition = false;
  int compoundOnlyLSymbol = 0;
  int compoundRSymbol = 0;
  int compound_max_elements = 4; //Integer.MAX_VALUE-1;// hard coded for now, but there might come a switch one day

  public void initDecomposition() {
    do_decomposition = true;
    initAnalysis();
    initDecompositionSymbols();

    //System.err.println("initial_compounding_state = " + initial_compounding_state.toString());
    if (!showControlSymbols) {
      hideControlSymbols();
    }
  }

  private void hideControlSymbols() {
    for (String symbol : alphabet.getFlagMatchSymbols()) {
      // Find symbols starting with "<@" (or "<:")
      if ("".equals(symbol)) {
        continue; // already hidden
      }
      char start = symbol.charAt(1);
      if (start == '@' || start == ':') {
        int symboli = alphabet.cast(symbol);
        alphabet.setSymbol(symboli, "");
        //System.err.println("Skjuler symbol = " + symbol);
      }
    }
  }
  private boolean do_flagMatch = false;
  int[] flagMatch_symbolToVarVal;
  int flagMatch_no_of_flags = -1;

  public void initFlagMatch() {
    do_flagMatch = true;
    if (flagMatch_symbolToVarVal != null) {
      return; // already initialized
    }

    flagMatch_symbolToVarVal = new int[alphabet.size()];
    if (DEBUG) {
      System.err.println("alphabet = " + alphabet);
    }
    final String flagAssigmentChar = ":";
    // iterate thru all <var:val> like <mi:1>

    ArrayList<String> flagList = new ArrayList<String>();
    ArrayList<String> valueList = new ArrayList<String>();


    for (String symbol : alphabet.getFlagMatchSymbols()) {
      String[] varval = symbol.split(flagAssigmentChar);
      if (varval.length != 2 || varval[1].length() == 0) {
        continue;
      }
      if (varval[1].length() == 0) {
        System.err.println("Warning: symbol must have a value to be used for flagmatch: " + symbol);
        continue;
      }
      int symboli = alphabet.cast(symbol);
      if (!showControlSymbols) {
        alphabet.setSymbol(symboli, "");
      }

      // Instead of remembering strings
      int flagIndex = flagList.indexOf(varval[0]);
      if (flagIndex == -1) {
        flagIndex = flagList.size();
        flagList.add(varval[0]);
        flagMatch_no_of_flags = flagIndex + 1;
      }

      int valueIndex = valueList.indexOf(varval[1]);
      if (valueIndex == -1) {
        valueIndex = valueList.size();
        valueList.add(varval[1]);
      }
      valueIndex++; // count values from one

      flagMatch_symbolToVarVal[-symboli - 1] = flagIndex << 16 | valueIndex;
      if (DEBUG) {
        System.err.println(symboli + symbol + " is " + flagIndex + ":" + valueIndex + "(==" + (flagIndex << 16 | valueIndex));
      }
    }
    if (DEBUG) {
      System.err.println("alphabet = " + alphabet);
    }
    if (DEBUG) {
      System.err.println("flagList = " + flagList);
    }
    if (DEBUG) {
      System.err.println("valueList = " + valueList);
    }
    if (DEBUG) {
      System.err.println("flagMatch_symbolToVarVal = " + Arrays.toString(flagMatch_symbolToVarVal));
    }
    //if (removeSymbolsFromOutput) alphabet.setSymbol(l, "");
  }

  private void deleteStatesWithConflictingFlags(State current_state) {
    if (DEBUG) {
      System.err.println("deleteStatesWithConflictingFlags: " + current_state);
    }

    stateLoop:
    for (int i = current_state.state.size() - 1; i >= 0; i--) {
      byte[] flagvalues = new byte[flagMatch_no_of_flags]; // TODO reuse/avoid initialization
      if (DEBUG) {
        System.err.println("deleteStatesWithConflictingFlags: " + current_state.state.get(i));
      }
      List<Integer> seq = current_state.state.get(i).sequence;
      for (Integer symbolii : seq) {
        int symboli = symbolii.intValue();
        if (symboli >= 0) {
          continue;
        }
        int flagIndex_value = flagMatch_symbolToVarVal[-symboli - 1];
        if (flagIndex_value == 0) {
          continue;
        }
        int flagIndex = flagIndex_value >> 16;
        int newValue = (flagIndex_value & 0xffff);
        int oldValue = flagvalues[flagIndex];
        if (oldValue == 0) {
          flagvalues[flagIndex] = (byte) newValue;

          if (DEBUG) {
            System.err.println(flagIndex + ": " + oldValue + "->" + newValue + "flagvalues = " + Arrays.toString(flagvalues));
          }
          continue;
        } else if (oldValue != newValue) {
          // conbflicting flags, remove  state

          if (DEBUG) {
            System.err.println(flagIndex + ": " + oldValue + "!=" + newValue + " - deleting " + current_state.state.get(i));
          }
          current_state.state.remove(i);
          continue stateLoop;
        }
      }
    }
  }

  public void initTMAnalysis() {
    calc_initial_state();
    tmNumbers = new ArrayList<String>();
    if (!showControlSymbols) {
      hideControlSymbols();
    }
  }

  public void initGeneration() {
    calc_initial_state();
    if (do_flagMatch) {
      initFlagMatch();
    }
    if (!showControlSymbols) {
      hideControlSymbols();
    }
  }

  public void initPostgeneration() {
    initGeneration();
  }

  public void initBiltrans() {
    initGeneration();
  }

  /*
   * private final char charAt(String s, int index) { return s.charAt(index); }
   */
  public synchronized void analysis(Reader input, Appendable output) throws IOException {
    if (getNullFlush()) {
      analysis_wrapper_null_flush(input, output);
    }

    boolean last_incond = false;
    boolean last_postblank = false;
    boolean last_preblank = false;
    State current_state = initial_state.copy();
    StringBuilder lf = new StringBuilder(200); // lexical form
    StringBuilder sf = new StringBuilder(200); // surface form
    int last = 0;
    /*
     * System.err.println("\nall_finals = " + all_finals); System.err.println("\ninconditional = " + inconditional);
     * System.err.println("\npostblank = " + postblank); System.err.println("\npreblank = " + preblank);
     */
    char val;
    while ((val = readAnalysis(input)) != (char) 0) {
      if (current_state.isFinal()) {
        if (current_state.isFinal(inconditional)) {
          boolean firstupper = !dictionaryCase && Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = !dictionaryCase && firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));
          if (compoundOnlyLSymbol != 0) {
            current_state.pruneStatesWithForbiddenSymbol(compoundOnlyLSymbol);
          }
          if (do_flagMatch) {
            deleteStatesWithConflictingFlags(current_state);
          }
          lf.setLength(0);
          current_state.filterFinals(lf, alphabet, escaped_chars, uppercase, firstupper);
          last = input_buffer.getPos();
          last_incond = true;
        } else if (current_state.isFinal(postblank)) {
          boolean firstupper = !dictionaryCase && Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = !dictionaryCase && firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));
          if (compoundOnlyLSymbol != 0) {
            current_state.pruneStatesWithForbiddenSymbol(compoundOnlyLSymbol);
          }
          if (do_flagMatch) {
            deleteStatesWithConflictingFlags(current_state);
          }
          lf.setLength(0);
          current_state.filterFinals(lf, alphabet, escaped_chars, uppercase, firstupper);
          last = input_buffer.getPos();
          last_postblank = true;
        } else if (current_state.isFinal(preblank)) {
          boolean firstupper = !dictionaryCase && Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = !dictionaryCase && firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));
          if (compoundOnlyLSymbol != 0) {
            current_state.pruneStatesWithForbiddenSymbol(compoundOnlyLSymbol);
          }
          if (do_flagMatch) {
            deleteStatesWithConflictingFlags(current_state);
          }
          lf.setLength(0);
          current_state.filterFinals(lf, alphabet, escaped_chars, uppercase, firstupper);
          last = input_buffer.getPos();
          last_preblank = true;
        } else {
          // then current_state.isFinal(standard) must be true
          // if (!current_state.isFinal(standard)) throw new IllegalStateException("expected current_state.isFinal(standard) here");
          if (!isAlphabetic(val)) {
            boolean firstupper = !dictionaryCase && Alphabet.isUpperCase(sf.charAt(0));
            boolean uppercase = !dictionaryCase && firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));
            if (compoundOnlyLSymbol != 0) {
              current_state.pruneStatesWithForbiddenSymbol(compoundOnlyLSymbol);
            }
            if (do_flagMatch) {
              deleteStatesWithConflictingFlags(current_state);
            }
            lf.setLength(0);
            current_state.filterFinals(lf, alphabet, escaped_chars, uppercase, firstupper);
            last = input_buffer.getPos();
            last_postblank = last_preblank = last_incond = false;
          }
        }
      } else if ((sf.length() == 0) && Alphabet.isSpaceChar(val)) {
        lf.setLength(0); // TODO fjern?
        lf.append("/*");
        lf.append(sf);
        last_postblank = last_preblank = last_incond = false;
        last = input_buffer.getPos();
      }

      current_state.step_case(val, caseSensitive);

      if (current_state.size() != 0) {

        sf.append(alphabet.getSymbol(val));

      } else {
        if (!isAlphabetic(val) && sf.length() == 0) {
          if (Alphabet.isSpaceChar(val)) {
            printSpace(val, output);
          } else {
            if (isEscaped(val)) {
              output.append('\\');
            }
            output.append((char) val);
          }
        } else if (last_postblank) {
          printWord(sf.substring(0, sf.length() - input_buffer.diffPrevPos(last)), lf.toString(), output);
          output.append(' ');
          input_buffer.setPos(last);
          input_buffer.back(1);
        } else if (last_preblank) {
          output.append(' ');
          printWord(sf.substring(0, sf.length() - input_buffer.diffPrevPos(last)), lf.toString(), output);
          input_buffer.setPos(last);
          input_buffer.back(1);
        } else if (last_incond) {
          printWord(sf.substring(0, sf.length() - input_buffer.diffPrevPos(last)), lf.toString(), output);
          input_buffer.setPos(last);
          input_buffer.back(1);
        } else if (isAlphabetic(val) && ((sf.length() - input_buffer.diffPrevPos(last)) > lastBlank(sf) || lf.length() == 0)) {
          do {
            sf.append(alphabet.getSymbol(val));
          } while (((val = readAnalysis(input)) != (char) 0) && isAlphabetic(val));

          int limit = firstNotAlpha(sf);
          int size = sf.length();
          limit = (limit == Integer.MAX_VALUE ? size : limit);
          if (limit == 0) {
            input_buffer.back(sf.length());
            output.append(sf.charAt(0));
          } else {
            input_buffer.back(1 + (size - limit));
            String unknownWord = sf.substring(0, limit);
            if (do_decomposition) {
              String compound = compoundAnalysis2(unknownWord);
              if (compound != null) {
                printWord(unknownWord, compound, output);
              } else {
                printUnknownWord(unknownWord, output);
              }
            } else {
              printUnknownWord(unknownWord, output);
            }
          }
        } else if (lf.length() == 0) {
          int limit = firstNotAlpha(sf);
          int size = sf.length();
          limit = (limit == Integer.MAX_VALUE ? size : limit);
          if (limit == 0) {
            input_buffer.back(sf.length());
            output.append(sf.charAt(0));
          } else {
            input_buffer.back(1 + (size - limit));
            //printUnknownWord(sf.substring(0, limit), output);
            String unknownWord = sf.substring(0, limit);
            if (do_decomposition) {
              String compound = compoundAnalysis2(unknownWord);
              if (compound != null) {
                printWord(unknownWord, compound, output);
              } else {
                printUnknownWord(unknownWord, output);
              }
            } else {
              printUnknownWord(unknownWord, output);
            }
          }
        } else {
          printWord(sf.substring(0, sf.length() - input_buffer.diffPrevPos(last)), lf.toString(), output);
          input_buffer.setPos(last);
          input_buffer.back(1);
        }

        current_state.copy(initial_state);
        lf.setLength(0);
        sf.setLength(0);
        last_incond = last_postblank = last_preblank = false;
      }
    }

    // print remaining blanks
    flushBlanks(output);
  }

  public String compoundAnalysisOld(String input_word) {
    // Francis' heuristic
    if (input_word.length() < 9) {
      return null;
    }

    State current_state = initial_state.copy();
    //input_word += " ";

    // List compound elements. Each element can have multiple alternatives
    ArrayList<String[]> compoundElements = new ArrayList<String[]>();

    boolean firstupper = Alphabet.isUpperCase(input_word.charAt(0));
    boolean uppercase = firstupper && Alphabet.isUpperCase(input_word.charAt(1));

    for (int i = 0; i <= input_word.length(); i++) {
      boolean endOfWord = (i == input_word.length());

      State previous_state = new State().copy(current_state);

      if (!endOfWord) {
        char val = input_word.charAt(i);
        current_state.step_case(val, caseSensitive);
      }

      String result = previous_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
      if (DEBUG) {
        System.err.println(i + " result = " + result);
      }

      if (current_state.size() == 0 || endOfWord) {
        // longest match exceeded has come - or end of word

        if (previous_state.isFinal()) {

          String[] alternatives = result.substring(1).split("/");
          // Add array of possible analyses
          compoundElements.add(alternatives);

          // start over
          if (!endOfWord) {
            current_state.copy(initial_state);
            // do again on current char
            i--;
          }
        } else {
          // word is not present
          return null;
        }
      }

      // Francis' heuristic
      if (compoundElements.size() > 2) {
        return null;
      }
    }
    if (DEBUG) {
      System.err.println("compoundElements = " + compoundElements);
    }

    final int MAX_COMBINATIONS = 500;
    int combinations = 1;

    // Build list of combination tuples
    ArrayList<String> tuples = null;
    for (String[] arr : compoundElements) {
      // abort if too many alternatives
      combinations *= arr.length;
      if (combinations > MAX_COMBINATIONS) {
        System.err.println("Warning: compoundAnalysis' MAX_COMBINATIONS exceeded for " + input_word);
        return null;
      }

      if (tuples == null) {
        tuples = new ArrayList<String>();
        for (String part : arr) {
          tuples.add(part);
        }
      } else {
        ArrayList<String> tuples2 = new ArrayList<String>(tuples.size() * arr.length);

        if (DEBUG) {
          System.err.println("tuples.size() * arr.length = " + tuples.size() * arr.length);
        }
        for (String head : tuples) {
          for (String part : arr) {
            tuples2.add(head + "+" + part);
          }
        }
        tuples = tuples2;
      }
    }

    // build resulting string
    StringBuilder result = new StringBuilder();
    result.delete(0, result.length());
    for (String word : tuples) {
      result.append('/');
      result.append(word);
    }

    if (DEBUG) {
      System.err.println("compoundAnalysis(" + input_word);
    }
    if (DEBUG) {
      System.err.println(result);
    }
    return result.toString();
  }

  public String compoundAnalysis2(String input_word) {
    final int MAX_COMBINATIONS = 500;

    if (DEBUG) {
      System.err.println(" compoundAnalysis2(input_word = " + input_word);
    }

    State current_state = initial_state.copy();

    boolean firstupper = Alphabet.isUpperCase(input_word.charAt(0));
    boolean uppercase = firstupper && input_word.length() > 1 && Alphabet.isUpperCase(input_word.charAt(1));

    for (int i = 0; i < input_word.length(); i++) {
      char val = input_word.charAt(i);

      if (DEBUG) {
        System.err.println(val + " før step " + i + " current_state = " + current_state);
        current_state.consistency_check();
      }
      current_state.step_case(val, caseSensitive);
      if (current_state.size() > MAX_COMBINATIONS) {
        System.err.println("Warning: compoundAnalysis' MAX_COMBINATIONS exceeded for '" + input_word + "'");
        System.err.println("         gave up at char " + i + " '" + val + "'. Here are first 20 states:'");
        System.err.println(current_state.state.subList(0, 20));

        return null;
      }
      if (DEBUG) {
        System.err.println(val + " eft step " + i + " current_state = " + current_state);
      }
      String result = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);

      if (i < input_word.length() - 1) {
        current_state.restartFinals(compoundOnlyLSymbol, initial_state, '+');
      }
      if (DEBUG) {
        System.err.println(val + " eft rest " + i + " current_state = " + current_state);
        current_state.consistency_check();
      }
      if (DEBUG) {
        System.err.println(i + " result = " + result);
      }
      if (DEBUG) {
        result = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
      }
      if (DEBUG) {
        System.err.println(i + " result = " + result);
      }
      if (DEBUG) {
        System.err.println("-- size=" + current_state.size());
      }
      if (current_state.size() == 0) {
        // word is not present
        return null;
      }
    }
    current_state.pruneCompounds(compoundRSymbol, '+', compound_max_elements);

    String result = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
    if (DEBUG) {
      System.err.println("rrresult = " + result.replaceAll("/", "/\n"));
    }
    if (result.length() > 0) {
      return result;
    }
    return null;
  }

  private void analysis_wrapper_null_flush(Reader input, Appendable output) throws IOException {
    setNullFlush(false);
    while (input.ready()) {
      analysis(input, output);
      output.append('\0');
      IOUtils.flush(output);
    }
  }

  private void generation_wrapper_null_flush(Reader input, Appendable output, GenerationMode mode) throws IOException {
    setNullFlush(false);
    while (input.ready()) {
      generation(input, output, mode);
      output.append('\0');
      IOUtils.flush(output);
    }
  }

  private void postgeneration_wrapper_null_flush(Reader input, Appendable output) throws IOException {
    setNullFlush(false);
    while (input.ready()) {
      postgeneration(input, output);
      output.append('\0');
      IOUtils.flush(output);
    }
  }

  private void transliteration_wrapper_null_flush(Reader input, Appendable output) throws IOException {
    setNullFlush(false);
    while (input.ready()) {
      transliteration(input, output);
      output.append('\0');
      IOUtils.flush(output);
    }
  }

  private void tm_analysis(Reader input, Appendable output) throws IOException {

    State current_state = initial_state.copy();
    String lf = "";
    String sf = "";
    int last = 0;

    char val;
    while ((val = readAnalysis(input)) != (char) 0) {
      // test for final states
      if (current_state.isFinal()) {
        if (iswpunct(val)) {
          lf = current_state.filterFinalsTM(alphabet, escaped_chars, blankqueue, tmNumbers).substring(1);
          last = input_buffer.getPos();
          tmNumbers.clear();
        }
      } else if (sf.equals("") && Alphabet.isSpaceChar(val)) {
        lf += sf;
        last = input_buffer.getPos();
      }

      current_state.step_case(val, false);

      if (current_state.size() != 0) {
        if (val == EOF) {
          sf += tmNumbers.get(tmNumbers.size() - 1);
        } else if (isLastBlankTM && val == ' ') {
          sf += blankqueue.getLast();
        } else {
          sf = sf + alphabet.getSymbol(val);
        }
      } else {
        if ((Alphabet.isWhitespace(val) || iswpunct(val)) && sf.equals("")) {
          if (Alphabet.isWhitespace(val)) {
            printSpace(val, output);
          } else {
            if (isEscaped(val)) {
              output.append('\\');
            }
            output.append(val);
          }
        } else if (!Alphabet.isWhitespace(val) && !iswpunct(val)
            && ((sf.length() - input_buffer.diffPrevPos(last)) > lastBlank(sf)
            || lf.equals(""))) {

          do {
            if (val == EOF) {
              sf += tmNumbers.get(tmNumbers.size() - 1);
            } else if (isLastBlankTM && val == ' ') {
              sf += (blankqueue.getLast());
            } else {
              sf += alphabet.getSymbol(val);
            }
          } while (((val = readTMAnalysis(input)) != (char) 0) && !Alphabet.isWhitespace(val) && !iswpunct(val));

          if (val == 0) {
            output.append(sf);
            return;
          }
          input_buffer.back(1);
          output.append(sf);
          while (blankqueue.size() > 0) {
            if (blankqueue.size() == 1 && isLastBlankTM) {
              break;
            }
            blankqueue.removeLast();
          }
        } else if (lf.equals("")) {
          input_buffer.back(1);

          output.append(sf);
          while (blankqueue.size() > 0) {
            if (blankqueue.size() == 1 && isLastBlankTM) {
              break;
            }
            blankqueue.removeLast();
          }
        } else {

          output.append('[');
          output.append(lf);
          output.append(']');
          input_buffer.setPos(last);
          input_buffer.back(1);
        }
        current_state.copy(initial_state);
        lf = "";
        sf = "";
      }
    }

    // print remaining blanks
    flushBlanks(output);
  }

  public synchronized void generation(Reader input, Appendable output, GenerationMode mode) throws IOException {
    if (getNullFlush()) {
      generation_wrapper_null_flush(input, output, mode);
    }

    State current_state = initial_state.copy();
    StringBuilder sf = new StringBuilder();

    outOfWord = false;

    skipUntil(input, output, '^');
    int val;
    while ((val = readGeneration(input, output)) != 0x7fffffff) {
      if (sf.length() == 0 && val == '=') {
        output.append('=');
        val = readGeneration(input, output);
      }
      if (val == '$' && outOfWord) {
        if (sf.length() == 0) {
          // nothing inside, so output nothing
        } else {
          char ch0 = sf.charAt(0);
          if (ch0 == '*' || ch0 == '%') {
            if (mode != GenerationMode.gm_clean) {
              writeEscaped(sf, output);
            } else {
              writeEscaped(sf.substring(1), output);
            }
          } else if (ch0 == '@') {
            if (mode == GenerationMode.gm_all) {
              writeEscaped(sf, output);
            } else if (mode == GenerationMode.gm_clean) {
              writeEscaped(removeTags(sf.substring(1)), output);
            } else if (mode == GenerationMode.gm_unknown) {
              writeEscaped(removeTags(sf), output);
            }
          } else if (current_state.isFinal()) {
            boolean uppercase = sf.length() > 1 && Alphabet.isUpperCase(sf.charAt(1));
            boolean firstupper = Alphabet.isUpperCase(ch0);
            if (mode == GenerationMode.gm_tagged) {
              output.append('^');
            }
            if (do_flagMatch) {
              deleteStatesWithConflictingFlags(current_state);
            }
            output.append(current_state.filterFinals(alphabet,
                escaped_chars, uppercase, firstupper).substring(1));
            if (mode == GenerationMode.gm_tagged) {
              output.append('/');
              output.append(sf);
              output.append('$');
            }

          } else {
            if (mode == GenerationMode.gm_all) {
              output.append('#');
              writeEscaped(sf, output);
            } else if (mode == GenerationMode.gm_clean) {
              writeEscaped(removeTags(sf), output);
            } else if (mode == GenerationMode.gm_unknown) {
              output.append('#');
              writeEscaped(removeTags(sf), output);
            }
          }
        }

        current_state.copy(initial_state);
        sf.setLength(0);
      } else if (Alphabet.isSpaceChar((char) val) && sf.length() == 0) {
        // do nothing
      } else if (sf.length() > 0 && (sf.charAt(0) == '*' || sf.charAt(0) == '%')) {
        sf.append(alphabet.getSymbol(val));
      } else {
        sf.append(alphabet.getSymbol(val));
        current_state.step_case(val, caseSensitive);
      }
    }
  }

  public synchronized void postgeneration(Reader input, Appendable output) throws IOException {
    if (getNullFlush()) {
      postgeneration_wrapper_null_flush(input, output);
    }

    boolean skip_mode = true;
    State current_state = initial_state.copy();
    StringBuilder lf = new StringBuilder();
    String sf = "";
    SetOfCharacters empty_escaped_chars = new SetOfCharacters();
    int last = 0;

    char val;
    while ((val = readPostgeneration(input)) != (char) 0) {

      if (val == '~') {
        skip_mode = false;
      }

      if (skip_mode) {
        if (Alphabet.isSpaceChar(val)) {
          printSpace(val, output);
        } else {
          if (isEscaped(val)) {
            output.append('\\');
          }
          output.append(val);
        }
      } else {
        // test for final states
        if (current_state.isFinal()) {
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(1));
          boolean uppercase = sf.length() > 1 && firstupper && Alphabet.isUpperCase(sf.charAt(2));
          lf = new StringBuilder(current_state.filterFinals(alphabet, empty_escaped_chars, uppercase, firstupper));

          // case of the beggining of the next word

          String mybuf = "";
          for (int i = sf.length() - 1; i >= 0; i--) {
            if (!Alphabet.isLetter(sf.charAt(i))) {
              break;
            } else {
              mybuf = sf.charAt(i) + mybuf;
            }
          }

          if (mybuf.length() > 0) {
            boolean myfirstupper = Alphabet.isUpperCase(mybuf.charAt(0));
            boolean myuppercase = mybuf.length() > 1 && Alphabet.isUpperCase(mybuf.charAt(1));

            for (int i = lf.length() - 1; i >= 0; i--) {
              if (!Alphabet.isLetter(lf.charAt(i))) {
                if (myfirstupper && i != lf.length() - 1) {
                  lf.setCharAt(i + 1, Alphabet.toUpperCase(lf.charAt(i + 1)));
                } else {
                  lf.setCharAt(i + 1, Alphabet.toLowerCase(lf.charAt(i + 1)));
                }
                break;
              } else {
                if (myuppercase) {
                  lf.setCharAt(i, Alphabet.toUpperCase(lf.charAt(i)));
                } else {
                  lf.setCharAt(i, Alphabet.toLowerCase(lf.charAt(i)));
                }
              }
            }
          }

          last = input_buffer.getPos();
        }

        current_state.step_case(val, caseSensitive);

        if (current_state.size() != 0) {
          sf += alphabet.getSymbol(val);
        } else {
          if (lf.length() == 0) {
            int mark = sf.length();
            for (int i = 1, limit = sf.length(); i < limit; i++) {
              if (sf.charAt(i) == '~') {
                mark = i;
                break;
              }
            }
            output.append(sf.substring(1, mark - 1 + 1));
            if (mark == sf.length()) {
              input_buffer.back(1);
            } else {
              input_buffer.back(sf.length() - mark);
            }
          } else {
            output.append(lf.substring(1, lf.length() - 3 + 1));
            input_buffer.setPos(last);
            input_buffer.back(2);
            val = lf.charAt(lf.length() - 2);
            if (Alphabet.isSpaceChar(val)) {
              printSpace(val, output);
            } else {
              if (isEscaped(val)) {
                output.append('\\');
              }
              output.append(val);
            }
          }

          current_state.copy(initial_state);
          lf = new StringBuilder("");
          sf = "";
          skip_mode = true;
        }
      }
    }

    // print remaining blanks
    flushBlanks(output);
  }

  public synchronized void transliteration(Reader input, Appendable output) throws IOException {
    if (getNullFlush()) {
      transliteration_wrapper_null_flush(input, output);
    }

    State current_state = initial_state.copy();
    String lf = "";
    String sf = "";
    int last = 0;

    char val;
    while ((val = readPostgeneration(input)) != (char) 0) {
      if (iswpunct(val) || Alphabet.isSpaceChar(val)) {
        boolean firstupper = false;
        boolean uppercase = false;
        // Not JDK 1.5 compliant: if (!sf.isEmpty()) {
        if (sf.length() > 0) {
          firstupper = Alphabet.isUpperCase(sf.charAt(0));
          uppercase = sf.length() > 1 && firstupper && Alphabet.isUpperCase(sf.charAt(1));
        }
        lf = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
        if (lf.length() > 0) {
          output.append(lf.substring(1));
          current_state.copy(initial_state);
          lf = "";
          sf = "";
        }
        if (Alphabet.isSpaceChar(val)) {
          printSpace(val, output);
        } else {
          if (isEscaped(val)) {
            output.append('\\');
          }
          output.append(val);
        }
      } else {
        if (current_state.isFinal()) {
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = sf.length() > 1 && firstupper && Alphabet.isUpperCase(sf.charAt(1));
          lf = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
          last = input_buffer.getPos();
        }

        current_state.step(val);
        if (current_state.size() != 0) {
          sf += alphabet.getSymbol(val);
        } else {
          if (lf.length() > 0) {
            output.append(lf.substring(1));
            input_buffer.setPos(last);
            input_buffer.back(1);
            val = lf.charAt(lf.length() - 1);
          } else {
            if (Alphabet.isSpaceChar(val)) {
              printSpace(val, output);
            } else {
              if (isEscaped(val)) {
                output.append('\\');
              }
              output.append(val);
            }
          }
          current_state.copy(initial_state);
          lf = "";
          sf = "";
        }
      }
    }
    // print remaining blanks
    flushBlanks(output);
  }

  public synchronized String biltrans(String input_word, boolean with_delim) {
    State current_state = initial_state.copy();
    StringBuilder result = new StringBuilder("");
    int start_point = 1;
    int end_point = input_word.length() - 2;
    StringBuilder queue = new StringBuilder("");
    boolean mark = false;

    if (!with_delim) {
      start_point = 0;
      end_point = input_word.length() - 1;
    }

    if (input_word.charAt(start_point) == '*') {
      return input_word;
    }

    if (input_word.charAt(start_point) == '=') {
      start_point++;
      mark = true;
    }
    boolean firstupper = Alphabet.isUpperCase(input_word.charAt(start_point));
    boolean uppercase = firstupper && Alphabet.isUpperCase(input_word.charAt(start_point + 1));

    for (int i = start_point; i <= end_point; i++) {
      int val;
      String symbol = "";

      if (input_word.charAt(i) == '\\') {
        i++;
        val = (int) (input_word.charAt(i));
      } else if (input_word.charAt(i) == '<') {
        symbol = "<";
        for (int j = i + 1; j <= end_point; j++) {
          symbol += input_word.charAt(j);
          if (input_word.charAt(j) == '>') {
            i = j;
            break;
          }
        }
        val = alphabet.cast(symbol);
      } else {
        val = (int) (input_word.charAt(i));
      }

      current_state.step_case(val, caseSensitive);

      if (current_state.isFinal()) {
        result = new StringBuilder(current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper));
        if (with_delim) {
          if (mark) {
            result = new StringBuilder("^=" + result.substring(1));
          } else {
            result.setCharAt(0, '^');
          }
        } else {
          if (mark) {
            result = new StringBuilder("=" + result.substring(1));
          } else {
            result = new StringBuilder(result.substring(1));
          }
        }
      }

      if (current_state.size() == 0) {
        if (!symbol.equals("") && !result.toString().equals("")) {
          queue.append(symbol);
        } else {
          // word is not present
          if (with_delim) {
            result = new StringBuilder("^@" + input_word.substring(1));
          } else {
            result = new StringBuilder("@" + input_word);
          }
          return result.toString();
        }
      }
    }

    // attach unmatched queue automatically

    if (queue.length() != 0) {
      StringBuilder result_with_queue = new StringBuilder("");
      boolean multiple_translation = false;
      for (int i = 0, limit = result.length(); i != limit; i++) {
        switch (result.charAt(i)) {
          case '\\':
            result_with_queue.append('\\');
            i++;
            break;

          case '/':
            result_with_queue.append(queue);
            multiple_translation = true;
            break;

          default:
            break;
        }
        result_with_queue.append(result.charAt(i));
      }
      result_with_queue.append(queue);

      if (with_delim) {
        result_with_queue.append('$');
      }
      return result_with_queue.toString();
    } else {
      if (with_delim) {
        result.append('$');
      }
      return result.toString();
    }
  }

  /**
   * @param input ^Jeg<prn><p1><mf><sg><nom>$ ^ha<vblex><pres>$ ^ikke<adv>$
   * @param output ^Jeg<prn><p1><mf><sg><nom>/Prpers<prn><p1><mf><sg><nom>$ ^ha<vblex><pres>/have<vbhaver><pres>$ ^ikke<adv>/not<adv>$
   * @throws IOException
   */
  public synchronized void bilingual(Reader input, Appendable output) throws IOException {
    /* XXX TODO
     * if (getNullFlush()) {
     * transliteration_wrapper_null_flush(input, output);
     * } */

    State current_state = initial_state.copy();
    StringBuilder sf = new StringBuilder(200);    // source language analysis
    StringBuilder queue = new StringBuilder(200); // symbols to be added to each target
    StringBuilder result = new StringBuilder(200);// result of looking up analysis in bidix

    outOfWord = false;
    skipUntil(input, output, '^');
    Pair<String, Integer> tr;                    // readBilingual return value, containing:
    int val;                                     // the alphabet value of current symbol, and
    String symbol = "";                          // the current symbol as a string
    boolean seentags = false;                    // have we seen any tags at all in the analysis?

    boolean seensurface = false;
    String surface = "";

    while (true) {
      tr = readBilingual(input, output);
      symbol = tr.first;
      val = tr.second;
      if (biltransSurfaceForms && !seensurface && !outOfWord) {
        while (val != '/' && val != 0x7fffffff) {
          surface = surface + symbol;
          surface = alphabet.getSymbol(val);
          tr = readBilingual(input, output);
          symbol = tr.first;
          val = tr.second;
        }
        seensurface = true;
        tr = readBilingual(input, output);
        symbol = tr.first;
        val = tr.second;
      }

      if (val == 0x7fffffff) {
        break;
      }

      if (val == '$' && outOfWord) {
        if (!seentags) {       // if no tags: only return complete matches
          boolean uppercase = (sf.length() > 1) && Alphabet.isUpperCase(sf.charAt(1));
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));

          result = current_state.filterFinals(result, alphabet, escaped_chars, uppercase, firstupper);
          /*
           * result = current_state.filterFinals(all_finals, alphabet,
           * escaped_chars,
           * uppercase, firstupper, 0);
           */
        }
        if (sf.charAt(0) == '*') {
          printWordBilingual(sf.toString(), "/" + sf, output);
        } else if (result.length() > 0) {
          printWordBilingual(sf.toString(), result.toString() + queue.toString(), output);
        } else {
          if (biltransSurfaceForms) {
            printWordBilingual(surface, "/@" + surface, output);
          } else {
            printWordBilingual(sf.toString(), "/@" + sf, output);
          }
        }
        seensurface = false;
        surface = "";
        queue.setLength(0);
        result.setLength(0);
        current_state = initial_state.copy();
        sf.setLength(0);
        seentags = false;
      } else if (Alphabet.isWhitespace((char) val) && sf.length() == 0) {
        // do nothing
      } else if (sf.length() > 0 && sf.charAt(0) == '*') {
        if (isEscaped((char) val)) {
          sf.append('\\');
        }
        sf.append(alphabet.getSymbol(val));  // add symbol to sf iff alphabetic
        if (val == 0) // non-alphabetic, possibly unknown tag; add to sf
        {
          sf.append(symbol);
        }
      } else {
        if (isEscaped((char) val)) {
          sf.append('\\');
        }
        sf.append(alphabet.getSymbol(val));  // add symbol to sf iff alphabetic
        if (val == 0) // non-alphabetic, possibly unknown tag; add to sf
        {
          sf.append(symbol);
        }
        if (alphabet.isTag(val) || val == 0) {
          seentags = true;
        }
        if (current_state.size() != 0) {
          if (!alphabet.isTag(val) && Alphabet.isUpperCase(val) && !caseSensitive) {
            current_state.step(val, Alphabet.toLowerCase(val));
          } else {
            current_state.step(val);
          }
        }
        if (current_state.isFinal()) {

          boolean uppercase = (sf.length() > 1) && Alphabet.isUpperCase(sf.charAt(1));
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));

          result = current_state.filterFinals(result, alphabet, escaped_chars, uppercase, firstupper);
        }

        if (current_state.size() == 0 && result.length() > 0) {
          // We already have a result, but there is still more to read
          // of the analysis; following tags are not consumed, but
          // output as target language tags (added to result on
          // end-of-word)
          if (alphabet.isTag(val)) // known tag
          {
            queue.append(alphabet.getSymbol(val));
          } else if (val == 0) // non-alphabetic, possibly unknown tag
          {
            queue.append(symbol);
          } else {
            // There are no more alive transductions and the current symbol is not a tag -- unknown word!
            result.setLength(0);
          }
        }
      }
    }

    // print remaining blanks
    flushBlanks(output);
  }

  public synchronized Pair<String, Integer> biltransWithQueue(String input_word, boolean with_delim) {
    State current_state = initial_state.copy();
    StringBuilder result = new StringBuilder();
    StringBuilder queue = new StringBuilder();
    boolean mark = false;

    int start_point, end_point;


    if (with_delim) {
      start_point = 1;
      end_point = input_word.length() - 2;
    } else {
      start_point = 0;
      end_point = input_word.length() - 1;
    }

    char ch = input_word.charAt(start_point);
    if (ch == '*') {
      return new Pair<String, Integer>(input_word, 0);
    }

    if (ch == '=') {
      start_point++;
      mark = true;
    }

    boolean firstupper = Alphabet.isUpperCase(ch);
    boolean uppercase = firstupper && Alphabet.isUpperCase(input_word.charAt(start_point + 1));

    for (int i = start_point; i <= end_point; i++) {
      int val = 0;
      String symbol = "";
      ch = input_word.charAt(i);
      if (ch == '\\') {
        i++;
        val = input_word.charAt(i);
      } else if (ch == '<') {
        //symbol = "<";
        int j = i + 1;
        for (; j <= end_point; j++) {
          char ch_j = input_word.charAt(j);
          //symbol += ch_j;
          if (ch_j == '>') {
            break;
          }
        }
        symbol = input_word.substring(i, j + 1);
        i = j;
        val = alphabet.cast(symbol);
      } else {
        val = ch;
      }

      current_state.step_case(val, caseSensitive);

      if (current_state.isFinal()) {
        String res0 = current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper);
        if (with_delim) {
          if (mark) {
            result = new StringBuilder("^=");
            result.append(res0.substring(1));
          } else {
            result = new StringBuilder(res0);
            result.setCharAt(0, '^');
          }
        } else {
          if (mark) {
            result = new StringBuilder(res0);
            //result = new StringBuilder("=" + result.substring(1));
            result.setCharAt(0, '=');
          } else {
            result = new StringBuilder(res0.substring(1));
          }
        }
      }

      if (current_state.size() == 0) {
        if (symbol.length() > 0 && !(result.length() == 0)) {
          queue.append(symbol);
        } else {
          // word is not present
          if (with_delim) {
            return new Pair<String, Integer>("^@" + input_word.substring(1), 0);
          } else {
            return new Pair<String, Integer>("@" + input_word, 0);
          }
        }
      }
    }

    // attach unmatched queue automatically

    if (queue.length() > 0) {
      StringBuilder result_with_queue = new StringBuilder();
      boolean multiple_translation = false;
      for (int i = 0, limit = result.length(); i != limit; i++) {
        ch = result.charAt(i);
        switch (ch) {
          case '\\':
            result_with_queue.append('\\');
            i++;
            break;

          case '/':
            result_with_queue.append(queue);
            multiple_translation = true;
            break;

          default:
            break;
        }
        result_with_queue.append(ch);
      }
      result_with_queue.append(queue);

      if (with_delim) {
        result_with_queue.append('$');
      }
      return new Pair<String, Integer>(result_with_queue.toString(), queue.length());
    } else {
      if (with_delim) {
        result.append('$');
      }
      return new Pair<String, Integer>(result.toString(), 0);
    }
  }

  public synchronized String biltransWithoutQueue(String input_word, boolean with_delim) {
    State current_state = initial_state.copy();
    StringBuilder result = new StringBuilder("");
    int start_point = 1;
    int end_point = input_word.length() - 2;
    boolean mark = false;

    if (!with_delim) {
      start_point = 0;
      end_point = input_word.length() - 1;
    }

    if (input_word.charAt(start_point) == '*') {
      return input_word;
    }

    if (input_word.charAt(start_point) == '=') {
      start_point++;
      mark = true;
    }

    boolean firstupper = Alphabet.isUpperCase(input_word.charAt(start_point));
    boolean uppercase = firstupper && Alphabet.isUpperCase(input_word.charAt(start_point + 1));

    for (int i = start_point; i <= end_point; i++) {
      int val;
      String symbol = "";

      if (input_word.charAt(i) == '\\') {
        i++;
        val = (int) (input_word.charAt(i));
      } else if (input_word.charAt(i) == '<') {
        symbol = "<";
        for (int j = i + 1; j <= end_point; j++) {
          symbol += input_word.charAt(j);
          if (input_word.charAt(j) == '>') {
            i = j;
            break;
          }
        }
        val = alphabet.cast(symbol);
      } else {
        val = (int) (input_word.charAt(i));
      }

      current_state.step_case(val, caseSensitive);

      if (current_state.isFinal()) {
        result = new StringBuilder(current_state.filterFinals(alphabet, escaped_chars, uppercase, firstupper));
        if (with_delim) {
          if (mark) {
            result = new StringBuilder("^=" + result.substring(1));
          } else {
            result.setCharAt(0, '^');
          }
        } else {
          if (mark) {
            result = new StringBuilder("=" + result.substring(1));
          } else {
            result = new StringBuilder(result.substring(1));
          }
        }
      }

      if (current_state.size() == 0) {
        if (symbol.equals("")) {
          // word is not present
          if (with_delim) {
            result = new StringBuilder("^@" + input_word.substring(1));
          } else {
            result = new StringBuilder("@" + input_word);
          }
          return result.toString();
        }
      }
    }

    if (with_delim) {
      result.append('$');
    }
    return result.toString();
  }

  char readSAO(Reader input) throws IOException {
    if (!input_buffer.isEmpty()) {
      return input_buffer.next();
    }

    if (!input.ready()) {
      return (char) 0;
    }
    char val = (char) input.read(); // XXX
    //System.out.println("read "+val);


    if (escaped_chars.contains(val)) {
      if (val == '<') {
        String str = readFullBlock(input, '<', '>');
        if (str.substring(0, 9).equals("<![CDATA[")) {
          while (!"]]>".equals(str.substring(str.length() - 3))) {
            str += readFullBlock(input, '<', '>').substring(1);
          }
          blankqueue.addLast(str);
          input_buffer.add((' '));
          return (int) (' ');
        } else {
          streamError();
        }
      } else if (val == '\\') {
        val = (char) input.read(); // XXX
        //System.out.println("read "+val);
        if (isEscaped(val)) {
          input_buffer.add(val);
          return (val);
        } else {
          streamError(val);
        }
      } else {
        streamError(val);
      }
    }

    input_buffer.add(val);
    return (val);
  }

  void printSAOWord(String lf, Appendable output) throws IOException {
    for (int i = 1, limit = lf.length(); i != limit; i++) {
      if (lf.charAt(i) == '/') {
        break;
      }
      output.append(lf.charAt(i));
    }
  }

  public void SAO(Reader input, Appendable output) throws IOException {
    boolean last_incond = false;
    boolean last_postblank = false;
    State current_state = initial_state.copy();
    String lf = "";
    String sf = "";
    int last = 0;

    escaped_chars.clear();
    escaped_chars.add('\\');
    escaped_chars.add(('<'));
    escaped_chars.add(('>'));

    char val;
    while ((val = readSAO(input)) != (char) 0) {
      // test for final states
      if (current_state.isFinal()) {
        if (current_state.isFinal(inconditional)) {
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));

          lf = current_state.filterFinalsSAO(alphabet, escaped_chars, uppercase, firstupper, 0);

          last_incond = true;
          last = input_buffer.getPos();
        } else if (current_state.isFinal(postblank)) {
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));

          lf = current_state.filterFinalsSAO(alphabet, escaped_chars, uppercase, firstupper, 0);

          last_postblank = true;
          last = input_buffer.getPos();
        } else if (!isAlphabetic(val)) {
          boolean firstupper = Alphabet.isUpperCase(sf.charAt(0));
          boolean uppercase = firstupper && Alphabet.isUpperCase(sf.charAt(sf.length() - 1));

          lf = current_state.filterFinalsSAO(alphabet, escaped_chars, uppercase, firstupper, 0);

          last_postblank = false;
          last_incond = false;
          last = input_buffer.getPos();
        }
      } else if (sf.equals("") && Alphabet.isSpaceChar(val)) {
        lf = "/*" + sf;
        last_postblank = false;
        last_incond = false;
        last = input_buffer.getPos();
      }

      current_state.step_case(val, caseSensitive);

      if (current_state.size() != 0) {
        sf += alphabet.getSymbol(val);
      } else {
        if (!isAlphabetic(val) && sf.equals("")) {
          if (Alphabet.isSpaceChar(val)) {
            printSpace(val, output);
          } else {
            if (isEscaped(val)) {
              output.append('\\');
            }
            output.append(val);
          }
        } else if (last_incond) {
          printSAOWord(lf, output);
          input_buffer.setPos(last);
          input_buffer.back(1);
        } else if (last_postblank) {
          printSAOWord(lf, output);
          output.append(' ');
          input_buffer.setPos(last);
          input_buffer.back(1);
        } else if (isAlphabetic(val) && ((sf.length() - input_buffer.diffPrevPos(last)) > lastBlank(sf) || lf.equals(""))) {
          do {
            sf += alphabet.getSymbol(val);
          } while ((val = readSAO(input)) != (char) 0 && isAlphabetic(val));

          int limit = firstNotAlpha(sf);
          int size = sf.length();
          limit = (limit == Integer.MAX_VALUE ? size : limit);
          input_buffer.back(1 + (size - limit));
          output.append("<d>");
          output.append(sf);
          output.append("</d>");
        } else if (lf.equals("")) {
          int limit = firstNotAlpha(sf);
          int size = sf.length();
          limit = (limit == Integer.MAX_VALUE ? size : limit);
          input_buffer.back(1 + (size - limit));
          output.append("<d>").append(sf).append("</d>");
        } else {
          printSAOWord(lf, output);
          input_buffer.setPos(last);
          input_buffer.back(1);
        }

        current_state.copy(initial_state);
        lf = "";
        sf = "";
        last_incond = false;
        last_postblank = false;
      }
    }

    // print remaining blanks
    flushBlanks(output);
  }

  CharSequence removeTags(CharSequence str) {
    char chlast = 0;
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      //if (ch == '<' && i >= 1 && str.charAt(i - 1) != '\\') {
      if (ch == '<' && chlast != '\\') {
        return str.subSequence(0, i);
      }
      chlast = ch;
    }

    return str;
  }

  public void setBiltransSurfaceForms(boolean value) {
    biltransSurfaceForms = value;
  }

  public void setCaseSensitiveMode(boolean value) {
    caseSensitive = value;
  }

  public void setFlagMatchMode(boolean b) {
    do_flagMatch = b;
  }

  public void setNullFlush(boolean value) {
    if (value)
      System.err.println("sorry, null flush is currently not supported.");
    nullFlush = false; // value;
  }

  public boolean getNullFlush() {
    return nullFlush;
  }

  int firstNotAlpha(CharSequence sf) {
    for (int i = 0, limit = sf.length(); i < limit; i++) {
      if (!isAlphabetic(sf.charAt(i))) {
        return i;
      }
    }
    //throw new RuntimeException("Should not have gotten here");
    return Integer.MAX_VALUE;
  }

  private char xread(Reader input) throws IOException {
    return (char) input.read();
  }

  private boolean iswdigit(char val) {
    int i = (int) val;
    return ((i >= 48 && i <= 57) || i == 178 || i == 179 || i == 185);
  }

  private boolean iswpunct(char val) {
    int i = (int) val;
    return ((i >= 161 && i <= 191)
        || (i >= 33 && i <= 47)
        || (i >= 58 && i <= 64)
        || (i >= 91 && i <= 96)
        || (i >= 123 && i <= 126));
  }
}
