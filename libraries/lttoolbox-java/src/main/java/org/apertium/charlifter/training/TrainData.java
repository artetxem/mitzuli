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

import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import org.apertium.charlifter.Data;

/**
 *
 * @author jimregan
 */
public class TrainData extends Data {
  /**
   * keys are chars with diacritics, val is initially count, then turned
   * into a log prob
   */
  HashMap<Character, Integer> charcount;
  /**
   * keys are strings that look like "ár|+|bpost" or "ar|-|me" and values
   * are at first counts, then turned into a log prob - first example is
   * probability of seeing something which asciifies to "bpost" following
   * (+) the word "ár".
   */
  HashMap<String, Integer> contextcount;

  TrainData() {
    super();
    charcount = new HashMap<Character, Integer>();
    contextcount = new HashMap<String, Integer>();
  }
}
