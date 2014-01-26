/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lex;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.Compression;
import org.apertium.lttoolbox.process.Node;
import org.apertium.lttoolbox.process.SetOfCharacters;
import org.apertium.lttoolbox.process.State;
import org.apertium.lttoolbox.process.TransducerExe;
import static org.apertium.utils.IOUtils.*;

/**
 *
 * @author j
 */
class LRXProcessor {
  Alphabet alphabet;
  TransducerExe transducer;
  HashMap<String, TransducerExe> recognisers = new HashMap<String, TransducerExe>();
  HashSet<TransducerExe> anfinals = new HashSet<TransducerExe>();
  /**
   * Initial state of every token
   */
  private State initial_state = new State();
  /**
   * Set of characters to escape with a backslash
   */
  private SetOfCharacters escaped_chars = new SetOfCharacters();
  private boolean debugMode;
  private boolean outOfWord;
  private boolean DEBUG = true;
  private int ANY_TAG;
  private int ANY_CHAR;

  void setTraceMode(boolean b) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  void setDebugMode(boolean b) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  void load(ByteBuffer in) throws IOException {
    alphabet = Alphabet.read(in);
    D(alphabet);
    ANY_TAG = alphabet.cast("<ANY_TAG>");
    ANY_CHAR = alphabet.cast("<ANY_CHAR>");
    LOG("ANY_TAG=" + ANY_TAG);
    LOG("ANY_CHAR=" + ANY_CHAR);


    int len = Compression.multibyte_read(in); // numer of letters (52)
    while (len > 0) {
      int len2 = Compression.multibyte_read(in);
      String name = "";
      while (len2 > 0) {
        name += (char) Compression.multibyte_read(in);
        len2--;
      }
      TransducerExe tx = new TransducerExe();
      tx.read(in, alphabet);
      recognisers.put(name, tx);
      D(name + " -> " + tx.getFinals());
      len--;
    }


    D("recognisers: " + recognisers.size());

    int len2 = Compression.multibyte_read(in);
    String name = "";
    while (len2 > 0) {
      name += (char) Compression.multibyte_read(in);
      len2--;
    }
    transducer = new TransducerExe();
    transducer.read(in, alphabet);
    transducer.show_DEBUG(alphabet);
    D(name + " -> " + transducer);
    // Now read in weights
//...
  }

  private void streamError() throws IOException {
    throw new IOException("streamError");
  }

  private void LOG(Object string) {
    System.err.println(string);
  }

  private void D(Object string) {
    if (!DEBUG)
      return;
    System.err.println(string);
  }

  private char eofRead(Reader input) throws IOException {
    int c = input.read();
    if (c == -1)
      throw new EOFException();
    return (char) c;
  }

  public void init() {
    initial_state.init(transducer);

    anfinals.add(transducer);

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
  }

  private char readEscaped(Reader input) throws IOException {
    char val = eofRead(input);

    if (!escaped_chars.contains(val)) {
      streamError();
    }

    return val;
  }

  private String readFullBlock(Reader input, char delim2) throws IOException {
    StringBuilder result = new StringBuilder();
    char ch = 0;
    while (ch != delim2) {
      ch = eofRead(input);
      result.append(ch);
      if (ch == '\\') {
        result.append(readEscaped(input));
      }
    }
    return result.toString();
  }

  private void skipUntil(Reader input, Appendable output, char character) throws IOException {
    while (true) {
      char val = eofRead(input);

      if (val == character) {
        return;
      } else if (val == '\\') {
        output.append(val);
        output.append(eofRead(input));
      } else if (val == '[') {
        output.append(val);
        skipUntil(input, output, ']');
        output.append(']');
      } else {
        output.append(val);
      }
    }
  }
  /*
   * echo "^liten<adj><posi><mf><sg><ind>/small<adj><sint><posi><mf><sg><ind>/little<adj><sint><posi><mf><sg><ind>$" | lrx-proc rules.bin
   * ^liten<adj><posi><mf><sg><ind>/little<adj><sint><posi><mf><sg><ind>$
   */

  public static void main(String[] argv) throws Exception {
    Reader input = new StringReader("hej [sd\\$sd \\^sdsd\\$] oh ^liten<adj><posi><mf><sg><ind>/small<adj><sint><posi><mf><sg><ind>/little<adj><sint><posi><mf><sg><ind>$");
    Appendable output = new StringWriter();
    LRXProcessor lrxp = new LRXProcessor();
    //lrxp.load(openFileAsByteBuffer("/home/j/esperanto/apertium/nursery/apertium-no-en/no-en.lrx.bin"));
    lrxp.load(openFileAsByteBuffer("/home/j/esperanto/apertium/nursery/apertium-no-en/rules.bin"));
    lrxp.init();

    try {
      lrxp.process(input, output);
    } catch (EOFException e) {
    }

  }

  void process(Reader input, Appendable output) throws IOException {
    /*
     * ^liten<adj><posi><mf><sg><ind>/small<adj><sint><posi><mf><sg><ind>/little<adj><sint><posi><mf><sg><ind>$
     * ^liten<adj><posi><mf><sg><ind>/little<adj><sint><posi><mf><sg><ind>$
     */
    ArrayList<State> alive_states = new ArrayList<State>();
    alive_states.add(initial_state.copy());


    while (true) {
      skipUntil(input, output, '^');
      // We are at the start of a LU. Read it fully
      String lu = readFullBlock(input, '$');
      String[] luelems = lu.split("/");

      // We've finished reading a lexical form
      LOG(output);
      LOG("Here is the LU: " + Arrays.toString(luelems));

      String sl = luelems[0];

      for (int i = 0; i < sl.length(); i++) {
        char ch = sl.charAt(i);
        int val;
        if (ch == '<') {
          int n = sl.indexOf('>', i + 1);
          //LOG("sl "+sl);
          String tag = sl.substring(i, n + 1);
          val = alphabet.cast(tag);
          i = n;
          //LOG("step "+tag + " "+val);
        } else {
          val = ch;
          //LOG("step "+ch + " "+val);
        }

        ArrayList<State> new_state = new ArrayList<State>();

        for (State s : alive_states) {
          if (val < 0) {
            s.step(val, ANY_TAG);
          } else {
            s.step(val, ANY_CHAR);
          }
          if (s.size() > 0) {
            new_state.add(s);
          }
          if (s.isFinal(anfinals)) {
            StringBuilder sb = new StringBuilder();
            s.filterFinalsLRX(sb, alphabet, escaped_chars);
            LOG("filterfinals = " + sb);
          }
        }
        LOG("new_state " + new_state.size());

        alive_states = new_state;
        if (alive_states.size() == 1) {
          State s = alive_states.get(0);
        }
        alive_states.add(initial_state.copy());


        LOG("alive_states " + alive_states.size());
      }

    }
  }
}
