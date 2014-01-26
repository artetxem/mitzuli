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

/**
 * @author Stephen Tigner
 *
 */
public class Program {
  public enum ProgEnum {
    LT_PROC, TAGGER, PRETRANSFER, TRANSFER, INTERCHUNK, POSTCHUNK,
    TXT_DEFORMAT, TXT_REFORMAT, OMEGAT_DEFORMAT, OMEGAT_REFORMAT, UNKNOWN
  }

  //Each program has a "name" which is a command line.
  private String _commandName;
  private String _fullPath;
  private final ProgEnum _program;
  //Each program also has a list of files, which are used, in order.
  private String _parameters;

  public Program(String commandLine) {
    /* Splits on spaces, assumes path won't have internal spaces.
     * This only splits the command from the parameters. The parameters
     * are left as a single string.
     * This is to make it easier to run the command when the time comes.
     * If a specific command needs to have the parameters split up for some
     * reason, that can still be done later.
     */
    String[] paramList = commandLine.split(" ", 2);

    /* Split off the command name from the rest of the path, as the paths in
     * mode files are absolute unix paths and will fail in cygwin, as Java
     * doesn't run in the cygwin filesystem.
     * Running the executables w/o a path prefix will work in Windows with
     * cygwin, provided that the user has the cygwin bin dir in their path.
     */
    _fullPath = paramList[0].trim();
    String[] commandPathList = _fullPath.split("\\/");
    //Grab the last entry
    _commandName = commandPathList[commandPathList.length - 1];
    //Grab the 2nd (and last) entry -- if it exists
    if (paramList.length > 1) {
      _parameters = paramList[1];
    } else {
      _parameters = "";
    }

    if (_commandName.equals("lt-proc")) {
      _program = ProgEnum.LT_PROC;
    } else if (_commandName.matches("^apertium-tagger(-j)?$")) {
      _program = ProgEnum.TAGGER;
    } else if (_commandName.matches("^apertium-pretransfer(-j)?$")) {
      _program = ProgEnum.PRETRANSFER;
    } else if (_commandName.matches("^apertium-transfer(-j)?$")) {
      _program = ProgEnum.TRANSFER;
    } else if (_commandName.matches("^apertium-interchunk(-j)?$")) {
      _program = ProgEnum.INTERCHUNK;
    } else if (_commandName.matches("^apertium-postchunk(-j)?$")) {
      _program = ProgEnum.POSTCHUNK;
    } else if (_commandName.matches("^apertium-destxt(-j)?$")) {
      _program = ProgEnum.TXT_DEFORMAT;
    } else if (_commandName.matches("^apertium-retxt(-j)?$")) {
      _program = ProgEnum.TXT_REFORMAT;
    } else if (_commandName.matches("^apertium-desomegat(-j)?$")) {
      _program = ProgEnum.OMEGAT_DEFORMAT;
    } else if (_commandName.matches("^apertium-reomegat(-j)?$")) {
      _program = ProgEnum.OMEGAT_REFORMAT;
    } else {
      _program = ProgEnum.UNKNOWN;
    }
  }

  public String getCommandName() {
    return _commandName;
  }

  public String getFullPath() {
    return _fullPath;
  }

  public ProgEnum getProgram() {
    return _program;
  }

  /**
   * Allows for all the filename strings to be retrieved at once.
   *
   * @return A copy of the internal list of parameters.
   */
  public String getParameters() {
    return _parameters;
  }

  @Override
  public String toString() {
    /* StringBuilder tempString = new StringBuilder();
     * tempString.append("{Program -- " + _commandName + " (" +
     * _program.toString() + "): \n");
     * tempString.append("Parameters: " + _parameters + " }");
     * return tempString.toString(); */
    return _commandName + " " + _parameters;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 23 * hash + (this._program != null ? this._program.hashCode() : 0);
    hash = 23 * hash + (this._parameters != null ? this._parameters.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Program other = (Program) obj;
    if (this._program != other._program) {
      return false;
    }
    if ((this._parameters == null) ? (other._parameters != null) : !this._parameters.equals(other._parameters)) {
      return false;
    }
    return true;
  }
}
