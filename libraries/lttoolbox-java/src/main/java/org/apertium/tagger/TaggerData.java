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
 * along with this program; if not, writeDouble to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
package org.apertium.tagger;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import org.apertium.lttoolbox.Compression;

/**
 *
 * @author jimregan
 */
public class TaggerData {
  // FIXME: IntSet?
  private Set<Integer> open_class;
  private List<TForbidRule> forbid_rules;
  Map<String, Integer> tag_index;
  private List<String> array_tags;
  private List<TEnforceAfterRule> enforce_rules;
  private List<String> prefer_rules;
  private List<String> discard;
  private ConstantManager constants;
  private PatternList plist;
  private int N;
  private int M;
  private double[][] a;
  private double[][] b;
  private Collection output;
  private double ZERO = 1e-10;
  private boolean DEBUG = false;

  public TaggerData() {
    a = null;
    b = null;
    N = 0;
    M = 0;
    // I'm just gonna go nuts with initialisations, k?
    discard = new ArrayList<String>();
    enforce_rules = new ArrayList<TEnforceAfterRule>();
    prefer_rules = new ArrayList<String>();
    array_tags = new ArrayList<String>();
    open_class = new LinkedHashSet<Integer>();
    forbid_rules = new ArrayList<TForbidRule>();
    tag_index = new LinkedHashMap<String, Integer>();
    constants = new ConstantManager();
    plist = new PatternList();
    output = new Collection();
  }

  public TaggerData(TaggerData o) {
    copy(o);
  }

  /**
   * Copies the passed-in TaggerData object to this one.
   *
   * @param o - The TaggerData object to copy.
   */
  private void copy(TaggerData o) {
    open_class = new LinkedHashSet<Integer>(o.open_class);
    forbid_rules = new ArrayList<TForbidRule>(o.forbid_rules);
    tag_index = new LinkedHashMap<String, Integer>(o.tag_index);
    array_tags = new ArrayList<String>(o.array_tags);
    enforce_rules = new ArrayList<TEnforceAfterRule>(o.enforce_rules);
    prefer_rules = new ArrayList<String>(o.prefer_rules);
    constants = new ConstantManager(o.constants);
    plist = new PatternList(o.plist);
  }

  Set<Integer> getOpenClass() {
    return open_class;
  }

  void setOpenClass(Set<Integer> oc) {
    open_class = oc;
  }

  List<TForbidRule> getForbidRules() {
    return forbid_rules;
  }

  void setForbidRules(ArrayList<TForbidRule> rules) {
    this.forbid_rules = rules;
  }

  Map<String, Integer> getTagIndex() {
    return tag_index;
  }

  void setTagIndex(Map<String, Integer> ti) {
    tag_index = ti;
  }

  List<String> getArrayTags() {
    return array_tags;
  }

  void setArrayTags(List<String> at) {
    this.array_tags = at;
  }

  List<TEnforceAfterRule> getEnforceRules() {
    return enforce_rules;
  }

  void setEnforceRules(List<TEnforceAfterRule> ea) {
    this.enforce_rules = ea;
  }

  Collection getOutput() {
    return output;
  }

  void setOutput(Collection c) {
    output = c;
  }

  List<String> getPreferRules() {
    return prefer_rules;
  }

  void setPreferRules(List<String> a) {
    prefer_rules = a;
  }

  public void read(InputStream in) throws IOException {
    // open class
    int val = 0;
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      val += Compression.multibyte_read(in);
      open_class.add(val);
    }

    //if (DEBUG) System.out.println("open_class = " + open_class);

    // forbid_rules
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      TForbidRule aux = new TForbidRule();
      aux.tagi = Compression.multibyte_read(in);
      aux.tagj = Compression.multibyte_read(in);
      forbid_rules.add(aux);
    }


    //if (DEBUG) System.out.println("forbid_rules = " + forbid_rules);

    // array_tags
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      array_tags.add(Compression.String_read(in));
    }


    //if (DEBUG) System.out.println("array_tags = " + array_tags);
    // tag_index
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      String tmp = Compression.String_read(in);
      tag_index.put(tmp, Compression.multibyte_read(in));
    }

    // enforce_rules
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      TEnforceAfterRule aux = new TEnforceAfterRule();
      aux.tagi = Compression.multibyte_read(in);
      for (int j = Compression.multibyte_read(in); j != 0; j--) {
        aux.tagsj.add(Compression.multibyte_read(in));
      }
      enforce_rules.add(aux);
    }

    // prefer_rules
    for (int i = Compression.multibyte_read(in); i != 0; i--) {
      prefer_rules.add(Compression.String_read(in));
    }

    // constants
    constants.read(in);

    // output
    output.read(in);

    // dimensions
    N = Compression.multibyte_read(in);
    M = Compression.multibyte_read(in);


    if (DEBUG)
      System.out.println("N = " + N);
    if (DEBUG)
      System.out.println("M = " + M);

    a = new double[N][N];
    b = new double[N][M];

    // readDouble a
    DataInputStream indata = new DataInputStream(in);
    for (int i = 0; i != N; i++) {
      for (int j = 0; j != N; j++) {
        //a[i][j] = Compression.readDouble(in);
        a[i][j] = indata.readDouble();
        /* if(DEBUG) {
         * System.out.println("a[" + i + "][" + j +"] = " + a[i][j]);
         * System.out.flush();
         * } */
      }
    }

    // initializing b matrix
    for (int i = 0; i != N; i++) {
      for (int j = 0; j != M; j++) {
        b[i][j] = ZERO;
      }
    }

    // readDouble nonZERO values of b
    int nval = Compression.multibyte_read(in);

    for (; nval != 0; nval--) {
      int i = Compression.multibyte_read(in);
      int j = Compression.multibyte_read(in);
      b[i][j] = indata.readDouble(); // Compression.readDouble(in);
          /* if(DEBUG) {
       * System.out.println("b[" + i + "][" + j +"] = " + b[i][j]);
       * System.out.flush();
       * } */
    }

    // readDouble pattern list
    plist.read(in);

    // readDouble discards on ambiguity
    discard.clear();

    int limit = Compression.multibyte_read(in);

    if (limit < 0) { // EOF ?
      return;
    }

    for (int i = 0; i < limit; i++) {
      discard.add(Compression.String_read(in));
    }
  }

  void write(OutputStream out) throws IOException {
    Compression.multibyte_write(open_class.size(), out);
    int val = 0;
    for (int i : open_class) {
      Compression.multibyte_write(i - val, out);
      val = i;
    }

    Compression.multibyte_write(forbid_rules.size(), out);
    for (int i = 0; i != forbid_rules.size(); i++) {
      Compression.multibyte_write(forbid_rules.get(i).tagi, out);
      Compression.multibyte_write(forbid_rules.get(i).tagj, out);
    }

    Compression.multibyte_write(array_tags.size(), out);
    for (int i = 0; i != array_tags.size(); i++) {
      Compression.String_write(array_tags.get(i), out);
    }

    Compression.multibyte_write(tag_index.size(), out);
    for (Map.Entry<String, Integer> entry : tag_index.entrySet()) {
      Compression.String_write(entry.getKey(), out);
      Compression.multibyte_write(entry.getValue(), out);
    }

    Compression.multibyte_write(enforce_rules.size(), out);
    for (int i = 0; i != enforce_rules.size(); i++) {
      Compression.multibyte_write(enforce_rules.get(i).tagi, out);
      Compression.multibyte_write(enforce_rules.get(i).tagsj.size(), out);
      for (int j = 0; j != enforce_rules.get(i).tagsj.size(); j++) {
        Compression.multibyte_write(enforce_rules.get(i).tagsj.get(j), out);
      }
    }

    Compression.multibyte_write(prefer_rules.size(), out);
    for (int i = 0; i != prefer_rules.size(); i++) {
      Compression.String_write(prefer_rules.get(i), out);
    }

    constants.write(out);

    output.write(out);

    Compression.multibyte_write(N, out);
    Compression.multibyte_write(M, out);
    for (int i = 0; i != N; i++) {
      for (int j = 0; j != N; j++) {
        Compression.writeDouble(out, a[i][j]);
      }
    }

    int nval = 0;
    for (int i = 0; i != N; i++) {
      for (int j = 0; j != M; j++) {
        if (!output.get(j).contains(i)) {
          nval++;
        }
      }
    }

    Compression.multibyte_write(nval, out);
    for (int i = 0; i != N; i++) {
      for (int j = 0; j != N; j++) {
        Compression.multibyte_write(i, out);
        Compression.multibyte_write(j, out);
        Compression.writeDouble(out, b[i][j]);
      }
    }

    plist.write(out);

    if (discard.size() != 0) {
      Compression.multibyte_write(discard.size(), out);
      for (int i = 0; i != discard.size(); i++) {
        Compression.String_write(discard.get(i), out);
      }
    }
  }

  void addDiscard(String tags) {
    discard.add(tags);
  }

  PatternList getPatternList() {
    return plist;
  }

  void setPatternList(PatternList pl) {
    plist = pl;
  }

  ConstantManager getConstants() {
    return constants;
  }

  void setConstants(ConstantManager c) {
    constants = c;
  }

  List<String> getDiscardRules() {
    return discard;
  }

  void setDiscardRules(ArrayList<String> v) {
    discard = v;
  }

  void setProbabilities(int myN, int myM, double[][] myA, double[][] myB) {
    N = myN;
    M = myM;

    if (N != 0 && M != 0) {
      //NxN Matrix
      a = new double[N][];
      for (int i = 0; i != N; i++) {
        a[i] = new double[N];
        if (myA != null) {
          for (int j = 0; j != N; j++) {
            a[i][j] = myA[i][j];
          }
        }
      }

      //NxM matrix
      b = new double[N][];
      for (int i = 0; i != N; i++) {
        b[i] = new double[M];
        if (myB != null) {
          for (int j = 0; j != M; j++) {
            b[i][j] = myB[i][j];
          }
        }
      }

    } else {
      a = null;
      b = null;
    }
  }

  void setProbabilities(int myN, int myM) {
    this.setProbabilities(myN, myM, null, null);
  }

  double[][] getA() {
    return a;
  }

  void setA(double[][] myA) {
    a = myA;
  }

  double[][] getB() {
    return b;
  }

  void setAElement(int i, int j, double d) {
    a[i][j] = d;
  }

  void setBElement(int i, int j, double d) {
    b[i][j] = d;
  }

  void setB(double[][] myB) {
    b = myB;
  }

  int getN() {
    return N;
  }

  int getM() {
    return M;
  }
}
