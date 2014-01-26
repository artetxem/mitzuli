package org.apertium.lttoolbox;

/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
import org.apertium.CommandLineInterface;
import org.xml.sax.SAXException;
import java.io.*;

/**
 * main class for dictionary expansion
 *
 * @author Raah
 */
public class LTExpand {
  private static void showHelp(String name) {
    if (name != null) {
      System.out.println(" v" + CommandLineInterface.PACKAGE_VERSION + ": expand the contents of a dictionary file"
          + "USAGE: " + name + " dictionary_file [output_file]");
    }
  }

  /**
   * Main method
   *
   * @param argv the command line arguments
   * @throws java.io.IOException
   * @throws org.xml.sax.SAXException
   */
  public static void main(String[] argv) throws IOException, SAXException {

    int argc = argv.length;
    Writer output = new OutputStreamWriter(System.out);

    switch (argc) {
      case 1:
        output = new OutputStreamWriter(System.out);
        break;

      case 2:
        output = fwrite(argv[1]);
        if (output == null) {
          throw new RuntimeException("Error: Cannot open file '" + argv[1] + "'.");
        }
        break;
      default:
        showHelp("LTExpand");
        return;
    }

    Expander e = new Expander();
    e.expand(argv[0], output);
    output.close();

  }

  private static Writer fwrite(String s) throws FileNotFoundException {
    final File f = new File(s);
    return new OutputStreamWriter(new FileOutputStream(f));
  }
}
