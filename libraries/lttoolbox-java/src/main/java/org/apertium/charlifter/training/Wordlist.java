/*
 * Copyright 2010 Jimmy O'Regan
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
package org.apertium.charlifter.training;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;

/**
 *
 * @author jimregan
 */
public class Wordlist {
  static String[] read(String filename) throws IOException {
    ArrayList<String> a = new ArrayList<String>();

    BufferedReader br = null;

    try {
      FileInputStream fstream = new FileInputStream(filename);
      DataInputStream in = new DataInputStream(fstream);
      br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      String strLine = "";

      while ((strLine = br.readLine()) != null) {
        if (strLine.equals("")) {
          continue;
        }
        a.add(strLine);
        strLine = "";
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    } finally {
      if (br != null)
        br.close();
    }

    return a.toArray(new String[a.size()]);
  }
}
