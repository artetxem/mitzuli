package org.apertium.lttoolbox.compile;

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
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * This class contains several print methods useful for
 * debugging XML parsing
 *
 * @author Raah
 */
public class XMLPrint {
  /**
   * Print n XML events
   *
   * @param reader the XML stream reader
   * @param n the number of events to be printed
   * @throws javax.xml.stream.XMLStreamException
   */
  public static void printNEvent(XMLStreamReader reader, int n) throws XMLStreamException {
    for (int i = 0; i < n; i++) {
      System.out.println("type of event : " + getEventTypeString(reader.getEventType()));
      printEvent(reader);
      System.out.println();
      reader.next();
    }
  }

  /**
   * Print one XML event
   *
   * @param reader the XML stream reader
   */
  public static void printEvent(XMLStreamReader reader) {

    switch (reader.getEventType()) {

      case XMLStreamConstants.START_ELEMENT:
        System.out.print("<");
        printName(reader);
        printNamespaces(reader);
        printAttributes(reader);
        System.out.print(">");
        break;

      case XMLStreamConstants.END_ELEMENT:
        System.out.print("</");
        printName(reader);
        System.out.print(">");
        break;

      case XMLStreamConstants.SPACE:

      case XMLStreamConstants.CHARACTERS:
        int start = reader.getTextStart();
        int length = reader.getTextLength();
        System.out.print(new String(reader.getTextCharacters(),
            start,
            length));
        break;

      case XMLStreamConstants.PROCESSING_INSTRUCTION:
        System.out.print("<?");
        if (reader.hasText()) {
          System.out.print(reader.getText());
        }
        System.out.print("?>");
        break;

      case XMLStreamConstants.CDATA:
        System.out.print("<![CDATA[");
        start = reader.getTextStart();
        length = reader.getTextLength();
        System.out.print(new String(reader.getTextCharacters(),
            start,
            length));
        System.out.print("]]>");
        break;

      case XMLStreamConstants.COMMENT:
        System.out.print("<!--");
        if (reader.hasText()) {
          System.out.print(reader.getText());
        }
        System.out.print("-->");
        break;

      case XMLStreamConstants.ENTITY_REFERENCE:
        System.out.print(reader.getLocalName() + "=");
        if (reader.hasText()) {
          System.out.print("[" + reader.getText() + "]");
        }
        break;

      case XMLStreamConstants.START_DOCUMENT:
        System.out.print("<?xml");
        System.out.print(" version='" + reader.getVersion() + "'");
        System.out.print(" encoding='" + reader.getCharacterEncodingScheme() + "'");
        if (reader.isStandalone()) {
          System.out.print(" standalone='yes'");
        } else {
          System.out.print(" standalone='no'");
        }
        System.out.print("?>");
        break;

    }
  }

  /**
   * Convert the integer constant that is the event type
   * to the corresponding string
   *
   * @param eventType the evnt type to be converted
   * @return the string corresponding to the event type
   */
  public static final String getEventTypeString(int eventType) {

    switch (eventType) {

      case XMLStreamConstants.START_ELEMENT:
        return "START_ELEMENT";

      case XMLStreamConstants.END_ELEMENT:
        return "END_ELEMENT";

      case XMLStreamConstants.PROCESSING_INSTRUCTION:
        return "PROCESSING_INSTRUCTION";

      case XMLStreamConstants.CHARACTERS:
        return "CHARACTERS";

      case XMLStreamConstants.COMMENT:
        return "COMMENT";

      case XMLStreamConstants.START_DOCUMENT:
        return "START_DOCUMENT";

      case XMLStreamConstants.END_DOCUMENT:
        return "END_DOCUMENT";

      case XMLStreamConstants.ENTITY_REFERENCE:
        return "ENTITY_REFERENCE";

      case XMLStreamConstants.ATTRIBUTE:
        return "ATTRIBUTE";

      case XMLStreamConstants.DTD:
        return "DTD";

      case XMLStreamConstants.CDATA:
        return "CDATA";

      case XMLStreamConstants.SPACE:
        return "SPACE";
    }

    return "UNKNOWN_EVENT_TYPE , " + eventType;
  }

  /**
   * Print the name of the current node
   *
   * @param reader the XML stream reader
   */
  private static void printName(XMLStreamReader reader) {
    if (reader.hasName()) {
      String prefix = reader.getPrefix();
      String uri = reader.getNamespaceURI();
      String localName = reader.getLocalName();
      printName(prefix, uri, localName);
    }
  }

  /**
   * Print the string corresponding to the name of the node
   * described by the parameters
   *
   * @param prefix
   * @param uri
   * @param localName
   */
  private static void printName(String prefix,
      String uri,
      String localName) {
    if (uri != null && !("".equals(uri))) {
      System.out.print("['" + uri + "']:");
    }
    if (localName != null) {
      System.out.print(localName);
    }
  }

  /**
   * Print the attributes of a node
   *
   * @param reader the XML stream reader
   */
  private static void printAttributes(XMLStreamReader reader) {
    for (int i = 0; i < reader.getAttributeCount(); i++) {
      printAttribute(reader, i);
    }
  }

  /**
   * Print attribute of the current node given its index
   *
   * @param reader the XML stream reader
   * @param index the index of the attribute to be printed
   */
  private static void printAttribute(XMLStreamReader reader, int index) {
    String prefix = reader.getAttributePrefix(index);
    String namespace = reader.getAttributeNamespace(index);
    String localName = reader.getAttributeLocalName(index);
    String value = reader.getAttributeValue(index);
    System.out.print(" ");
    printName(prefix, namespace, localName);
    System.out.print("='" + value + "'");
  }

  /**
   * Print the name spaces of the current node
   *
   * @param reader the XML stream reader
   */
  private static void printNamespaces(XMLStreamReader reader) {
    for (int i = 0; i < reader.getNamespaceCount(); i++) {
      printNamespace(reader, i);
    }
  }

  /**
   * Print a namespace of the current node given its index
   *
   * @param reader the XML stream reader
   * @param index the index of the namespace to be printed
   */
  private static void printNamespace(XMLStreamReader reader, int index) {
    String prefix = reader.getNamespacePrefix(index);
    String uri = reader.getNamespaceURI(index);
    System.out.print(" ");
    if (prefix == null) {
      System.out.print("xmlns ='" + uri + "'");
    } else {
      System.out.print("xmlns:" + prefix + "='" + uri + "'");
    }
  }
}
