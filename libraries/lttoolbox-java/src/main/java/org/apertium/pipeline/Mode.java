/*
 * Copyright (C) 2010 Stephen Tigner
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
package org.apertium.pipeline;

import static org.apertium.utils.IOUtils.readFile;

import java.io.IOException;
import java.util.ArrayList;
import org.apertium.Translator;

/**
 * @author Stephen Tigner
 *
 */
public class Mode {
  //Each mode has a pipeline, which is a list of programs to run.
  ArrayList<Program> _pipeline = new ArrayList<Program>();
  //Mode name
  String _filename;

  public Mode(String filename) throws IOException {
    _filename = filename;
    String[] progList = readFile(filename).split("\\|");
    for (String prog : progList) {
      _pipeline.add(new Program(prog.trim()));
    }
  }

  public Program getProgramByIndex(int index) {
    return _pipeline.get(index);
  }

  public int getPipelineLength() {
    return _pipeline.size();
  }

  public String getFilename() {
    return _filename;
  }

  @Override
  public String toString() {
    return Translator.getTitle(_filename.substring(_filename.lastIndexOf('/') + 1, _filename.endsWith(".mode") ? _filename.length() - 5 : _filename.length()));
  }
}
