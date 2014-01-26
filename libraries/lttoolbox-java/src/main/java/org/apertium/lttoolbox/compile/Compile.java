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
import org.apertium.lttoolbox.*;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A compiler of dictionaries to letter transducers
 * It's called 'Compile' and not 'Compiler' to avoid clash with java.lang.Compiler
 *
 * @author Raah
 */
public class Compile {
  public static String COMPILER_DICTIONARY_ELEM = "dictionary";
  public static String COMPILER_ALPHABET_ELEM = "alphabet";
  public static String COMPILER_SDEFS_ELEM = "sdefs";
  public static String COMPILER_SDEF_ELEM = "sdef";
  public static String COMPILER_N_ATTR = "n";
  public static String COMPILER_PARDEFS_ELEM = "pardefs";
  public static String COMPILER_PARDEF_ELEM = "pardef";
  public static String COMPILER_PAR_ELEM = "par";
  public static String COMPILER_ENTRY_ELEM = "e";
  public static String COMPILER_RESTRICTION_ATTR = "r";
  public static String COMPILER_RESTRICTION_LR_VAL = "LR";
  public static String COMPILER_RESTRICTION_RL_VAL = "RL";
  public static String COMPILER_PAIR_ELEM = "p";
  public static String COMPILER_LEFT_ELEM = "l";
  public static String COMPILER_RIGHT_ELEM = "r";
  public static String COMPILER_S_ELEM = "s";
  public static String COMPILER_REGEXP_ELEM = "re";
  public static String COMPILER_SECTION_ELEM = "section";
  public static String COMPILER_ID_ATTR = "id";
  public static String COMPILER_TYPE_ATTR = "type";
  public static String COMPILER_IDENTITY_ELEM = "i";
  public static String COMPILER_FLAG_ELEM = "f";
  public static String COMPILER_VALUE_ATTR = "v";
  public static String COMPILER_JOIN_ELEM = "j";
  public static String COMPILER_BLANK_ELEM = "b";
  public static String COMPILER_POSTGENERATOR_ELEM = "a";
  public static String COMPILER_GROUP_ELEM = "g";
  public static String COMPILER_LEMMA_ATTR = "lm";
  public static String COMPILER_IGNORE_ATTR = "i";
  public static String COMPILER_IGNORE_YES_VAL = "yes";
  /**
   * The paradigm being compiled
   */
  private String current_paradigm = "";
  /**
   * The dictionary section being compiled
   */
  private String current_section = "";
  /**
   * The direction of the compilation, 'lr' (leftSide-to-right) or 'rl'
   * (right-to-leftSide)
   */
  private String direction = "";
  /**
   * List of characters to be considered alphabetic
   */
  private String letters = "";
  /**
   * Identifier of all the symbols during the compilation
   */
  private CompileAlphabet alphabet;
  /** this lookup is needed very often and thus cached */
  private Integer alphabet_cast00;
  /**
   * List of named transducers-paradigms
   */
  private Map<String, TransducerComp> paradigms = new HashMap<String, TransducerComp>();
  /**
   * List of named dictionary sections.
   * MUST be sorted when writing .bin file to retain compatibility with C++ code.
   */
  public Map<String, TransducerComp> sections = new TreeMap<String, TransducerComp>();
  /**
   * List of named prefix copy of a paradigm
   */
  private HashMap<String, HashMap<String, Integer>> prefix_paradigms = new HashMap<String, HashMap<String, Integer>>();
  /**
   * List of named suffix copy of a paradigm
   */
  private HashMap<String, HashMap<String, Integer>> suffix_paradigms = new HashMap<String, HashMap<String, Integer>>();
  /**
   * List of named endings of a suffix copy of a paradgim
   */
  private HashMap<String, HashMap<String, Integer>> postsuffix_paradigms = new HashMap<String, HashMap<String, Integer>>();
  /**
   * Mapping of aliases of characters specified in ACX files
   */
  private HashMap<Integer, HashSet<Integer>> acx_map = new HashMap<Integer, HashSet<Integer>>();
  /**
   * Original char being mapped
   */
  int acx_current_char;
  /**
   * to remember the type of the XML file
   */
  String version = "";
  String encoding = "";
  Boolean standalone = false;
  /**
   * The reader
   */
  XMLStreamReader reader;
  public static boolean DEBUG = false;

  /**
   * The constructor
   */
  public Compile() {
    alphabet = new CompileAlphabet();
    alphabet_cast00 = alphabet.cast(0, 0);
    // LtLocale.tryToSetLocale();
  }

  /**
   * Compile dictionary to letter transducers
   *
   * @param file the address of the XML dictionnary to be read
   * @param dir the direction of the compilation, 'lr' (leftSide-to-right) or 'rl'
   * (right-to-leftSide)
   */
  public void parse(String file, String dir) {
    try {
      direction = dir;
      XMLInputFactory factory = XMLInputFactory.newInstance();
      if (file.equals("-")) {
        reader = factory.createXMLStreamReader(System.in);
      } else {
        reader = factory.createXMLStreamReader(new FileInputStream(file));
      }
      while (reader.hasNext()) {
        procNode();
        reader.next();
      }
      reader.close();
      // Minimize transducers
      for (TransducerComp transducer : sections.values()) {
        transducer.minimize();
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error: Cannot open '" + file + "'.");
    } catch (RuntimeException e) {
      System.err.println("Error (" + e + ") at line " + reader.getLocation().getLineNumber());
      throw e;
    } catch (Throwable ex) {
      System.err.println("Error (" + ex + ") at line " + reader.getLocation().getLineNumber());
      throw new RuntimeException("Error t " + reader.getLocation().getLineNumber(), ex);
    }

  }

  /**
   * Read ACX file.
   *
   * @see http://wiki.apertium.org/wiki/ACX format
   * @param file the address of the file to be read
   * @param dir the direction of the compilation, 'lr' (leftSide-to-right) or 'rl'
   * (right-to-leftSide)
   */
  public void parseACX(String file, String dir) {
    try {
      if (dir.equals(COMPILER_RESTRICTION_LR_VAL)) {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        reader = factory.createXMLStreamReader(new FileInputStream(file));
        while (reader.hasNext()) {
          procNodeACX();
          reader.next();
        }
        reader.close();
      } else {
        throw new RuntimeException("parseACX must only be called while lr compilation");
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error: Cannot open '" + file + "'.");
    } catch (XMLStreamException e) {
      e.printStackTrace();
      throw new RuntimeException("Error: An error occured parsing '" + file + "'.");
    }
  }

  /**
   * Write the result of compilation
   *
   * @param output the stream where write the result
   * @throws java.io.IOException
   */
  public void write(String file) throws IOException {
    TransducerCollection tc = new TransducerCollection();
    tc.alphabet = alphabet;
    tc.letters = letters;
    tc.sections = sections;
    tc.write(file);
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
   * Insert a list of tokens into the paradigm / section being processed
   *
   * @param elements the list
   */
  private void insertEntryTokens(ArrayList<EntryToken> elements) {

    if (DEBUG)
      System.err.println("insertEntryTokens( " + elements);
    if (!current_paradigm.equals("")) {
      // compilation of paradigms
      TransducerComp t = paradigms.get(current_paradigm);
      if (t == null) {
        t = new TransducerComp();
        paradigms.put(current_paradigm, t);
      }

      Integer e = t.getInitial();

      for (int i = 0, limit = elements.size(); i < limit; i++) {
        EntryToken entry = elements.get(i);

        if (entry.isParadigm()) {
          if (!paradigms.containsKey(entry.paradigmName)) {
            paradigms.put(entry.paradigmName, new TransducerComp());
          }
          e = t.insertTransducer(e, paradigms.get(entry.paradigmName));
        } else if (entry.isSingleTransduction()) {
          e = matchTransduction(entry.leftSide, entry.rightSide, e, t);
        } else if (entry.isRegexp()) {
          RegexpCompiler analyzer = new RegexpCompiler();
          analyzer.initialize(alphabet);
          analyzer.compile(entry.regexp);
          t.setEpsilon_Tag(alphabet_cast00);
          e = t.insertTransducer(e, analyzer.getTransducer());
        } else {
          throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
              + "): Invalid entry token.");
        }
      }
      t.setFinal(e);
    } else {
      // compilation of the dictionary
      TransducerComp t;
      if (!sections.containsKey(current_section)) {
        t = new TransducerComp();
        sections.put(current_section, t);
      } else {
        t = sections.get(current_section);
      }
      int e = t.getInitial();

      for (int i = 0, limit = elements.size(); i < limit; i++) {
        EntryToken entry = elements.get(i);
        if (entry.isParadigm()) {
          final String paradigmName = entry.paradigmName;
          if (i == elements.size() - 1) {
            // paradigm sufix
            if (!suffix_paradigms.containsKey(current_section)) {
              suffix_paradigms.put(current_section, new HashMap<String, Integer>());
            }
            if (suffix_paradigms.get(current_section).containsKey(paradigmName)) {
              t.linkStates(e, suffix_paradigms.get(current_section).get(paradigmName), 0);
              e = postsuffix_paradigms.get(current_section).get(paradigmName);
            } else {
              e = t.insertNewSingleTransduction(alphabet_cast00, e);
              suffix_paradigms.get(current_section).put(paradigmName, e);
              t.setEpsilon_Tag(0);
              e = t.insertTransducer(e, paradigms.get(paradigmName));
              if (!postsuffix_paradigms.containsKey(current_section)) {
                postsuffix_paradigms.put(current_section, new HashMap<String, Integer>());
              }
              postsuffix_paradigms.get(current_section).put(paradigmName, e);
            }
          } else if (i == 0) {
            // paradigm prefix
            if (!prefix_paradigms.containsKey(current_section)) {
              prefix_paradigms.put(current_section, new HashMap<String, Integer>());
            }
            if (prefix_paradigms.get(current_section).containsKey(paradigmName)) {
              e = prefix_paradigms.get(current_section).get(paradigmName);
            } else {
              t.setEpsilon_Tag(0);
              e = t.insertTransducer(e, paradigms.get(paradigmName));
              prefix_paradigms.get(current_section).put(paradigmName, e);
            }
          } else {
            // paradigm intermediate
            if (!paradigms.containsKey(paradigmName)) {
              paradigms.put(paradigmName, new TransducerComp());
            }
            t.setEpsilon_Tag(0);
            e = t.insertTransducer(e, paradigms.get(paradigmName));
          }
        } else if (entry.isRegexp()) {
          RegexpCompiler analyzer = new RegexpCompiler();
          analyzer.initialize(alphabet);
          analyzer.compile(entry.regexp);
          t.setEpsilon_Tag(alphabet_cast00);
          e = t.insertTransducer(e, analyzer.getTransducer());
        } else {
          e = matchTransduction(entry.leftSide, entry.rightSide, e, t);
        }
      }
      t.setFinal(e);
    }
  }

  /**
   * Construct symbol pairs by align leftSide side of both parts and insert
   * them into a transducer
   *
   * @param pi leftSide part of the transduction
   * @param pd right part of the transduction
   * @param state the state from wich insert the new transduction
   * @param t the transducer
   * @return the last state of the inserted transduction
   */
  int matchTransduction(ArrayList<Integer> pi, ArrayList<Integer> pd, int state, TransducerComp t) {
    int izqda, dcha, limizqda, limdcha;
    if (direction.equals(COMPILER_RESTRICTION_LR_VAL)) {
      izqda = 0;
      dcha = 0;
      limizqda = pi.size();
      limdcha = pd.size();

      if (pi.size() == 0 && pd.size() == 0) {
        if (DEBUG)
          System.err.println("e = " + t.toString());
        state = t.insertNewSingleTransduction(alphabet_cast00, state);
      } else {
        HashSet<Integer> acx_map_ptr = null;
        int rsymbol = 0;

        while (true) {
          int etiqueta;
          if (izqda == limizqda && dcha == limdcha) {
            break;
          } else if (izqda == limizqda) {
            etiqueta = alphabet.cast(0, pd.get(dcha));
            dcha++;
          } else if (dcha == limdcha) {
            Integer pi_izqda = pi.get(izqda);
            etiqueta = alphabet.cast(pi_izqda, 0);
            acx_map_ptr = acx_map.get(pi_izqda); // perhaps null
            rsymbol = 0;
            izqda++;
          } else {
            Integer pi_izqda = pi.get(izqda);
            Integer pd_dcha = pd.get(dcha);
            etiqueta = alphabet.cast(pi_izqda, pd_dcha);
            acx_map_ptr = acx_map.get(pi_izqda); // perhaps null
            rsymbol = pd_dcha;
            izqda++;
            dcha++;
          }

          int nuevo_estado = t.insertSingleTransduction(etiqueta, state);
          if (acx_map_ptr != null) {
            for (Integer integer : acx_map_ptr) {
              t.linkStates(state, nuevo_estado, alphabet.cast(integer, rsymbol));
            }
          }
          state = nuevo_estado;
        }
      }
      return state;

    } else {
      izqda = 0;
      dcha = 0;
      limizqda = pd.size();
      limdcha = pi.size();

      if (pi.size() == 0 && pd.size() == 0) {
        state = t.insertNewSingleTransduction(alphabet_cast00, state);
      } else {
        HashSet<Integer> acx_map_ptr = null;
        int rsymbol = 0;

        while (true) {
          int etiqueta;
          if (izqda == limizqda && dcha == limdcha) {
            break;
          } else if (izqda == limizqda) {
            etiqueta = alphabet.cast(0, pi.get(dcha));
            dcha++;
          } else if (dcha == limdcha) {
            Integer pd_izqda = pd.get(izqda);
            etiqueta = alphabet.cast(pd_izqda, 0);
            acx_map_ptr = acx_map.get(pd_izqda); // perhaps null
            rsymbol = 0;
            izqda++;
          } else {
            Integer pd_izqda = pd.get(izqda);
            Integer pi_dcha = pi.get(dcha);
            etiqueta = alphabet.cast(pd_izqda, pi_dcha);
            acx_map_ptr = acx_map.get(pd_izqda); // perhaps null
            rsymbol = pi_dcha;
            izqda++;
            dcha++;
          }

          int nuevo_estado = t.insertSingleTransduction(etiqueta, state);
          if (acx_map_ptr != null) {
            for (Integer integer : acx_map_ptr) {
              t.linkStates(state, nuevo_estado, alphabet.cast(integer, rsymbol));
            }
          }
          state = nuevo_estado;
        }
      }
      return state;
    }
  }

  /**
   * Parse the <re> elements
   *
   * @return a list of tokens from the dictionary's entry
   * @throws javax.xml.stream.XMLStreamException
   */
  EntryToken procRegexp() throws XMLStreamException {

    reader.next();
    StringBuffer re = new StringBuffer();
    int start = reader.getTextStart();
    int length = reader.getTextLength();
    while (reader.isCharacters()) {
      start = reader.getTextStart();
      length = reader.getTextLength();
      re.append(reader.getTextCharacters(), start, length);
      reader.next();
    }
    EntryToken et = new EntryToken();
    et.setRegexp(re.toString());
    return et;
  }

  /**
   * Parse the <alphabet> elements
   *
   * @throws javax.xml.stream.XMLStreamException
   */
  private void procAlphabet() throws XMLStreamException {
    reader.next();
    if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
      if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
        int start = reader.getTextStart();
        int length = reader.getTextLength();
        letters = new String(reader.getTextCharacters(), start, length);
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Missing alphabet symbols.");
      }
    }
//      if (letters.length()==0) {
//        System.err.println("Note: A bug in C library libxml makes an empty <alphabet></alphabet> read a newline letters. This is not the case in Java, so the .bin files will differ in length.");
//      }
/*
     * if (reader.hasNext()) {
     * reader.next();
     * if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
     * int start = reader.getTextStart();
     * int length = reader.getTextLength();
     * letters = new String(reader.getTextCharacters(),
     * start,
     * length);
     * } else {
     * throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() +
     * "): not good event type for alphabet symbols.");
     * }
     * } else {
     * throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() +
     * "): Missing alphabet symbols.");
     * }
     */
  }

  /**
   * Parse the <e> elements
   *
   * @throws javax.xml.stream.XMLStreamException
   */
  private void procEntry() throws XMLStreamException {
    String attribute = attrib(COMPILER_RESTRICTION_ATTR);
    String ignore = attrib(COMPILER_IGNORE_ATTR);
    String name = "";
    if (ignore.equals(COMPILER_IGNORE_YES_VAL) || (!(attribute.equals("")) && !(attribute.equals(direction)))) {
      while (!(name.equals(COMPILER_ENTRY_ELEM))) {
        reader.next();
        if (reader.getEventType() == XMLStreamConstants.END_ELEMENT) {
          name = reader.getLocalName();
        }
      }
      return;
    }

    ArrayList<EntryToken> elements = new ArrayList<EntryToken>();

    while (true) {
      if (!reader.hasNext()) {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + "): Parse error.");
      }
      if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
        name = reader.getLocalName();
      }
      skipBlanks();
      name = reader.getLocalName();
      int type = reader.getEventType();

      if (name.equals(COMPILER_PAIR_ELEM)) {
        elements.add(procTransduction());
      } else if (name.equals(COMPILER_IDENTITY_ELEM)) {
        elements.add(procIdentity());
      } else if (name.equals(COMPILER_FLAG_ELEM)) {
        elements.add(procFlag());
      } else if (name.equals(COMPILER_REGEXP_ELEM)) {
        elements.add(procRegexp());
      } else if (name.equals(COMPILER_PAR_ELEM)) {
        EntryToken par = procPar();
        elements.add(par);

        // detection of the use of undefined paradigms
        String p = par.paradigmName;
        TransducerComp t = paradigms.get(p);
        if (t == null) {
          throw new RuntimeException("Error: Undefined paradigm '" + p + "'.");
        }
        // descartar entradas con paradigms vac�os (por las direciones,
        // normalmente
        if (t.isEmpty()) {
          while (!name.equals(COMPILER_ENTRY_ELEM) || type != XMLStreamConstants.END_ELEMENT) {
            reader.next();
            if (reader.hasName()) {
              name = reader.getLocalName();
            }
            type = reader.getEventType();
          }
          return;
        }
        reader.next();
      } else if (name.equals(COMPILER_ENTRY_ELEM) && type == XMLStreamConstants.END_ELEMENT) {
        // insertar elements into letter transducer
        insertEntryTokens(elements);
        reader.next();
        return;
      } else if (reader.isWhiteSpace()) {
      } else if (allBlanks()) {
        if (!reader.hasText()) {
        }
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Invalid inclusion of '<" + name + ">' into '<" + COMPILER_ENTRY_ELEM
            + ">'.");
      }
    }
  }

  /**
   * Method to parse an XML Node
   *
   * @throws javax.xml.stream.XMLStreamException
   */
  private void procNode() throws XMLStreamException {
    int eventType = reader.getEventType();
    String nombre = "";
    if (eventType == XMLStreamConstants.END_ELEMENT
        || eventType == XMLStreamConstants.START_ELEMENT) {
      nombre = reader.getLocalName();
      if (nombre == null) {
        nombre = "";
      }
    }

    if (nombre.equals(COMPILER_ENTRY_ELEM)) { // most often
      procEntry();
    } else if (eventType == XMLStreamConstants.SPACE) { // also often
      //do nothing
    } else if (eventType == XMLStreamConstants.CHARACTERS) {
      //do nothing
    } else if (eventType == XMLStreamConstants.COMMENT) {
      //do nothing
    } else if (nombre.equals(COMPILER_PARDEF_ELEM)) {
      procParDef();
    } else if (nombre.equals(COMPILER_DICTIONARY_ELEM)) {
      /* ignore */
    } else if (eventType == XMLStreamConstants.END_ELEMENT && !nombre.equals(COMPILER_PARDEF_ELEM)) {
      //do nothing
    } else if (nombre.equals(COMPILER_ALPHABET_ELEM)) {
      procAlphabet();
    } else if (nombre.equals(COMPILER_SDEFS_ELEM)) {
      /* ignore */
    } else if (nombre.equals(COMPILER_SDEF_ELEM)) {
      procSDef();
    } else if (nombre.equals(COMPILER_PARDEFS_ELEM)) {
      /* ignore */
    } else if (nombre.equals(COMPILER_SECTION_ELEM)) {
      procSection();
    } else if (eventType == XMLStreamConstants.START_DOCUMENT) {
      version = reader.getVersion();
      encoding = reader.getCharacterEncodingScheme();
      //System.err.println("encoding = " + encoding);

      standalone = reader.isStandalone();
    } else if (eventType == XMLStreamConstants.END_DOCUMENT) {
      //do nothing
    } else {
      XMLPrint.printEvent(reader);
      throw new IllegalStateException("procNode(): don't know how to treat <" + nombre + "> " + XMLPrint.getEventTypeString(eventType));
    }
  }

  /**
   * Method to parse an XML Node in ACX files
   */
  private void procNodeACX() {
    int type = reader.getEventType();
    String name = "";
    if (reader.hasName()) {
      name = reader.getLocalName();
    }
    if (type == XMLStreamConstants.START_ELEMENT) {
      if (name.equals("char")) {
        acx_current_char = (int) (attrib("value").charAt(0));
      } else if (name.equals("equiv-char")) {
        if (!acx_map.containsKey(acx_current_char)) {
          HashSet<Integer> set = new HashSet<Integer>();
          acx_map.put(acx_current_char, set);
        }
        acx_map.get(acx_current_char).add((int) (attrib("value").charAt(0)));
      } else {
      }
    } else if (type == XMLStreamConstants.SPACE) {
    } else if (type == XMLStreamConstants.CHARACTERS) {
    } else if (type == XMLStreamConstants.END_DOCUMENT) {
    } else if (type == XMLStreamConstants.START_DOCUMENT) {
    } else if (type == XMLStreamConstants.END_ELEMENT) {
    } else if (type == XMLStreamConstants.COMMENT) {
    } else {
      throw new RuntimeException("Error in ACX file (" + reader.getLocation().getLineNumber()
          + "," + reader.getLocation().getColumnNumber()
          + "): invalid node '<" + XMLPrint.getEventTypeString(reader.getEventType()) + ">");
    }
  }

  /**
   * Parse the <pardef> elements
   */
  private void procParDef() {
    if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
      current_paradigm = attrib(COMPILER_N_ATTR);

      if (DEBUG)
        System.err.println("current_paradigm1 = " + current_paradigm);
    } else {

      if (DEBUG)
        System.err.println("current_paradigm2 = " + current_paradigm);
      if (paradigms.containsKey(current_paradigm)) {
        if (!paradigms.get(current_paradigm).isEmpty()) {
          paradigms.get(current_paradigm).minimize();
          paradigms.get(current_paradigm).joinFinals();
          current_paradigm = "";
        }
      } else {
        paradigms.put(current_paradigm, new TransducerComp());
      }
    }
  }

  /**
   * Gets an attribute value in the current context from their name
   *
   * @param s the name of the attribute
   * @return the value of the attribute
   */
  private String attrib(String s) {
    String value = reader.getAttributeValue(reader.getNamespaceURI(), s);
    if (value == null) {
      value = "";
    }
    return value;
  }

  /**
   * Parse the <sdef> elements
   */
  private void procSDef() {
    if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
      alphabet.includeSymbol("<" + attrib(COMPILER_N_ATTR) + ">");
    }
  }

  /**
   * Parse the <section> elements
   *
   * @throws javax.xml.stream.XMLStreamException
   */
  private void procSection() throws XMLStreamException {
    int type = reader.getEventType();
    if (type != XMLStreamConstants.END_ELEMENT) {
      final String id = attrib(COMPILER_ID_ATTR);
      final String type_attr = attrib(COMPILER_TYPE_ATTR);
      requireAttribute(id, COMPILER_ID_ATTR, COMPILER_SECTION_ELEM);
      requireAttribute(type_attr, COMPILER_TYPE_ATTR, COMPILER_SECTION_ELEM);
      current_section = id + "@" + type_attr;
    } else {
      current_section = "";
    }
  }

  /**
   * Parse the <i> elements
   *
   * @return a list of tokens from the dictionary's entry
   * @throws javax.xml.stream.XMLStreamException
   */
  EntryToken procIdentity() throws XMLStreamException {
    ArrayList<Integer> both_sides = new ArrayList<Integer>();
    if (!(reader.isStartElement() && reader.isEndElement())) {
      //not an emoty node
    }
    String name = "";
    reader.next();
    while (true) {
      if (reader.isEndElement() && reader.getLocalName().equals(COMPILER_IDENTITY_ELEM)) {
        break;
      }
      if (reader.isStartElement()) {
        name = reader.getLocalName();
        readString(both_sides, name);
        reader.next();
      } else if (reader.isCharacters()) {
        readString(both_sides, "");
        reader.next();
      } else if (reader.isEndElement()) {
        reader.next();
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "," + reader.getLocation().getColumnNumber()
            + "): unexpected type of event.");
      }
    }
    /*
     * while (true) {
     * reader.next();
     * int type = reader.getEventType();
     * if (type == XMLStreamConstants.END_ELEMENT || type == XMLStreamConstants.START_ELEMENT) {
     * name = reader.getLocalName();
     * }
     * if (name.equals(COMPILER_IDENTITY_ELEM)) {
     * break;
     * }
     * readString(both_sides, name);
     * }
     */
    EntryToken e = new EntryToken();
    e.setSingleTransduction(both_sides, both_sides);
    return e;
  }

  /**
   * Parse the <f> elements
   *
   * @return a list of tokens from the dictionary's entry
   */
  EntryToken procFlag() throws XMLStreamException {
    ArrayList<Integer> both_sides = new ArrayList<Integer>();
    //String n = attrib(COMPILER_N_ATTR);
    //String v = attrib(COMPILER_VALUE_ATTR);
    String name = "";
    reader.next();
    while (true) {
      if (reader.isEndElement() && reader.getLocalName().equals(COMPILER_FLAG_ELEM)) {
        break;
      }
      if (reader.isStartElement()) {
        name = reader.getLocalName();
        readString(both_sides, name);
        reader.next();
      } else if (reader.isCharacters()) {
        readString(both_sides, "");
        reader.next();
      } else if (reader.isEndElement()) {
        reader.next();
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "," + reader.getLocation().getColumnNumber()
            + "): unexpected type of event.");
      }
    }

    /*
     * while (true) {
     * reader.next();
     * int type = reader.getEventType();
     * if (type == XMLStreamConstants.END_ELEMENT || type == XMLStreamConstants.START_ELEMENT) {
     * name = reader.getLocalName();
     *
     * System.err.println("name = " + name);
     * }
     * if (name.equals(COMPILER_FLAG_ELEM)) {
     * break;
     * }
     * readString(both_sides, name);
     * }
     */
    EntryToken e = new EntryToken();

    if (direction.equals(COMPILER_RESTRICTION_LR_VAL)) {
      e.setSingleTransduction(new ArrayList<Integer>(), both_sides);
    } else {
      e.setSingleTransduction(both_sides, new ArrayList<Integer>());
    }
    return e;
  }

  /**
   * Parse the <p> elements
   *
   * @return a list of tokens from the dictionary's entry
   * @throws javax.xml.stream.XMLStreamException
   */
  EntryToken procTransduction() throws XMLStreamException {
    ArrayList<Integer> lhs = new ArrayList<Integer>();
    ArrayList<Integer> rhs = new ArrayList<Integer>();
    skipUntil("", COMPILER_LEFT_ELEM);
    reader.next();
    String name = "";
    while (true) {
      if (reader.isEndElement() && reader.getLocalName().equals(COMPILER_LEFT_ELEM)) {
        break;
      }
      if (reader.isStartElement()) {
        name = reader.getLocalName();
        readString(lhs, name);
        reader.next();
      } else if (reader.isCharacters()) {
        readString(lhs, "");
        reader.next();
      } else if (reader.isEndElement()) {
        reader.next();
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "," + reader.getLocation().getColumnNumber()
            + "): unexpected type of event.");
      }
    }
    skipUntil(name, COMPILER_RIGHT_ELEM);
    reader.next();
    while (true) {
      if (reader.isEndElement() && reader.getLocalName().equals(COMPILER_RIGHT_ELEM)) {
        break;
      }
      if (reader.isStartElement()) {
        name = reader.getLocalName();
        readString(rhs, name);
        reader.next();
      } else if (reader.isCharacters()) {
        readString(rhs, "");
        reader.next();
      } else if (reader.isEndElement()) {
        reader.next();
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "," + reader.getLocation().getColumnNumber()
            + "): unexpected type of event.");
      }
    }

    skipUntil(name, COMPILER_PAIR_ELEM);
    EntryToken e = new EntryToken();
    e.setSingleTransduction(lhs, rhs);
    return e;
  }

  /**
   * Parse the <par> elements
   *
   * @return a list of tokens from the dictionary's entry
   * @throws javax.xml.stream.XMLStreamException
   */
  EntryToken procPar() throws XMLStreamException {
    String paradigm_name = attrib(COMPILER_N_ATTR);
    if (!paradigms.containsKey(paradigm_name)) {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): Undefined paradigm '" + paradigm_name + "'.");
    }
    EntryToken e = new EntryToken();
    e.setParadigm(paradigm_name);
    return e;
  }

  /**
   * Reads a string and puts it in a list of integers
   *
   * @param result the list of integers that contains the read string
   * @param name the name of the current node
   * @throws javax.xml.stream.XMLStreamException
   */
  private void readString(List<Integer> result, String name) throws XMLStreamException {
    if (reader.getEventType() == XMLStreamConstants.CHARACTERS) {
      int start = reader.getTextStart();
      int length = reader.getTextLength();
      String value = new String(reader.getTextCharacters(), start, length);
      for (int i = 0; i < value.length(); i++) {
        result.add((int) value.charAt(i));
      }
      //System.err.println("value = " + value);
    } else if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
      if (name.equals(COMPILER_BLANK_ELEM)) {
        requireEmptyError(name);
        result.add((int) (' '));
      } else if (name.equals(COMPILER_JOIN_ELEM)) {
        requireEmptyError(name);
        result.add((int) '+');
      } else if (name.equals(COMPILER_POSTGENERATOR_ELEM)) {
        requireEmptyError(name);
        result.add((int) '~');
      } else if (name.equals(COMPILER_GROUP_ELEM)) {
        int type = reader.getEventType();
        if (type != XMLStreamConstants.END_ELEMENT) {
          result.add((int) '#');
        }
      } else if (name.equals(COMPILER_S_ELEM)) {
        requireEmptyError(name);
        String symbol = "<" + attrib(COMPILER_N_ATTR) + ">";
        if (!alphabet.isSymbolDefined(symbol)) {
          throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
              + "," + reader.getLocation().getColumnNumber() + "): Undefined symbol '" + symbol + "'.");
        }
        result.add(alphabet.cast(symbol));
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "): Invalid specification of element '<" + name
            + ">' in this context.");
      }
    } else if (reader.getEventType() == XMLStreamConstants.END_ELEMENT) {
      if (name.equals(COMPILER_BLANK_ELEM)) {
      } else if (name.equals(COMPILER_JOIN_ELEM)) {
      } else if (name.equals(COMPILER_POSTGENERATOR_ELEM)) {
      } else if (name.equals(COMPILER_GROUP_ELEM)) {
      } else {
        throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
            + "," + reader.getLocation().getColumnNumber()
            + "): unexpected event type '"
            + XMLPrint.getEventTypeString(reader.getEventType()) + "'.");
      }
    } else {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "," + reader.getLocation().getColumnNumber()
          + "): unexpected event type '"
          + XMLPrint.getEventTypeString(reader.getEventType()) + "'.");
    }

    //System.err.println("result = " + result);
  }

  /**
   * Force an attribute to be specified, amd check for it
   *
   * @param value the value of the attribute
   * @param name_attr the name of the attribute
   * @param name_elem the parent of the attribute
   */
  private void requireAttribute(String value, String name_attr, String name_elem) {
    if (value.equals("")) {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): '" + name_elem + "' must be specified non-void '" + name_attr + "' attribute");
    }
  }

  /**
   * Force an element to be empty, and check for it
   *
   * @param name the element
   */
  private void requireEmptyError(String name) {
    //FIXME
    //System.out.println("I dont't know how to check for an \"empty event\" " +
    //    "without reading what is after");
    if (false) {
      throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
          + "): Non-empty element '<" + name + ">' should be empty.");
    }
  }

  //sets the cursor on the next START_ELEMENT
  /**
   * Skip all blank #text nodes before "name"
   *
   * @throws javax.xml.stream.XMLStreamException
   */
  //to be changed
  private void skipBlanks() throws XMLStreamException {
    reader.next();
    while (true) {
      if (reader.isStartElement() || reader.isEndElement()) {
        return;
      } else if (reader.getEventType() == XMLStreamConstants.COMMENT) {
        reader.next();
      } else if (reader.isCharacters()) {
        if (!reader.isWhiteSpace()) {
          throw new RuntimeException("Error (" + reader.getLocation().getLineNumber()
              + "): Invalid construction.");
        }
        reader.next();
      }
    }
  }

  /**
   *
   * @param name the name of the current node
   * @param elem the name of the node until which we want to skip
   * @throws javax.xml.stream.XMLStreamException
   */
  private void skipUntil(String name, String elem) throws XMLStreamException {
    skipBlanks();
    name = reader.getLocalName();
    if (name.equals(elem)) {
      return;
    }
    XMLPrint.printNEvent(reader, 3);
    throw new RuntimeException("Error (" + reader.getLocation().getLineNumber() + ", " + reader.getLocation().getColumnNumber()
        + "): Parse error.");

  }
}
