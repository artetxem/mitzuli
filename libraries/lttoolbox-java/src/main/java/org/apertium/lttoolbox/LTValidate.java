/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox;

import org.apertium.CommandLineInterface;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

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
/**
 * main validation class
 *
 * @author Raah
 */
public class LTValidate {
  /**
   * usage method
   */
  private static void showHelp() {
    //System.out.println(" v" + PACKAGE_VERSION + ": validate an XML file" +
    //    " according to a schema\n" +
    //    "USAGE : LTValidate XML_File Schema");
    System.out.println(" v" + CommandLineInterface.PACKAGE_VERSION + ": validate an XML file"
        + " according to a schema\n"
        + "USAGE : LTValidate -dix dictionary.xml\n"
        + "        LTValidate -acx dictionary.acx");
  }

  static boolean validateDix(String fn) {
    return validateXmlSchema(LTValidate.class.getResource("/dix.xsd"), fn);
  }

  static boolean validateAcx(String fn) {
    return validateXmlSchema(LTValidate.class.getResource("/acx.xsd"), fn);
  }

  static boolean validateXmlSchema(URL schemaUrl, String xmlFile) {

    System.err.println("schemaUrl = " + schemaUrl);
    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      Schema schema = schemaFactory.newSchema(schemaUrl);
      schema.newValidator().validate(new StreamSource(xmlFile));
      System.out.println("The file " + xmlFile + " is valid considering the schema " + schemaUrl);
      return true;
    } catch (SAXParseException ex) {
      System.err.println("At line " + ex.getLineNumber() + ", column " + ex.getColumnNumber() + ":\n" + ex.getLocalizedMessage());
    } catch (FileNotFoundException ex) {
      System.err.println("File not found: " + ex.getLocalizedMessage());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    System.out.println("The file " + xmlFile + " is NOT valid considering the schema " + schemaUrl);
    return false;
  }

  /*
   *
   *
   * public static void main(String args[]) throws SAXException {
   *
   * //String xmlFile="testdata/apertium-fr-es.fr.dix";
   * String xmlFile="testdata/short-invalid.dix";
   * String schemaFile="testdata/dix.xsd";
   * validateXmlSchema(schemaFile, xmlFile);
   * }
   *
   */
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    final int argc = args.length;
    boolean ok = false;
    if (argc < 1 || argc > 2) {
      showHelp();
      return;
    }
    if (argc == 1) {
      String fn = args[0];
      if (fn.endsWith(".dix"))
        ok = validateDix(fn);
      else if (fn.endsWith(".acx"))
        ok = validateAcx(fn);
      else {
        showHelp();
        return;
      }
    } else {
      if (args[0].equals("-dix"))
        ok = validateDix(args[1]);
      else if (args[0].equals("-acx"))
        ok = validateAcx(args[1]);
      else
        try {
          ok = validateXmlSchema(new URL(args[0]), args[1]);
        } catch (MalformedURLException ex) {
          System.out.println("Error in file name " + args[0]);
        }
    }

    // Give exit values according to whether it validated or not
    if (ok)
      System.exit(0);  // OK - this will never be invoked from a library
    else
      System.exit(1); // OK - this will never be invoked from a library
  }
}
