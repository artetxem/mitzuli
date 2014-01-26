/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.process;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.Compression;
import org.apertium.utils.IOUtils;

/**
 *
 * @author j
 */
public class BasicFSTProcessor {
  public static boolean DEBUG = false;
  /**
   * Alphabet
   */
  public Alphabet alphabet = new Alphabet();
  /**
   * Set of characters being considered alphabetics in wound-example.dix file this corresponds to
   * <alphabet>ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz</alphabet>
   */
  protected SetOfCharacters alphabetic_chars;
  /**
   * Initial state of every token
   */
  public State initial_state = new State();
  /**
   * Transducers in FSTP
   */
  protected Map<String, TransducerExe> transducers = new TreeMap<String, TransducerExe>();

  public BasicFSTProcessor() {
  }

  public void calc_initial_state() {
    Collection<TransducerExe> transducerc = transducers.values();
    /*
     * Node root = new Node();
     * root.initTransitions(transducerc.size());
     * for (TransducerExe transducer : transducerc) {
     * Node initialNodeForThisTransducer = transducer.getInitial();
     * root.addTransition(0, 0, initialNodeForThisTransducer);
     * }
     * initial_state.init(root);
     */
    initial_state.init(transducerc);
  }

  /**
   * Reads the binary representation of a .dix file - <b>keeping the whole file in memory at all times</b>.
   * <b>WARNING</b>This method should be avoided on Android and other memory constrained devices.
   *
   * @param input
   * @throws IOException
   */
  public void load(InputStream input) throws IOException {
    if (DEBUG) {
      System.err.println("FSTProcessor.load - SLOW VERSION");
    }
    ByteBuffer byteBuffer = IOUtils.inputStreamToByteBuffer(input);
    load(byteBuffer);
  }

  /**
   * Reads the binary representation of a .dix file
   *
   * @param filePath
   * @throws IOException
   */
  public void load(String filePath) throws IOException {
    if (DEBUG) {
      System.err.println("FSTProcessor memmap and load(" + filePath);
    }
    load(IOUtils.memmap(filePath), filePath);
  }

  /**
   * Reads the binary representation of a .dix file
   *
   * @param input
   * @throws IOException
   */
  public void load(ByteBuffer input) throws IOException {
    load(input, null);
  }

  /**
   * Reads the binary representation of a .dix file
   *
   * @param input
   * @param cachedIndexes path to a unique directory where cached transducer indexes are
   */
  public void load(ByteBuffer input, String filename) throws IOException {
    // the following examples and numbers corresponds to testdata/wound-example.dix
    // read letters (<alphabet>ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz</alphabet>)
    int len = Compression.multibyte_read(input); // numer of letters (52)
    alphabetic_chars = new SetOfCharacters();
    while (len > 0) {
      char c = (char) Compression.multibyte_read(input);
      alphabetic_chars.add(c);
      len--;
    }
    // symbols
    alphabet = Alphabet.read(input);
    if (DEBUG) {
      System.err.println("FSTProcessor load(" + input + " " + filename);
      System.err.println("alphabet = " + alphabet.toString().replace(',', '\n'));
    }
    //loading the sections transducers
    len = Compression.multibyte_read(input); // xx  (2 for eo-en.dix)
    while (len > 0) {
      String name = Compression.String_read(input); // "main@standard", "propraj_nomoj@standard"
      TransducerExe tx = transducers.get(name);
      if (tx == null) {
        tx = new TransducerExe();
        transducers.put(name, tx);
      } else {
        System.err.println(this.getClass() + ".load() Why has transducer already name " + name);
      }
      File cacheFile = null;
      //System.out.println("reading : "+name);
      if (IOUtils.cacheDir != null && filename != null) {
        // Try to load make cached a memmapped transducer cache file
        String fileid = new File(filename).getAbsolutePath().replace(File.separatorChar, '_').replace('.', '_');
        cacheFile = new File(IOUtils.cacheDir, fileid + "@" + input.position());
        //System.out.println("cachedFile = " + cacheFile);
      }
      tx.read(input, alphabet, cacheFile);
      len--;
      //System.out.println(len);
    }
    //if (DEBUG)  System.err.println("  transducers = " + transducers.toString());
  }

  public boolean valid() {
    if (initial_state.isFinal()) {
      System.err.println("Error: Invalid dictionary (hint: the left side of an entry is empty)");
      return false;
    } else {
      State s = initial_state.copy();
      s.step(' ');
      if (s.size() != 0) {
        System.err.println("Error: Invalid dictionary (hint: entry beginning with whitespace)");
        return false;
      }
    }
    return true;
  }

}
