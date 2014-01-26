/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.compile;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apertium.lttoolbox.Alphabet;
import org.apertium.lttoolbox.Compression;
import org.apertium.lttoolbox.collections.Transducer;

/**
 *
 * @author j
 */
public class TransducerCompTest {

  public static void main(String[] args) throws IOException {


    Compile c = new Compile();
    c.parse("testdata/apertium-fr-es.fr.dix", Compile.COMPILER_RESTRICTION_LR_VAL);


    for (String s : c.sections.keySet()) {
      System.out.println("considering transducer of section " + s);
      System.out.println("number of states : " + c.sections.get(s).transitions.size());
      int temp = 0;
      int max = 0;
      float average = 0;
      for (int i = 0; i < c.sections.get(s).transitions.size(); i++) {
        temp += c.sections.get(s).transitions.get(i).size();
        average += temp;
        max = (temp > max) ? temp : max;
        temp = 0;
      }
      System.out.println("maximal number of transitions leaving a state " + max);
      System.out.println("average number of transitions leaving a state " + average / ((float) c.sections.get(s).transitions.size()));
    }

    //System.exit(-1);
    c.write("testTransducer2.bin");
    InputStream input = new BufferedInputStream (new FileInputStream("testTransducer2.bin"));
    //InputStream input = new BufferedInputStream(new FileInputStream("outc"));
    //c2 = c.DEBUG_read(input);

    //FSTProcessor fstp = new FSTProcessor();
    //fstp.load(input);
    String letters = Compression.String_read(input);
    Alphabet alphabet = Alphabet.read(input);

    Map<String, TransducerComp> sections = new HashMap<String, TransducerComp>();

    int len = Compression.multibyte_read(input);

    while (len > 0) {
      String name = Compression.String_read(input);

      System.out.println("reading : " + name);
      //if (len ==2) {System.exit(-1);}
      TransducerComp t = new TransducerComp();
      Transducer.read(t, input, 0);
      sections.put(name, t);

      len--;
      if (c.sections.get(name) != null && sections.get(name) != null) {
        boolean same = c.sections.get(name).DEBUG_compare(sections.get(name));
        if (!same) throw new RuntimeException(name+" didnt compare");
        System.out.println(name + " passed comparison");
      }
      //System.exit(-1);
      //throw new RuntimeException("section "+name+" was totaly DEBUG_read");
    }
    input.close();

    for (String s : c.sections.keySet()) {
      int count1 = 0;
      int max1 = 0;
      int count2 = 0;
      int max2 = 0;
      for (int i = 0; i < c.sections.get(s).transitions.size(); i++) {
        if (i > max1) {
          max1 = i;
        }
        for (Integer j : c.sections.get(s).transitions.get(i).keySet()) {

          count1 += c.sections.get(s).transitions.get(i).get(j).size();
        }
      }
      for (int i = 0; i < sections.get(s).transitions.size(); i++) {
        if (i > max2) {
          max2 = i;
        }
        for (Integer j : sections.get(s).transitions.get(i).keySet()) {
          count2 += sections.get(s).transitions.get(i).get(j).size();
        }
      }

      System.out.println("comparing transducers of section " + s);
      System.out.println("original transducer : "+c.sections.get(s));
      System.out.println("original transducer has " + count1 + " transitions");
      System.out.println("original transducer higher state is " + max1);
      System.out.println("DEBUG_read transducer : "+sections.get(s));
      System.out.println("read transducer has " + count2 + " transitions");
      System.out.println("read transducer higher state is " + max2);
      //System.out.println(c.sections.get(s).DEBUG_compare(sections.get(s)));
      boolean same = c.sections.get(s).DEBUG_compare(sections.get(s));
      if (!same) throw new RuntimeException(s+" didnt compare");
      System.out.println(s + " passed comparison");
    }
  }
}
