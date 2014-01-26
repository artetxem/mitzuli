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

import static org.apertium.utils.IOUtils.getStdinReader;
import static org.apertium.utils.IOUtils.getStdoutWriter;
import static org.apertium.utils.IOUtils.openInFileReader;
import static org.apertium.utils.IOUtils.openOutFileWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apertium.lttoolbox.Getopt;

/**
 * @author Stephen Tigner
 * Generic formatter, base class for other formatters.
 */
public abstract class GenericFormatter {
  /**
   * Private enumeration for formatters.
   * There's three values. DEFORMAT, REFORMAT, and NOMODE.
   * These represent the modes for the formatter.
   * DEFORMAT means the deformat method will be invoked to escape
   * text, insert superblanks, etc.
   * REFORMAT means the reformat method will be invoked to de-escape
   * text, remove superblanks, etc. (Basically undo all the changes the
   * deformatter made in the source text.)
   * NOMODE means that a mode wasn't properly supplied or there was an
   * issue parsing the command-line, such as missing or extra options.
   * (For example, not having either -d or -r, or having both -d and -r.)
   *
   * @author Stephen Tigner
   *
   */
  protected enum FormatterMode {
    DEFORMAT, REFORMAT, NOMODE
  }

  /**
   * Input filename, if not defined on the command line, stdin will be used.
   */
  protected String _inputFile = null;
  /**
   * Output filename, if not defined on the command line, stdout will be used.
   */
  protected String _outputFile = null;
  /**
   * The command-line label used for this formatter. It is used in help() and
   * error messages.
   */
  protected String _commandLabel = null;
  /**
   * This flag determines if the output precisely mimics the output of the C++ version,
   * for compatibility, or if it is a bit smarter in its output.
   * An example would be the propensity of the C++ code to output extra periods at the end
   * of lines with just whitespace after them, even if there's already a period there.
   * Another would be the C++ failing to escape some Apertium stream characters, even
   * though it really should.
   */
  protected boolean _cppCompat = true;
  //Low-level dev debugging
  protected boolean DEBUG = false;

  /**
   * Gets the mode (either deformat or reformat) selected on the command line.
   * Also parses the input and output file parameters.
   * If the command-line is invalid (missing or extraneous command-line options)
   * then help text is printed out, and FormatterMode.NOMODE is returned.
   *
   * @param argv
   * @param commandLabel
   * @return A FormatterMode object representing the mode selected, or null if
   * there was no mode selected or there otherwise was a bad command line.
   */
  protected FormatterMode getModeAndFiles(String[] argv, String commandLabel) {
    /* The format for the string passed in as the third argument to the
     * GetOpt constructor is a list of command-line options.
     * The ones with colons after them have required arguments,
     * and if these arguments are missing, the parsing will fail
     * and return '?' instead of the option when calling the
     * getopt() method. This will cause the switch to fall through
     * to the default case and display the help(), and return FormatterMode.NOMODE.
     */
    Getopt getOpt = new Getopt(commandLabel, argv, "drci:o:");
    FormatterMode mode = FormatterMode.NOMODE;

    int opt = getOpt.getopt();
    boolean helpNeeded = false;

    do {
      switch (opt) {
        case 'd':
          if (mode == FormatterMode.NOMODE) {
            mode = FormatterMode.DEFORMAT;
          } else {
            helpNeeded = true;
          }
          break;
        case 'r':
          if (mode == FormatterMode.NOMODE) {
            mode = FormatterMode.REFORMAT;
          } else {
            helpNeeded = true;
          }
          break;
        case 'c':
          _cppCompat = false;
          break;
        case 'i':
          _inputFile = getOpt.getOptarg();
          break;
        case 'o':
          _outputFile = getOpt.getOptarg();
          break;
        default:
          helpNeeded = true;
      }
    } while ((opt = getOpt.getopt()) != -1);
    if (mode == FormatterMode.NOMODE) {
      helpNeeded = true;
    }
    if (helpNeeded) {
      help(commandLabel);
      //Reset mode to NOMODE since we have an invalid command line
      mode = FormatterMode.NOMODE;
    }
    return mode;
  }

  protected void help(String commandLabel) {
    System.out.println(commandLabel + ": deformatter and reformatter");
    System.out.println("USAGE: " + commandLabel + " -d [-c] [-i INPUT_FILE] [-o OUTPUT_FILE]");
    System.out.println("       " + commandLabel + " -r [-c] [-i INPUT_FILE] [-o OUTPUT_FILE]");
    System.out.println("OPTIONS:");
    System.out.println(" -d: Deformat, escape special characters and whitespace.");
    System.out.println(" -r: Reformat, un-escape special characters and whitespace.");
    System.out.println(" -c: Disable C++ compatibility mode, which emulates C++ output, including some sub-optimal behavior");
    System.out.println(" -i: Input file, uses the file INPUT_FILE as input.");
    System.out.println(" -o: Output file, uses the file OUTPUT_FILE as output");
    System.out.println("If the input and output files are not specified, then "
        + "stdin and stdout are used, respectively.");
  }

  /**
   * De-formats the incoming text in a format-specific manner.
   * In other words, it converts the text into Apertium stream format by escaping
   * special characters, white space, and other data that should not be translated.
   * (Such as tags in the case of HTML.)
   *
   * @param in - A Reader to pull the text to deformat from.
   * @param out - A Appendable to output the deformatted text to.
   */
  protected abstract void deFormat(Reader in, Appendable out);

  /**
   * Re-formats the incoming text in a format-specific manner.
   * In other words, it converts the text from Apertium stream format by de-escaping
   * special characters, and removing the superblanks around whitespace and other data
   * that was not to be translated. (Such as tags in the case of HTML.)
   *
   * @param in - A Reader to pull the text to deformat from.
   * @param out - A Appendable to output the deformatted text to.
   */
  protected abstract void reFormat(Reader in, Appendable out);

  /**
   * Reads the command-line arguments, sets up the mode and input/output streams,
   * and calls the appropriate deFormat or reFormat function.
   *
   * @param args
   * @throws FileNotFoundException
   * @throws UnsupportedEncodingException
   */
  public void doMain(String[] args) throws IOException {
    doMain(args, null, null);
  }

  public void doMain(String[] args, Reader in, Appendable out) throws IOException {

    FormatterMode mode = getModeAndFiles(args, _commandLabel);

    if (DEBUG) {
      System.err.println("mode: " + mode);
    }

    if (in == null) {
      //If we are given an input stream, use it, and ignore the command-line
      if (_inputFile != null) {
        in = openInFileReader(_inputFile);
      } else {
        in = getStdinReader();
      }
    }

    if (out == null) {
      //If we are given an output stream, use it, and ignore the command-line
      if (_outputFile != null) {
        out = openOutFileWriter(_outputFile);
      } else {
        out = getStdoutWriter();
      }
    }

    switch (mode) {
      case DEFORMAT:
        deFormat(in, out);
        break;
      case REFORMAT:
        reFormat(in, out);
        break;
      case NOMODE:
        //do nothing, help text should have been printed out already.
        break;
      default:
        //We should never get here, if we do, something is broken.
        String errorString = _commandLabel + ": invalid mode";
        errorString += System.getProperty("line.separator") + "Cannot continue.";
        throw new IllegalArgumentException(errorString);
    }
  }

  public GenericFormatter(String commandLabel) {
    _commandLabel = commandLabel;
  }
}
