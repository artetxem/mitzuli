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

import java.io.IOException;
import java.io.Reader;
import org.apertium.utils.IOUtils;

/**
 * @author Stephen Tigner
 * Class to de-format and re-format plain text.
 * (De)escapes Apertium stream format special characters and inserts/removes
 * superblanks.
 */
public class TextFormatter extends GenericFormatter {
  private boolean isApertiumSpecialCharacter(int charCode) {
    switch (charCode) {
      //Apertium stream format special characters
      case '^':
      case '$':
      case '/':
      case '<':
      case '>':
      case '{':
      case '}':
      case '\\':
      case '@':
      case '[':
      case ']':
        return true;
      case '*':
      case '#':
      case '+':
      case '~':
        /* If in C++ compatibility mode, return false, as these characters
         * aren't escaped in the C++ version, even though they probably
         * should be. If not in C++ compatibility mode, return true
         */
        return !_cppCompat;
      default:
        return false;
    }
  }

  @Override
  protected void deFormat(Reader inRead, Appendable outWrite) {
    try {
      int currentChar = inRead.read();
      /* Keep track of the previous char, intended for use if needing
       * to backtrack one character for various reasons.
       */
      int previousChar = -1;
      do {
        if (DEBUG) {
          System.err.println("currentChar: " + currentChar + ", char: '"
              + new String(Character.toChars(currentChar)) + "'");
        }
        if (isApertiumSpecialCharacter(currentChar)) {
          outWrite.append('\\');
          outWrite.append((char) currentChar);
          previousChar = currentChar;
        } else {
          if (Character.isWhitespace(currentChar)) {
            StringBuilder spaceWrite = new StringBuilder();
            boolean writePeriod = false;
            /* Whitespace is other than a single space.
             * If the whitespace is just a single space, then we don't want to
             * output the superblank brackets around it. For multiple spaces,
             * or for whitespace characters other than a single space
             * (for example, '\t', '\n', etc.), we do want to output the
             * superblank brackets around that whitespace.
             */
            boolean writeBrackets = false;

            /* Insert a period before a newline, to mimic the behavior
             * of the C++ deformatter. However, we don't have to completely
             * mimic the behavior of inserting an extra period when one
             * already exists, unless we are in C++ compatibility mode.
             * When in C++ compatibility mode, ignore the existence of any
             * existing periods when adding a period.
             */
            if ((currentChar == '\n') && ((previousChar != '.') || _cppCompat)) {
              writePeriod = true;
            }
            if (currentChar != ' ') { //Whitespace char is other than space
              writeBrackets = true;
            }
            spaceWrite.append((char) currentChar);
            previousChar = currentChar;
            while (Character.isWhitespace((currentChar = inRead.read()))) {
              spaceWrite.append((char) currentChar);
              previousChar = currentChar;
            }
            if (currentChar != -1) {
              writePeriod = false; //There's text after the newline, don't add a period
            }
            if (writePeriod) {
              /* Added periods always have an empty superblank ("[]") after
               * them, to denote that the period was added, so that it can
               * be removed by the reformatter.
               */
              outWrite.append(".[]");
            }
            /* If this section of whitespace is more than one character long,
             * or if it has non-space whitespace, then we do a superblank.
             * If it's only a single space, no superblank.
             */
            if ((spaceWrite.toString().length() > 1) || writeBrackets) {
              outWrite.append('[').append(spaceWrite).append(']');
            } else {
              outWrite.append(spaceWrite.toString());
            }
            if (currentChar != -1) {
              if (isApertiumSpecialCharacter(currentChar)) {
                outWrite.append('\\');
              }
              outWrite.append((char) currentChar);
              previousChar = currentChar;
            }
          } else {
            /* This character could be a special character that needs
             * escaping.
             */
            if (isApertiumSpecialCharacter(currentChar)) {
              outWrite.append('\\');
            }
            outWrite.append((char) currentChar);
            previousChar = currentChar;
          }
        }
      } while ((currentChar = inRead.read()) != -1);
      /* Insert a period at the end if the last character of the stream isn't
       * a period already or whitespace. Except in the case of C++ compat mode.
       * When in C++ compat mode, ignore if there was a period before or not.
       */
      if (((previousChar != '.') || _cppCompat) && !Character.isWhitespace(previousChar)) {
        /* Again, write an empty superblank ("[]") along with the period, to mark
         * it as added for the reformatter.
         */
        outWrite.append(".[]");
      }
    } catch (IOException e) {
      System.err.println("IOException occured in TextFormatter.deFormat()");
      e.printStackTrace();
    }
  }

  @Override
  protected void reFormat(Reader inRead, Appendable outWrite) {
    try {
      int currentChar = inRead.read();
      int previousChar = -1;
      /* This variable is used as a flag for if we're dealing with an extra
       * period inserted by the deformatter or not. When a period is encountered,
       * this flag is set and the period is skipped. If the next character is not
       * a '[', indicating the beginning of a superblank, then it is output.
       * If it *is* a '[', then the period is held until the superblank is resolved.
       * If the superblank is empty ("[]"), then it's marking an extra period that
       * was added, and the period should be discarded. If it's not empty, then the
       * period should be output.
       */
      boolean foundPeriod = false;

      do {
        if (currentChar == '\\') { //Escaped character
          if (foundPeriod) {
            /* A period was the previous character, since this one is not a
             * '[', which would start a superblank, output the period and reset
             * the flag.
             */
            outWrite.append('.');
            foundPeriod = false;
          }

          /* All backslashes in the incoming text are treated as escaping
           * the characters that follow them and are removed, regardless of
           * if the following character is an Apertium stream character or not
           * with the exception of a backslash that occurs at the very end
           * of the stream, which couldn't be escaping anything, so it is just
           * output normally. Note that this case of the single backslash at
           * the end of the input stream should never happen, but there is code
           * to deal with it anyway.
           */
          previousChar = currentChar;
          currentChar = inRead.read();
          if (currentChar == -1) {
            /* This should never happen, we shouldn't get a single backslash
             * at the end of the file, but we should expect the unexpected
             * and deal with it anyway. Go ahead and output the backslash.
             * This is also how the C++ code handles this situation.
             */
            outWrite.append((char) previousChar);
          } else {
            //Output the char that was escaped.
            outWrite.append((char) currentChar);
          }
        } else if (currentChar == '[') { //Start of a superblank
          previousChar = currentChar;
          currentChar = inRead.read();
          /* This writes the contents of the superblank to a separate
           * string buffer so that we can deal with it as a whole after the
           * entire thing has been read, as the logic dealing with the
           * empty superblanks (".[]"), which mark periods added by the
           * deformatter, requires us to have read the superblank
           * to decide if we should output the period or not.
           */
          StringBuilder spaceWrite = new StringBuilder();
          while ((currentChar != -1) && (currentChar != ']')) {
            /* Superblanks should have only whitespace characters in them
             * in the plain text format, so no need to check all the characters
             * inside of them for escaped characters.
             */
            spaceWrite.append((char) currentChar);
            previousChar = currentChar;
            currentChar = inRead.read();
          }
          /* spaceWrite should have all the characters inside the superblank
           * in it. If the length is greater than 0, then we need to check
           * foundPeriod to see if we need to output a period.
           * If it's 0, then it was an empty superblank marking an added period
           * and neither the period, nor the empty string should be output.
           */
          if (spaceWrite.toString().length() > 0) {
            if (foundPeriod) {
              outWrite.append('.');
              //Set foundPeriod to false, since we just output it.
              foundPeriod = false;
            }
            outWrite.append(spaceWrite);
          } else { //Empty superblank, meaning we need to drop that period.
            //Set foundPeriod to false, since we're dropping it.
            foundPeriod = false;
          }
        } else if (currentChar == '.') {
          if (foundPeriod) {
            //Multiple periods in a row, output the previous one.
            outWrite.append('.');
          }
          foundPeriod = true;
        } else { //Not a backslash, period, or '['
          if (foundPeriod) { //No superblank after found period, output period
            outWrite.append('.');
            foundPeriod = false; //Period output, set to false.
          }
          outWrite.append((char) currentChar);
        }
        previousChar = currentChar;
      } while ((currentChar = inRead.read()) != -1);
      /* Have to flush it, or you'll never get any output!
       * This is needed both with and without the BufferedWriter wrapped
       * around the OutputStreamWriter.
       */
      IOUtils.flush(outWrite);
    } catch (IOException e) {
      System.err.println("IOException occured in TextFormatter.reFormat()");
      e.printStackTrace();
    }
  }

  public TextFormatter(String commandLabel) {
    super(commandLabel);
  }

  public TextFormatter() {
    this("TextFormatter");
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws IOException {
    TextFormatter formatter = new TextFormatter();
    formatter.doMain(args);
  }
}
