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
import java.io.FileInputStream;
import org.apertium.lttoolbox.compile.XMLPrint;
import org.apertium.lttoolbox.compile.Compile;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author Raah
 */
public class Expander {
  /**
   * The paradigm being compiled
   */
  String current_paradigm;

  /**
   * The direction of the compilation, 'lr' (left-to-right) or 'rl'
   * (right-to-left)
   */
  //String direction;
  /**
   * Class to represent pairs of strings
   *
   * @author Raah
   */
  public class SPair extends Pair<String, String> {
    public SPair(String obj1, String obj2) {
      super(obj1, obj2);
    }
  }

  private static class EntList extends ArrayList<SPair> {
    public EntList() {
    }

    public EntList(Collection<? extends SPair> sPairs) {
      super(sPairs);
    }
  }

  /**
   * Paradigms
   */
  Map<String, EntList> paradigm;
  Map<String, EntList> paradigm_lr;
  Map<String, EntList> paradigm_rl;
  Writer output;
  XMLStreamReader reader;

  /**
   * Expands the dictionnary
   *
   * @param file the dictionary
   * @param out the output
   * @throws java.io.IOException
   */
  public void expand(String file, Writer out) throws IOException {
    paradigm = new HashMap<String, EntList>();
    paradigm_lr = new HashMap<String, EntList>();
    paradigm_rl = new HashMap<String, EntList>();
    try {
      output = out;
      XMLInputFactory factory = XMLInputFactory.newInstance();
      reader = factory.createXMLStreamReader(new FileInputStream(file));
      while (reader.hasNext()) {
        procNode();
        reader.next();
      }
      reader.close();
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error: Cannot open '" + file + "'.");
    } catch (XMLStreamException e) {
      e.printStackTrace();
      throw new RuntimeException("Error: An error occured parsing '" + file + "'.");
    }
  }

  /**
   * True if all the elements in the current node are blanks
   *
   * @return true if all are blanks
   * @throws javax.xml.stream.XMLStreamException
   */
  private boolean allBlanks() throws XMLStreamException {
    boolean res = true;
    if (!reader.hasText()) {
      return true;
    }
    String text = reader.getText();
    for (int i = 0, limit = text.length(); i < limit; i++) {
      res = res && Character.isWhitespace(text.charAt(i));
    }
    return res;
  }

  /**
   * Append a list of endings to a list of current transductions.
   *
   * @param result the current partial transductions
   * @param endings the endings to be appended
   * @return the result of concatenations
   */
  private EntList append(EntList result, EntList endings) {
    EntList temp = new EntList();
    for (int i = 0; i < result.size(); i++) {
      for (int j = 0; j < endings.size(); j++) {
        temp.add(new SPair(result.get(i).first + endings.get(j).first,
            result.get(i).second + endings.get(j).second));
      }
    }
    result = temp;
    return result;
  }

  /**
   * Append an ending to a list of current transductions.
   *
   * @param result the current partial transductions
   * @param endings the ending to be appended
   * @return the result of concatenations
   */
  private EntList append(EntList result, String endings) {
    for (int i = 0; i < result.size(); i++) {
      result.get(i).first += endings;
      result.get(i).second += endings;
    }
    return result;
  }

  /**
   * Append endings to a list of current transductions
   *
   * @param result the current partial transductions
   * @param endings the endings to be appended
   * @return the result of concatenations
   */
  private EntList append(EntList result, Pair<String, String> endings) {
    for (int i = 0; i < result.size(); i++) {
      result.get(i).first += endings.first;
      result.get(i).second += endings.second;
    }
    return result;
  }

  /**
   * Gets an attribute value with their name and the current context
   *
   * @param s the name of the attribute
   * @return the value of the attribute
   */
  private String attrib(String s) {
    String value = "";
    if (reader.isStartElement() || reader.getEventType() == XMLStreamConstants.ATTRIBUTE) {
      value = reader.getAttributeValue(reader.getNamespaceURI(), s);
      if (value == null) {
        value = "";
      }
    }
    return value;
  }

  /**
   * Compute if the current node is emmpty
   *
   * @param reader the xml stream reader
   * @return true is the current node is empty
   */
  private boolean isEmpty(XMLStreamReader reader) {
    System.err.println("carefull, using the isEmpty() method, "
        + "which is not really implemented yet");
    return (reader.isStartElement() && reader.isEndElement() && !reader.hasText());
  }

  /**
   * Parse the <e> elements
   *
   * @throws javax.xml.stream.XMLStreamException
   * @throws java.io.IOException
   */
  private void procEntry() throws XMLStreamException, IOException {
    String attribute = attrib(Compile.COMPILER_RESTRICTION_ATTR);
    String entrname = attrib(Compile.COMPILER_LEMMA_ATTR);
    String myname = "";
    if (attrib(Compile.COMPILER_IGNORE_ATTR).equals("yes")) {
      do {
        if (!reader.hasNext()) {
          throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + "): Parse error.");
        }
        reader.next();
        if (reader.hasName()) {
          myname = reader.getLocalName();
        }
      } while (!myname.equals(Compile.COMPILER_ENTRY_ELEM));
      return;
    }

    EntList items = new EntList();
    EntList items_lr = new EntList();
    EntList items_rl = new EntList();

    if (attribute.equals(Compile.COMPILER_RESTRICTION_LR_VAL)) {
      items_lr.add(new SPair("", ""));
    } else if (attribute.equals(Compile.COMPILER_RESTRICTION_RL_VAL)) {
      items_rl.add(new SPair("", ""));
    } else {
      items.add(new SPair("", ""));
    }

    while (true) {
      if (!reader.hasNext()) {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + "): Parse error.");
      }
      reader.nextTag();
      String name = "";
      if (reader.hasName()) {
        name = reader.getLocalName();
      }
      int type = reader.getEventType();
      if (name.equals(Compile.COMPILER_PAIR_ELEM)) {
        if (reader.isStartElement()) {
          SPair p = procTransduction();
          items = append(items, p);
          items_lr = append(items_lr, p);
          items_rl = append(items_rl, p);
        }
      } else if (name.equals(Compile.COMPILER_IDENTITY_ELEM)) {
        if (reader.isStartElement()) {
          String val = procIdentity();
          items = append(items, val);
          items_lr = append(items_lr, val);
          items_rl = append(items_rl, val);
        }
      } else if (name.equals(Compile.COMPILER_REGEXP_ELEM)) {
        if (reader.isStartElement()) {
          String val = "__REGEXP__" + procRegexp();
          items = append(items, val);
          items_lr = append(items_lr, val);
          items_rl = append(items_rl, val);
        }
      } else if (name.equals(Compile.COMPILER_PAR_ELEM)) {
        if (!reader.isEndElement()) {
          String p = procPar();
          // detecci�n del uso de paradigmas no definidos

          if (!paradigm.containsKey(p)
              && !paradigm_lr.containsKey(p)
              && !paradigm_rl.containsKey(p)) {
            XMLPrint.printEvent(reader);

            throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + "): Undefined paradigm '" + p + "'.");
          }

          if (attribute.equals(Compile.COMPILER_RESTRICTION_LR_VAL)) {
            if (paradigm.get(p).size() == 0 && paradigm_lr.get(p).size() == 0) {
              name = skip(name, Compile.COMPILER_ENTRY_ELEM);
              return;
            }
            EntList first = new EntList();
            first.addAll(items_lr);
            first = append(first, paradigm.get(p));
            items_lr = append(items_lr, paradigm_lr.get(p));
            items_lr.addAll(first);

          } else if (attribute.equals(Compile.COMPILER_RESTRICTION_RL_VAL)) {
            if (paradigm.get(p).size() == 0 && paradigm_rl.get(p).size() == 0) {
              name = skip(name, Compile.COMPILER_ENTRY_ELEM);
              return;
            }

            EntList first = new EntList();
            first.addAll(items_rl);
            first = append(first, paradigm.get(p));
            items_rl = append(items_rl, paradigm_rl.get(p));
            items_rl.addAll(first);
          } else {
            if (paradigm_lr.get(p).size() > 0) {
              items_lr.addAll(items);
            }
            if (paradigm_rl.get(p).size() > 0) {
              items_rl.addAll(items);
            }
            EntList aux_lr = new EntList();
            aux_lr.addAll(items_lr);
            EntList aux_rl = new EntList();
            aux_rl.addAll(items_rl);
            aux_lr = append(aux_lr, paradigm.get(p));
            aux_rl = append(aux_rl, paradigm.get(p));
            items_lr = append(items_lr, paradigm_lr.get(p));
            items_rl = append(items_rl, paradigm_rl.get(p));
            items = append(items, paradigm.get(p));
            items_lr.addAll(aux_lr);
            items_rl.addAll(aux_rl);
          }
        }
      } else if (name.equals(Compile.COMPILER_ENTRY_ELEM) && type == XMLStreamConstants.END_ELEMENT) {
        if (current_paradigm.equals("")) {
          for (SPair it : items) {
            output.write(it.first);
            output.write(':');
            output.write(it.second);
            output.write('\n');
          }
          for (SPair it : items_lr) {
            output.write(it.first);
            output.write(':');
            output.write('>');
            output.write(':');
            output.write(it.second);
            output.write('\n');
          }
          for (SPair it : items_rl) {
            output.write(it.first);
            output.write(':');
            output.write('<');
            output.write(':');
            output.write(it.second);
            output.write('\n');
          }
        } else {
          if (!paradigm_lr.containsKey(current_paradigm)) {
            paradigm_lr.put(current_paradigm, new EntList());
          }
          paradigm_lr.get(current_paradigm).addAll(items_lr);
          if (!paradigm_rl.containsKey(current_paradigm)) {
            paradigm_rl.put(current_paradigm, new EntList());
          }
          paradigm_rl.get(current_paradigm).addAll(items_rl);
          if (!paradigm.containsKey(current_paradigm)) {
            paradigm.put(current_paradigm, new EntList());
          }
          paradigm.get(current_paradigm).addAll(items);
        }
        return;
      } else if (type == XMLStreamConstants.COMMENT) {
      } else if (reader.isCharacters() && allBlanks()) {
      } else {
        XMLPrint.printEvent(reader);
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + ", " + reader.getLocation().getColumnNumber()
            + "): Invalid inclusion of '<" + name + ">' into '<" + Compile.COMPILER_ENTRY_ELEM
            + ">'.");
      }
    }
  }

  /**
   * Parse the <i> element
   *
   * @return a string from the dictionary's entry
   * @throws javax.xml.stream.XMLStreamException
   */
  private String procIdentity() throws XMLStreamException {
    StringBuilder both_sides = new StringBuilder("");
    String name = "";
    while (true) {
      reader.next();
      if (reader.hasName()) {
        name = reader.getLocalName();
        if (name.equals(Compile.COMPILER_IDENTITY_ELEM)) {
          break;
        }
      }
      readString(both_sides, name);
    }
    return both_sides.toString();
  }

  /**
   * Method to parse an XML Node
   *
   * @throws javax.xml.stream.XMLStreamException
   * @throws java.io.IOException
   */
  private void procNode() throws XMLStreamException, IOException {

    String nombre = "";
    if (reader.hasName()) {
      nombre = reader.getLocalName();
    }
    // HACER: optimizar el orden de ejecuci�n de esta ristra de "ifs"

    if (nombre.equals("")) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_DICTIONARY_ELEM)) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_ALPHABET_ELEM)) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_SDEFS_ELEM)) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_SDEF_ELEM)) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_PARDEFS_ELEM)) {
      /* ignorar */
    } else if (nombre.equals(Compile.COMPILER_PARDEF_ELEM)) {
      procParDef();
    } else if (nombre.equals(Compile.COMPILER_ENTRY_ELEM)) {
      procEntry();
    } else if (nombre.equals(Compile.COMPILER_SECTION_ELEM)) {
      /* ignorar */
    } else {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): Invalid node '<" + nombre + ">'.");

    }
  }

  /**
   * Parse the <par> elements
   *
   * @return the name of the paradigm
   */
  private String procPar() {
    String paradigmName = attrib(Compile.COMPILER_N_ATTR);
    return paradigmName;
  }

  /**
   * Parse the <pardef> elements
   */
  private void procParDef() {
    int type = reader.getEventType();
    if (type != XMLStreamConstants.END_ELEMENT) {
      current_paradigm = attrib(Compile.COMPILER_N_ATTR);
    } else {
      current_paradigm = "";
    }
  }

  /**
   * Parse the <re> elements
   *
   * @return the string representing the regular expression
   * @throws javax.xml.stream.XMLStreamException
   */
  private String procRegexp() throws XMLStreamException {
    reader.next();
    String re = "";
    int start = reader.getTextStart();
    int length = reader.getTextLength();
    while (reader.isCharacters()) {
      start = reader.getTextStart();
      length = reader.getTextLength();
      re += new String(reader.getTextCharacters(), start, length);
      reader.next();
    }
    return re;
  }

  /**
   * Parse the <p> elements
   *
   * @return a pair of strings, left part and right part of a transduction
   * @throws javax.xml.stream.XMLStreamException
   */
  private SPair procTransduction() throws XMLStreamException {

    StringBuilder lhs = new StringBuilder(), rhs = new StringBuilder();
    String name = "";

    name = skip(name, Compile.COMPILER_LEFT_ELEM);
    name = "";
    while (true) {
      reader.next();
      if (reader.hasName()) {
        name = reader.getLocalName();
      }
      if (name.equals(Compile.COMPILER_LEFT_ELEM)) {
        break;
      }
      readString(lhs, name);
    }
    name = skip(name, Compile.COMPILER_RIGHT_ELEM);
    name = "";
    while (true) {
      reader.next();
      if (reader.hasName()) {
        name = reader.getLocalName();
      }
      if (name.equals(Compile.COMPILER_RIGHT_ELEM)) {
        break;
      }
      readString(rhs, name);
    }
    name = skip(name, Compile.COMPILER_PAIR_ELEM);

    return new SPair(lhs.toString(), rhs.toString());
  }

  /**
   * Read a string and append it to result
   *
   * @param result the result string
   * @param name the name of the current node
   */
  private void readString(StringBuilder result, String name) {
    if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
      int start = reader.getTextStart();
      int length = reader.getTextLength();
      result.append(new String(reader.getTextCharacters(), start, length));
    } else if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
      if (name.equals(Compile.COMPILER_BLANK_ELEM)) {
        requireEmptyError(name);
        result.append(' ');
      } else if (name.equals(Compile.COMPILER_JOIN_ELEM)) {
        requireEmptyError(name);
        result.append('+');
      } else if (name.equals(Compile.COMPILER_POSTGENERATOR_ELEM)) {
        requireEmptyError(name);
        result.append('~');
      } else if (name.equals(Compile.COMPILER_GROUP_ELEM)) {
        int type = reader.getEventType();
        if (type != XMLStreamConstants.END_ELEMENT) {
          result.append('#');
        }
      } else if (name.equals(Compile.COMPILER_S_ELEM)) {
        requireEmptyError(name);
        result.append("<" + attrib(Compile.COMPILER_N_ATTR) + ">");
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Invalid specification of element '<" + name
            + ">' in this context.");
      }
    }
  }

  /**
   * Force an element to be empty, and check for it
   *
   * @param name the name of the element
   */
  private void requireEmptyError(String name) {
    if (false) {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): Non-empty element '<" + name + ">' should be empty.");
    }
  }

  /**
   * Skip all nodes before "elem"
   *
   * @param name the name of the node
   * @param elem the name of the expected node
   * @return the name of the arrival node
   * @throws javax.xml.stream.XMLStreamException
   */
  private String skip(String name, String elem) throws XMLStreamException {
    reader.next();
    if (reader.hasName()) {
      name = reader.getLocalName();
    }
    if (reader.isCharacters()) {
      if (!allBlanks()) {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Invalid construction.");
      }
      reader.next();
      if (reader.hasName()) {
        name = reader.getLocalName();
      }
    }
    if (!name.equals(elem)) {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): Expected '<" + elem + ">'.");
    }
    return name;
  }

  /**
   * Skip all blank nodes before the next node named 's'
   *
   * @param s the name of the node
   * @return the name of the arrival node
   * @throws javax.xml.stream.XMLStreamException
   */
  private String skipBlanks(String s) throws XMLStreamException {
    if (reader.isCharacters()) {
      if (!reader.isWhiteSpace()) {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Invalid construction.");
      }
      reader.next();
      if (reader.hasName()) {
        s = reader.getLocalName();
        return s;
      } else {
        System.out.println("possible problem at line (" + reader.getLocation().getLineNumber()
            + ") in call to skipBlanks");
      }
    }
    reader.next();
    if (reader.hasName()) {
      s = reader.getLocalName();
    }
    return s;
  }
}
