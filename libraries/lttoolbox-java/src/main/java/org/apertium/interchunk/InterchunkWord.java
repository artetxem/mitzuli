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
package org.apertium.interchunk;

import org.apertium.transfer.ApertiumRE;

/**
 * @author Stephen Tigner
 *
 */
public class InterchunkWord {

  /* Private members and methods
   * Added a _ (underscore) before the private members as a visual hint that
   * they are private, and to avoid getting them confused with method parameters
   * that, in some cases, are named the same (just w/o the underscore).
   */
  /**
   * Target language chunk name and tags.
   */
  private String _chunk;
  /**
   * Target language chunk content.
   */
  private String _queue;

  //Public members and methods
  /**
   * Copies the the supplied InterchunkWord to this one.
   *
   * @param o - The other InterchunkWord object to copy from.
   */
  public void copy(final InterchunkWord o) {
    /* Yes, we're only copying chunk, not queue as well.
     * This is what the C++ code does as well.
     * However, the C++ code makes this private, as it overloads the '=' operator,
     * then calls this method. Java doesn't have operator overloading, so making
     * this method public instead.
     */
    _chunk = o._chunk;
  }

  /* The destroy() method in the C++ code does nothing, so not bothering with it.
   */
  public InterchunkWord() {
    //do nothing
  }

  public InterchunkWord(final String chunk) {
    init(chunk);
  }

  public InterchunkWord(final InterchunkWord o) {
    copy(o);
  }

  public void init(final String chunk) {
    for (int i = 0; i < chunk.length(); i++) {
      if (chunk.charAt(i) == '\\') {
        i++;
      } else if (chunk.charAt(i) == '{') {
        _chunk = chunk.substring(0, i); //Grab everything up to the '{'
        _queue = chunk.substring(i); //Grab from the '{' onward.
        return;
      }
    }
    _chunk = chunk;
    _queue = "";
  }

  public String chunkPart(final ApertiumRE part) {
    String result = part.match(_chunk);
    if (result.length() == 0) {
      result = part.match(_queue);
      if (result.length() != _queue.length()) {
        return "";
      } else {
        return result;
      }
    } else if (result.length() == _chunk.length()) {
      return part.match(_chunk + _queue);
    } else {
      return result;
    }
  }

  public void chunkPartSet(final ApertiumRE part, final String value) {
    _chunk = part.replace(_chunk, value);
  }

  public void tlSet(final ApertiumRE part, final String value) {
    chunkPartSet(part, value);
  }

  public void slSet(final ApertiumRE part, final String value) {
    chunkPartSet(part, value);
  }

  public String tl(final ApertiumRE part) {
    return chunkPart(part);
  }

  public String sl(final ApertiumRE part) {
    return chunkPart(part);
  }
}
