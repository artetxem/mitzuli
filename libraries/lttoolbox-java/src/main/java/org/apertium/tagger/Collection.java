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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apertium.lttoolbox.Compression;

/**
 *
 * @author jimregan
 */
/**
 * Collection
 * Is an indexed set.
 */
public class Collection {
  Map<Set<Integer>, Integer> index;
  ArrayList<Set<Integer>> element;

  Collection() {
    index = new LinkedHashMap<Set<Integer>, Integer>();
    element = new ArrayList<Set<Integer>>();
  }

  int size() {
    if (element != null)
      return element.size();
    else
      return 0;
  }

  /**
   * Checks whether or not the collection has the element received as
   * a parameter.
   *
   * @param t element
   * @return true if t is not in the collection
   */
  boolean has_not(Set<Integer> t) {
    return !index.containsKey(t);
  }

  /**
   * @param n position in the collection
   * @return the element at the n-th position
   */
  Set<Integer> get(int n) {
    return element.get(n);
  }

  /**
   * @param t the element to find in the collection
   * @return position in the collection
   */
  int get(Set<Integer> t) {
    if (has_not(t)) {
      index.put(t, index.size() - 1);
      element.add(t);
    }
    return index.get(t);
  }

  /**
   * Adds an element to the collection
   *
   * @param t the element to be added
   */
  int add(Set<Integer> t) {
    try {
      if (index == null) {
        index = new LinkedHashMap<Set<Integer>, Integer>();
      }
      if (element == null) {
        element = new ArrayList<Set<Integer>>();
      }

      /* So here's the original line:
       * index.put(t, Integer.valueOf((index.size() > 0) ? index.size()-1 : 0));
       * Even though the logic in the C++ code also said index.size() - 1,
       * this caused the value stored in the map to be 1 less than it should be.
       * Remove the -1, and it works properly now.
       */
      index.put(t, Integer.valueOf(index.size()));
      element.add(t);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return index.get(t);
  }

  /**
   * Reads the collection contents from an input stream
   *
   * @param input the input stream
   */
  void read(InputStream input) throws IOException {
    try {
      int size = Compression.multibyte_read(input);

      for (; size != 0; size--) {
        Set<Integer> myset = new LinkedHashSet<Integer>();
        int set_size = Compression.multibyte_read(input);
        for (; set_size != 0; set_size--) {
          myset.add(Compression.multibyte_read(input));
        }
        this.add(myset);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Write the collection contents to an output stream
   *
   * @param output the output stream
   */
  void write(OutputStream output) throws IOException {
    Compression.multibyte_write(element.size(), output);

    for (int i = 0; i != element.size(); i++) {
      Compression.multibyte_write(element.get(i).size(), output);
      for (Integer it : element.toArray(new Integer[element.size()])) {
        Compression.multibyte_write(it, output);
      }
    }
  }
}
