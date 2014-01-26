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
package org.apertium.pretransfer;

import static org.apertium.utils.IOUtils.getStdinReader;
import static org.apertium.utils.IOUtils.getStdoutWriter;
import static org.apertium.utils.IOUtils.openInFileReader;
import static org.apertium.utils.IOUtils.openOutFileWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.apertium.lttoolbox.Getopt;
import org.apertium.utils.IOUtils;

/**
 * @author Stephen Tigner
 *
 */
public class PreTransfer {
  /* The C++ code for pre-transfer is highly procedural, with no new classes, and thus
   * no member variables, just a bunch of functions called by each other and main().
   * As such, originally all functions were declared as static, but private so that the
   * only entry point is main().
   * When refactoring to allow for the internal pipeline, this had to change.
   * main() was split into main() and parseArgs(), the latter of which is also public.
   * The method processStream() also had to be made public.
   */
  public static class CommandLineParams {
    public boolean nullFlush;
    public Reader input;
    public Appendable output;
  }

  /**
   * Reads characters from an input stream and writes them to an output stream
   * until the specified character has been encountered. If it hits EOF before
   * the specified character is encountered, it prints an error message and exits.
   *
   * @param input
   * @param output
   * @param charCode -- The integer character code of the character to stop reading
   * and writing when found. This character is read from the input stream, but not
   * written to the output stream.
   * @throws IOException
   */
  private static void readAndWriteUntil(Reader input, Appendable output,
      final int charCode) throws IOException {
    int myChar;

    while ((myChar = input.read()) != charCode) {
      if (myChar == -1) {
        throw new IOException("pretransfer -- ERROR: unexpected EOF");
        // exit() is not an option as we are a library
        //System.exit(1); //EXIT_FAILURE macro constant in the C++ code = 1
      }
      output.append((char) myChar);
      /* The C++ code has an additional condition checking for a backslash
       * character ('\\'), then reading and writing another character.
       * This additional read and write is different, however, because
       * the normal read and write use "unlocked" variants that don't
       * "implicitly lock the stream" according to the documentation.
       * Since there is no equivalent to those functions in the Java
       * implementation, that section of the code has been left out
       * of the Java version.
       */
    }
  }

  private static void procWord(Reader input, Appendable output)
      throws IOException {
    int myChar;
    /* Using a StringBuilder instead of just a string for performance reasons,
     * because Strings are immutable objects in Java, and we're going to be
     * changing this one a lot. StringBuilders are for when you want mutable Strings.
     * StringBuilder is not synchronized, but this is single-threaded code, anyway.
     * If we need synchronization, then we'd want to use a StringBuffer instead.
     */
    StringBuilder buffer = new StringBuilder();

    boolean buffer_mode = false;
    boolean in_tag = false;
    boolean queuing = false;

    while ((myChar = input.read()) != '$') {
      if (myChar == -1) {
        throw new IOException("pretransfer -- ERROR: Unexpected EOF");
        // exit() is not an option as we are a library
        //System.exit(1); //EXIT_FAILURE = 1
      }

      switch (myChar) {
        case '<':
          in_tag = true;
          if (!buffer_mode) {
            buffer_mode = true;
          }
          break;

        case '>':
          in_tag = false;
          break;

        case '#':
          if (buffer_mode) {
            buffer_mode = false;
            queuing = true;
          }
          break;
      }

      if (buffer_mode) {
        if (myChar != '+' || (myChar == '+' && in_tag)) {
          /* C++ code has 'in_tag == true', which is unnecessary
           * because you can just test boolean values directly.
           */
          buffer.append(Character.toChars(myChar));
        } else if (!in_tag) { //Same here, no need for 'in_tag == false'
          buffer.append("$ ^");
        }
      } else {
        if (myChar == '+' && queuing) { //Ditto for queuing
          buffer.append("$ ^");
        } else {
          output.append((char) myChar);
        }
      }
    }
    output.append(buffer);
  }

  public static void processStream(Reader input, Appendable output,
      boolean null_flush) throws IOException {
    if (IOUtils.timing != null)
      IOUtils.timing.log("");

    int myChar;
    while ((myChar = input.read()) != -1) {
      /* The above while statement is equivalent to the C++ code:
       *
       * while(true)
       * {
       * int mychar = fgetwc_unlocked(input);
       * if(feof(input))
       * {
       * break;
       * }
       */

      switch (myChar) {
        case '[':
          output.append('[');
          readAndWriteUntil(input, output, ']');
          output.append(']');
          break;

        case '\\':
          output.append((char) myChar);
          int tempChar = input.read();
          /* C++ code doesn't seem to handle a backslash at the end of the file
           * with nothing after it. That's what this code is supposed to handle.
           * Only write out the char after the backslash if there's actually a
           * char to output.
           */
          if (tempChar != -1) {
            output.append((char) tempChar);
          }
          break;

        case '^':
          output.append((char) myChar);
          procWord(input, output);
          output.append('$');
          break;

        case '\0':
          output.append((char) myChar);
          break;

        default:
          output.append((char) myChar);
          break;
      }
    }
    if (IOUtils.timing != null)
      IOUtils.timing.log("Process pretransfer");
  }

  private static void showHelp() {
    System.err.println("USAGE: PreTransfer [input_file [output_file]]");
  }

  /**
   * @param args
   */
  public static void parseArgs(String[] args, CommandLineParams params,
      boolean pipelineMode) throws UnsupportedEncodingException {

    params.nullFlush = false;

    /* Only support short options, long opts are not currently supported.
     */
    Getopt getopt = new Getopt("PreTransfer", args, "zh");
    int c;
    while ((c = getopt.getopt()) != -1) {
      switch (c) {
        case 'z':
          params.nullFlush = true;
          break;
        case 'h':
        default:
          showHelp();
          return;
      }
    }

    /* getOptind() returns the index of the first non-option argument
     * encountered (since we iterated through the options until we got back
     * -1).
     *
     * In the C++ version, argv[0] is the command used to launch the program.
     * And since it's a zero-based array, adding 1 to it gives you the number
     * of options in argv. The expected arguments in argv (minus options) are
     * the program name, an input file, and an output file (3). If there are more
     * arguments supplied than that, then that is set of invalid arguments.
     *
     * In the Java version, however, args does not have the command used to
     * launch the program. So when subtracting options from the args, there
     * should be at most 2 options. And that's why the difference between
     * the C++ and Java versions in the following if statements.
     */

    // No need to run this same calculation over and over again.
    /**
     * Number of non-option arguments on the command-line.
     * Reminder: Does not include the executable's name, like in C++.
     */
    int numberOfArgs = args.length - getopt.getOptind();

    if (numberOfArgs > 2) {
      showHelp();
      return;
    }

    /* This really probably should be a switch statement.
     * Kept it as a sequence of if/else statements for ease of understanding
     * and code checking when comparing it with the C++ version.
     */
    if (numberOfArgs == 0 || pipelineMode) { //C++ version numberOfArgs == 1
            /* If we are in pipeline mode, we want to ignore any input/output
       * files specified on the command line, as we are using only internal
       * string readers and writers.
       */
      params.input = getStdinReader();
      params.output = getStdoutWriter();
    } else if (numberOfArgs == 1) { //C++ version numberOfArgs == 2
      try {
        /* Attempt to open a file for input, using the last argument on the
         * command line as the filename.
         */
        params.input = openInFileReader(args[args.length - 1]);
      } catch (FileNotFoundException e) {
        /* This exception is thrown if the file cannot be found, or
         * otherwise cannot be opened for reading.
         */
        showHelp();
        return;
      }
      params.output = getStdoutWriter();
    } else {
      try {
        /* Attempt to open a file for input, using the next-to-last argument
         * on the command line as the filename.
         */
        params.input = openInFileReader(args[args.length - 2]);
        /* Attempt to open a file for output, using the last argument on the
         * command line as the filename.
         */
        params.output = openOutFileWriter(args[args.length - 1]);
      } catch (FileNotFoundException e) {
        /* Either the input or the output file could not be found or otherwise
         * could not be opened for reading/writing.
         */
        showHelp();
        return;
      }
    }
  }

  public static void main(String[] args) throws IOException {
    System.setProperty("file.encoding", "UTF-8");

    CommandLineParams params = new CommandLineParams();
    parseArgs(args, params, false);

    /* The C++ version checks for EOF at this point, and dies if it finds it.
     * However we can't check for EOF in the Java version w/o reading the file
     * and advancing the pointer, so don't bother trying to check for EOF at
     * this point.
     */

    /* The ported functions have been written in the Java version to accept
     * InputStreamReader and OutputStreamWriter objects. The reason for this is to
     * avoid mojibake, which is a loanword from Japanese that refers to the garbled
     * character garbage you get when your character encoding is messed up.
     * When working on the port of the Tagger, I kept encountering mojibake until I
     * switched from using just straight InputStream and OutputStream objects to
     * InputStreamReader and OutputStreamWriter objects.
     */

    processStream(params.input, params.output, params.nullFlush);
    //Have to flush or won't get any output.
    IOUtils.flush(params.output);
  }
}
