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
import java.util.Map;
import org.apertium.lttoolbox.Compression;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 *
 * @author jimregan
 */
public class ConstantManager {
  private LinkedHashMap<String, Integer> constants;

  public ConstantManager() {
    this.constants = new LinkedHashMap<String, Integer>();
  }

  public ConstantManager(ConstantManager o) {
    copy(o);
  }

  /**
   * Copies the passed-in ConstantManager object to this one.
   *
   * @param o - The ConstantManager object to copy.
   */
  private void copy(ConstantManager o) {
    this.constants = new LinkedHashMap<String, Integer>(o.constants);
  }

  public void setConstant(String constant, int value) {
    try {
      if (constants == null) {
        constants = new LinkedHashMap<String, Integer>();
      }
      constants.put(constant, value);
    } catch (NullPointerException npe) {
      System.err.println("Null pointer: " + constant + " " + value);
      npe.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public int getConstant(String constant) {
    return constants.get(constant);
  }

  public void write(OutputStream output) throws IOException {
    Compression.multibyte_write(constants.size(), output);
    for (Map.Entry<String, Integer> e : constants.entrySet()) {
      Compression.String_write(e.getKey(), output);
      Compression.multibyte_write(e.getValue(), output);
    }
  }

  public void read(InputStream input) throws IOException {
    try {
      if (constants != null && constants.size() != 0) {
        constants.clear();
      }
      int size = Compression.multibyte_read(input);
      for (int i = 0; i != size; i++) {
        String str = Compression.String_read(input);
        int constant = Compression.multibyte_read(input);
        setConstant(str, constant);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
