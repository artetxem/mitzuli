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
package org.apertium.formatter;

import java.util.ArrayList;

/**
 * @author Stephen Tigner
 * When creating new formatters, please add them to this registry.
 * Also, don't forget to add them to {@link org.apertium.modes.Program}.
 */
public class FormatterRegistry {
  private static ArrayList<String> _registry = null;

  private static void initializeRegistry() {
    _registry = new ArrayList<String>();
    _registry.add("txt");
  }

  /**
   * Checks to see if a formatter exists for a certain type of file.
   * Formatters are typically named by the file extensions they work with.
   * For example, "txt" is for plain text files.
   *
   * @param formatterName -- The formatter to check for, typically named by
   * file extension.
   * @return <code>true</code> if registered, <code>false</code> if not
   */
  public static boolean isRegistered(String formatterName) {
    /* If indexOf returns something other than -1, then the supplied string
     * is in the registered list.
     */
    if (_registry == null) {
      initializeRegistry();
    }
    return (_registry.indexOf(formatterName) != -1);
  }
}
