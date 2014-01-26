/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.compile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.Compression;
import org.apertium.lttoolbox.collections.Transducer;

/**
 *
 * @author j
 */
public class TransducerCollection {
  public String letters;
  public Alphabet alphabet;
  public Map<String, TransducerComp> sections;

  public void read(String file) throws IOException {
    InputStream input = new BufferedInputStream (new FileInputStream(file));
    letters = Compression.String_read(input);
    alphabet = Alphabet.read(input);
    sections = new LinkedHashMap<String, TransducerComp>();

    int len = Compression.multibyte_read(input);
    while (len > 0) {
      String name = Compression.String_read(input);
      //System.err.println("read "+name+ "  @" + file);

      TransducerComp t = new TransducerComp();
      Transducer.read(t, input, 0);
      sections.put(name, t);
      len--;
    }
    input.close();
  }

  public void write(String file) throws IOException {
    OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
    // letters
    Compression.String_write(letters, output);

    // symbols
    alphabet.write(output);

    // transducers
    Compression.multibyte_write(sections.size(), output);

    for (String first : sections.keySet()) {
      final TransducerComp second = sections.get(first);
      System.out.println(first + " " + second.size() + " " + second.numberOfTransitions());
      Compression.String_write(first, output);
      second.write(output, 0);
    }
    output.close();
  }

}
