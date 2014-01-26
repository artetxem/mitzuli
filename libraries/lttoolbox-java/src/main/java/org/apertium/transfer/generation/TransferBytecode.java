/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer.generation;

import static com.sun.org.apache.bcel.internal.Constants.ACC_PRIVATE;
import static com.sun.org.apache.bcel.internal.Constants.ACC_PUBLIC;
import static com.sun.org.apache.bcel.internal.Constants.ACC_SUPER;
import static com.sun.org.apache.bcel.internal.Constants.INVOKEINTERFACE;
import static com.sun.org.apache.bcel.internal.Constants.INVOKESPECIAL;
import static com.sun.org.apache.bcel.internal.Constants.INVOKESTATIC;
import static com.sun.org.apache.bcel.internal.Constants.INVOKEVIRTUAL;

import com.sun.org.apache.bcel.internal.generic.ArrayType;
import com.sun.org.apache.bcel.internal.generic.BranchHandle;
import com.sun.org.apache.bcel.internal.generic.ClassGen;
import com.sun.org.apache.bcel.internal.generic.ConstantPoolGen;
import com.sun.org.apache.bcel.internal.generic.FieldGen;
import com.sun.org.apache.bcel.internal.generic.GOTO;
import com.sun.org.apache.bcel.internal.generic.IFEQ;
import com.sun.org.apache.bcel.internal.generic.IFLE;
import com.sun.org.apache.bcel.internal.generic.IFNE;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.ARRAYLENGTH;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.DUP;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.ICONST_0;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.ICONST_1;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.IRETURN;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.ISUB;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.NOP;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.POP;
import static com.sun.org.apache.bcel.internal.generic.InstructionConstants.RETURN;
import com.sun.org.apache.bcel.internal.generic.InstructionFactory;
import static com.sun.org.apache.bcel.internal.generic.InstructionFactory.*;
import com.sun.org.apache.bcel.internal.generic.InstructionHandle;
import com.sun.org.apache.bcel.internal.generic.InstructionList;
import com.sun.org.apache.bcel.internal.generic.MethodGen;
import com.sun.org.apache.bcel.internal.generic.TargetLostException;
import com.sun.org.apache.bcel.internal.generic.Type;
import static com.sun.org.apache.bcel.internal.generic.Type.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apertium.CommandLineInterface;
import static org.apertium.transfer.generation.DOMTools.*;
import static org.apertium.utils.IOUtils.openFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 *
 * @author Jacob Nordfalk, Mikel Artetxe
 */
public class TransferBytecode {
  private static final Type APERTIUM_RE = getType(org.apertium.transfer.ApertiumRE.class);
  private static final Type WORD_LIST = getType(org.apertium.transfer.WordList.class);
  private static final Type TRANSFER_WORD = getType(org.apertium.transfer.TransferWord.class);
  private static final Type INTERCHUNK_WORD = getType(org.apertium.interchunk.InterchunkWord.class);
  private static final Type APPENDABLE = getType(java.lang.Appendable.class);
  private static final Type STRING_BUILDER = getType(java.lang.StringBuilder.class);
  private static final Type INTERCHUNK_WORD_ARRAY = new ArrayType(INTERCHUNK_WORD, 1);
  private static final Type STRING_ARRAY = new ArrayType(STRING, 1);
  private static final Type CHAR_SEQUENCE = getType(java.lang.CharSequence.class);
  private Type WORD;
  private String className;
  private String fullClassName;
  private ClassGen cg;
  private ConstantPoolGen cp;
  private InstructionList il;
  private InstructionFactory factory;
  private FieldGen fg;
  private MethodGen mg;

  private enum ParseMode {
    TRANSFER, INTERCHUNK, POSTCHUNK
  }

  private ParseMode parseMode;
  //For checking macro names and numbers of parameters
  private HashMap<String, Integer> macroList = new HashMap<String, Integer>();
  //For checking attributes. Order is important
  private LinkedHashSet<String> attrList = new LinkedHashSet<String>();
  //For checking variables
  private LinkedHashSet<String> varList = new LinkedHashSet<String>();
  //For checking lists
  private LinkedHashSet<String> listList = new LinkedHashSet<String>();
  //The number of parameters in the rule/macro/method currently being defined
  private int currentNumberOfWordInParameterList;
  private Element currentNode;

  private void writeMethodBody(Element c0) {
    for (Element instr : listElements(c0.getChildNodes())) {
      processInstruction(instr);
    }
  }

  private void processLu(Element e) {
    // the lexical unit should only be outputted if it contains something
    boolean surelyNotEmpty = false;
    for (Element lu : listChildren(e)) {
      String n = lu.getTagName();
      if (n.equals("lit-tag") || n.equals("lit") && !lu.getAttribute("v").isEmpty() || n.equals("b") && lu.getAttribute("pos").isEmpty()) {
        surelyNotEmpty = true;
        break;
      }
    }
    if (surelyNotEmpty) {
      il.append(createLoad(APPENDABLE, 1));
      il.append(factory.createConstant('^'));
      il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      for (Element lu : listChildren(e)) {
        evalString(lu);
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      }
      il.append(factory.createConstant('$'));
      il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      il.append(POP);
    } else {
      //Perhaps empty expression. Do a temp string and evaluate runtime.
      il.append(factory.createNew("java.lang.StringBuilder"));
      il.append(DUP);
      il.append(factory.createInvoke("java.lang.StringBuilder", "<init>", VOID, NO_ARGS, INVOKESPECIAL));
      for (Element lu : listChildren(e)) {
        evalString(lu);
        il.append(factory.createInvoke("java.lang.StringBuilder", "append", STRING_BUILDER, new Type[]{STRING}, INVOKEVIRTUAL));
      }
      il.append(factory.createInvoke("java.lang.StringBuilder", "toString", STRING, NO_ARGS, INVOKEVIRTUAL));
      final int index = parseMode == ParseMode.POSTCHUNK ? 4 : currentNumberOfWordInParameterList * 2 + 1;
      il.append(createStore(STRING, index));
      il.append(createLoad(STRING, index));
      il.append(factory.createInvoke("java.lang.String", "length", INT, NO_ARGS, INVOKEVIRTUAL));
      BranchHandle ifle = il.append(new IFLE(null));
      il.append(createLoad(APPENDABLE, 1));
      il.append(factory.createConstant('^'));
      il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      il.append(createLoad(STRING, index));
      il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      il.append(factory.createConstant('$'));
      il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      il.append(POP);
      ifle.setTarget(il.append(NOP));
    }
  }

  private String getPathAsString(Node n) {
    if (n == null)
      return "";
    String path = "";
    do {
      String attrss = "";
      NamedNodeMap attrs = n.getAttributes();
      //for (int i=0; i<attrs.getLength(); i++) attrss += " "+attrs.item(i).getNodeName()+"="+attrs.item(i).getNodeValue();
      if (attrs != null) {
        for (int i = 0; i < attrs.getLength(); i++)
          attrss += " " + attrs.item(i);
      }

      if (path.length() > 0)
        path = "/" + path;
      path = "<" + n.getNodeName() + attrss + ">" + path;
      n = n.getParentNode();
    } while (!(n instanceof Document));
    return " - for " + path;
  }

  private void printErrorMessage(String message) {
    message = message + getPathAsString(currentNode);
    System.err.println(message);
  }

  private String escapeStr(String unescaped) {
    return unescaped.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String attrItemRegexp(ArrayList<String> items) {
    String item0 = items.get(0);
    int startSame = 0;
    stop:
    while (startSame < item0.length()) {
      char ch = item0.charAt(startSame);
      for (String item : items)
        if (startSame == item.length() || item.charAt(startSame) != ch)
          break stop;
      startSame++;
    }

    int stopSame = 0;
    stop:
    while (stopSame < item0.length() - startSame) {
      char ch = item0.charAt(item0.length() - stopSame - 1);
      for (String item : items)
        if (stopSame == item.length() || item.charAt(item.length() - stopSame - 1) != ch)
          break stop;
      stopSame++;
    }

    StringBuilder re = null;
    for (String item : items) {
      if (re == null)
        re = new StringBuilder(items.size() * 20);
      else
        re.append('|');
      re.append(escapeStr(item.substring(startSame, item.length() - stopSame)));
    }
    String res = "<" + item0.substring(0, startSame) + (re.length() == 0 ? "" : "(?:" + re.toString() + ")") + item0.substring(item0.length() - stopSame) + ">";
    res = res.replace(".", "><");

    return res;
  }

  public static String javaIdentifier(String str) {
    return str.replaceAll("\\W", "_");
  }

  //
  //  Code from transfer.c
  //
  private void evalString(Element e) {
    currentNode = e;
    String n = e.getTagName();
    if (n.equals("clip")) {
      // the 'side' attribute only really makes sense when a bilingual dictionary is used to translate the words (i.e. english 'dog<n><sg>' is translated to for example esperanto 'hundo<n><pl>').
      // If no bilingual dictionary is used (i.e. for apertium-transfer -n, for apertium-interchunk and for apertium-postchunk), then the sl and tl values will be the same
      String side = parseMode == ParseMode.TRANSFER ? e.getAttribute("side") : "tl";
      String part = e.getAttribute("part");
      String pos = e.getAttribute("pos");
      String queue = (e.getAttribute("queue").equals("no") ? "NoQueue" : "");

      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, attr(part), APERTIUM_RE));
      il.append(factory.createInvoke(WORD.toString(), side + queue, STRING, new Type[]{APERTIUM_RE}, INVOKEVIRTUAL));

      // fix for apertium/interchunk.cc evalString(): if(ti.getContent() == "content") then we need to strip the brackets
      if ("content".equals(part)) {
        il.append(factory.createInvoke(TRANSFER_WORD.toString(), "stripBrackets", STRING, new Type[]{STRING}, INVOKESTATIC));
      }

      String as = e.getAttribute("link-to");
      if (!as.isEmpty()) {
        il.append(factory.createInvoke("java.lang.String", "length", INT, NO_ARGS, INVOKEVIRTUAL));
        BranchHandle ifeq = il.append(new IFNE(null));
        il.append(factory.createConstant(""));
        BranchHandle g = il.append(new GOTO(null));
        ifeq.setTarget(il.append(factory.createConstant("<" + as + ">")));
        g.setTarget(il.append(NOP));
      }
    } else if (n.equals("lit-tag")) {
      il.append(factory.createConstant("<" + e.getAttribute("v").replaceAll("\\.", "><") + ">"));
    } else if (n.equals("lit")) {
      il.append(factory.createConstant(e.getAttribute("v")));
    } else if (n.equals("b")) {
      String pos = e.getAttribute("pos");
      if (pos.isEmpty())
        il.append(factory.createConstant(" "));
      else
        blank(pos);
    } else if (n.equals("get-case-from")) {
      String pos = e.getAttribute("pos");
      String queue = (e.getAttribute("queue").equals("no") ? "NoQueue" : "");
      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, "attr_lem", APERTIUM_RE));
      il.append(factory.createInvoke(WORD.toString(), "sl" + queue, STRING, new Type[]{APERTIUM_RE}, INVOKEVIRTUAL));
      evalString(getFirstChildElement(e));
      il.append(factory.createInvoke(TRANSFER_WORD.toString(), "copycase", STRING, new Type[]{STRING, STRING}, INVOKESTATIC));
    } else if (n.equals("var")) {
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, var(e.getAttribute("n")), STRING));
    } else if (n.equals("case-of")) {
      // the 'side' attribute only really makes sense when a bilingual dictionary is used to translate the words (i.e. english 'dog<n><sg>' is translated to for example esperanto 'hundo<n><pl>').
      // If no bilingual dictionary is used (i.e. for apertium-transfer -n, for apertium-interchunk and for apertium-postchunk), then the sl and tl values will be the same
      String side = parseMode == ParseMode.TRANSFER ? e.getAttribute("side") : "tl";
      String part = e.getAttribute("part");
      String pos = e.getAttribute("pos");
      String queue = (e.getAttribute("queue").equals("no") ? "NoQueue" : "");

      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, attr(part), APERTIUM_RE));
      il.append(factory.createInvoke(WORD.toString(), side + queue, STRING, new Type[]{APERTIUM_RE}, INVOKEVIRTUAL));

      // fix for apertium/interchunk.cc evalString(): if(ti.getContent() == "content") then we need to strip the brackets
      if ("content".equals(part)) {
        il.append(factory.createInvoke(TRANSFER_WORD.toString(), "stripBrackets", STRING, new Type[]{STRING}, INVOKESTATIC));
      }

      il.append(factory.createInvoke(TRANSFER_WORD.toString(), "caseOf", STRING, new Type[]{STRING}, INVOKESTATIC));
    } else if (n.equals("concat")) {
      il.append(factory.createNew("java.lang.StringBuilder"));
      il.append(DUP);
      il.append(factory.createInvoke("java.lang.StringBuilder", "<init>", VOID, NO_ARGS, INVOKESPECIAL));
      for (Element c : listElements(e.getChildNodes())) {
        evalString(c);
        il.append(factory.createInvoke("java.lang.StringBuilder", "append", STRING_BUILDER, new Type[]{STRING}, INVOKEVIRTUAL));
      }
      il.append(factory.createInvoke("java.lang.StringBuilder", "toString", STRING, NO_ARGS, INVOKEVIRTUAL));
    } else if (n.equals("lu-count") && parseMode == ParseMode.POSTCHUNK) {
      // the number of lexical units inside the chunk is the length of the words array, but as we might be in a
      // macro, where we dont have access to the array, we use a global variable
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, "lu_count", STRING));
    } else {
      throwParseError("ERROR: unexpected rvalue expression " + e);
      il.append(factory.createConstant(""));
    }
  }

  private void processOut(Element instr) {
    currentNode = instr;
    for (Element e : listChildren(instr)) {
      String n = e.getTagName();
      if (n.equals("lu")) {
        processLu(e);
      } else if (n.equals("mlu")) {
        il.append(createLoad(APPENDABLE, 1));
        il.append(factory.createConstant('^'));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
        for (java.util.Iterator<Element> it = listChildren(e).iterator(); it.hasNext();) {
          Element mlu = it.next();
          for (Element lu : listChildren(mlu)) {
            evalString(lu);
            il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
          }
          if (it.hasNext()) {
            il.append(factory.createConstant('+'));
            il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
          }
        }
        il.append(factory.createConstant('$'));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
        il.append(POP);
      } else if (n.equals("chunk")) {
        processChunk(e);
      } else {
        il.append(createLoad(APPENDABLE, 1));
        evalString(e);
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
        il.append(POP);
      }
    }
  }

  private void processChunk(Element e) {
    /* If we are processing an interchunk file, chunk tags should be treated
     * like lu tags?
     */
    if (parseMode == ParseMode.INTERCHUNK) {
      /* Try just calling processLu() for now, if that doesn't work, will have
       * to try something else. ;)
       * Not sure if this is the right way to go, may be causing the issues with
       * missing the ^ and $ on chunks in Interchunk.
       */
      processLu(e);
      return;
    }

    currentNode = e;
    String name = e.getAttribute("name");
    String namefromvar = e.getAttribute("namefrom");
    String caseofchunkvar = e.getAttribute("case");

    il.append(createLoad(APPENDABLE, 1));
    il.append(factory.createConstant('^'));
    il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
    if (caseofchunkvar.isEmpty()) {
      if (!name.isEmpty()) {
        il.append(factory.createConstant(name));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      } else if (!namefromvar.isEmpty()) {
        il.append(createThis());
        il.append(factory.createGetField(fullClassName, var(namefromvar), STRING));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      } else {
        printErrorMessage("ERROR: you must specify either 'name' or 'namefrom' for the 'chunk' element");
      }
    } else {
      if (!name.isEmpty()) {
        il.append(createThis());
        il.append(factory.createGetField(fullClassName, var(caseofchunkvar), STRING));
        il.append(factory.createConstant(name));
        il.append(factory.createInvoke(TRANSFER_WORD.toString(), "copycase", STRING, new Type[]{STRING, STRING}, INVOKESTATIC));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      } else if (!namefromvar.isEmpty()) {
        il.append(createThis());
        il.append(factory.createGetField(fullClassName, var(caseofchunkvar), STRING));
        il.append(createThis());
        il.append(factory.createGetField(fullClassName, var(namefromvar), STRING));
        il.append(factory.createInvoke(TRANSFER_WORD.toString(), "copycase", STRING, new Type[]{STRING, STRING}, INVOKESTATIC));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      } else {
        printErrorMessage("ERROR: you must specify either 'name' or 'namefrom' for the 'chunk' element");
      }
    }

    for (Element c0 : listChildren(e)) {
      String n = c0.getTagName();
      if (n.equals("tags")) {
        for (Element tag : listChildren(c0)) {
          evalString(findElementSibling(tag.getFirstChild()));
          il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
        }
        il.append(factory.createConstant('{'));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      } else if (n.equals("lu")) {
        processLu(c0);
      } else if (n.equals("mlu")) {
        il.append(factory.createConstant('^'));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
        for (java.util.Iterator<Element> it = listChildren(c0).iterator(); it.hasNext();) {
          Element mlu = it.next();
          for (Element lu : listChildren(mlu)) {
            evalString(lu);
            il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
          }
          if (it.hasNext()) {
            il.append(factory.createConstant('+'));
            il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
          }
        }
        il.append(factory.createConstant('$'));
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR}, INVOKEINTERFACE));
      } else {
        evalString(c0);
        il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
      }
    }
    il.append(factory.createConstant("}$"));
    il.append(factory.createInvoke("java.lang.Appendable", "append", APPENDABLE, new Type[]{CHAR_SEQUENCE}, INVOKEINTERFACE));
    il.append(POP);
  }

  private void processInstruction(Element instr) {
    currentNode = instr;
    String n = instr.getTagName();
    if (n.equals("choose")) {
      processChoose(instr);
    } else if (n.equals("let")) {
      processLet(instr);
    } else if (n.equals("append")) {
      processAppend(instr);
    } else if (n.equals("out")) {
      processOut(instr);
    } else if (n.equals("call-macro")) {
      processCallMacro(instr);
    } else if (n.equals("modify-case")) {
      processModifyCase(instr);
    } else {
      throwParseError("processInstruction(n = " + n);
    }
  }

  private void processLet(Element instr) {
    currentNode = instr;
    Element leftSide = findElementSibling(instr.getFirstChild());
    Element rightSide = findElementSibling(leftSide.getNextSibling());
    String n = leftSide.getTagName();
    if (n.equals("var")) {
      String name = leftSide.getAttribute("n");
      il.append(createThis());
      evalString(rightSide);
      il.append(factory.createPutField(fullClassName, var(name), STRING));
    } else if (n.equals("clip")) {
      // the 'side' attribute only really makes sense when a bilingual dictionary is used to translate the words (i.e. english 'dog<n><sg>' is translated to for example esperanto 'hundo<n><pl>').
      // If no bilingual dictionary is used (i.e. for apertium-transfer -n, for apertium-interchunk and for apertium-postchunk), then the sl and tl values will be the same
      String side = parseMode == ParseMode.TRANSFER ? leftSide.getAttribute("side") : "tl";
      String part = leftSide.getAttribute("part");
      String pos = leftSide.getAttribute("pos");
      String queue = (leftSide.getAttribute("queue").equals("no") ? "NoQueue" : "");

      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, attr(part), APERTIUM_RE));
      evalString(rightSide);
      il.append(factory.createInvoke(WORD.toString(), side + "Set" + queue, VOID, new Type[]{APERTIUM_RE, STRING}, INVOKEVIRTUAL));
    } else {
      throwParseError(n);
    }
  }

  private void processAppend(Element instr) {
    currentNode = instr;
    String var = var(instr.getAttribute("n"));

    il.append(createThis());
    il.append(factory.createNew("java.lang.StringBuilder"));
    il.append(DUP);
    il.append(createThis());
    il.append(factory.createGetField(fullClassName, var, STRING));
    il.append(factory.createInvoke("java.lang.StringBuilder", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
    Element appendElement = findElementSibling(instr.getFirstChild());
    while (appendElement != null) {
      evalString(appendElement);
      il.append(factory.createInvoke("java.lang.StringBuilder", "append", STRING_BUILDER, new Type[]{STRING}, INVOKEVIRTUAL));
      appendElement = findElementSibling(appendElement.getNextSibling());
    }
    il.append(factory.createInvoke("java.lang.StringBuilder", "toString", STRING, NO_ARGS, INVOKEVIRTUAL));
    il.append(factory.createPutField(fullClassName, var, STRING));
  }

  private void processModifyCase(Element instr) {
    currentNode = instr;
    Element leftSide = findElementSibling(instr.getFirstChild());
    Element rightSide = findElementSibling(leftSide.getNextSibling());

    String n = leftSide.getTagName();
    if (n.equals("var")) {
      String name = leftSide.getAttribute("n");
      if (varList.contains(name)) {
        String var = "var_" + name;
        il.append(createThis());
        evalString(rightSide);
        il.append(createThis());
        il.append(factory.createGetField(fullClassName, var, STRING));
        il.append(factory.createInvoke(TRANSFER_WORD.toString(), "copycase", STRING, new Type[]{STRING, STRING}, INVOKESTATIC));
        il.append(factory.createPutField(fullClassName, var, STRING));
      } else {
        printErrorMessage("WARNING: variable " + name + " doesent exist. Ignoring modify-case");
      }
    } else if (n.equals("clip")) {
      // the 'side' attribute only really makes sense when a bilingual dictionary is used to translate the words (i.e. english 'dog<n><sg>' is translated to for example esperanto 'hundo<n><pl>').
      // If no bilingual dictionary is used (i.e. for apertium-transfer -n, for apertium-interchunk and for apertium-postchunk), then the sl and tl values will be the same
      String side = parseMode == ParseMode.TRANSFER ? leftSide.getAttribute("side") : "tl";
      String part = leftSide.getAttribute("part");
      String pos = leftSide.getAttribute("pos");
      String queue = (leftSide.getAttribute("queue").equals("no") ? "NoQueue" : "");

      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, attr(part), APERTIUM_RE));

      evalString(rightSide);

      word(pos);
      il.append(createThis());
      il.append(factory.createGetField(fullClassName, attr(part), APERTIUM_RE));
      il.append(factory.createInvoke(WORD.toString(), side + queue, STRING, new Type[]{APERTIUM_RE}, INVOKEVIRTUAL));

      // fix for apertium/interchunk.cc evalString(): if(ti.getContent() == "content") then we need to strip the brackets
      if ("content".equals(part)) {
        il.append(factory.createInvoke("org.apertium.transfer.TransferWord", "stripBrackets", STRING, new Type[]{STRING}, INVOKESTATIC));
      }

      il.append(factory.createInvoke("org.apertium.transfer.TransferWord", "copycase", STRING, new Type[]{STRING, STRING}, INVOKESTATIC));
      il.append(factory.createInvoke(WORD.toString(), side + "Set" + queue, VOID, new Type[]{APERTIUM_RE, STRING}, INVOKEVIRTUAL));
    } else {
      throwParseError(n);
    }
  }

  private void processCallMacro(Element instr) {
    currentNode = instr;
    String n = instr.getAttribute("n");
    if (!macroList.containsKey(n)) {
      // this macro doesent exists!
      printErrorMessage("WARNING: Macro " + n + " is not defined. Ignoring call. Defined macros are: " + macroList.keySet());
      return;
    }
    il.append(createThis());
    il.append(createLoad(APPENDABLE, 1));
    int macronpar = macroList.get(n);
    Type args[] = new Type[macronpar != 0 ? macronpar * 2 : 1];
    args[0] = APPENDABLE;
    int npar = 0;
    for (Element c : listChildren(instr)) {
      if (npar >= macronpar) {
        printErrorMessage("WARNING: Macro " + n + " is invoked with too many parameters. Ignoring: " + c);
        break;
      }
      int pos = Integer.parseInt(c.getAttribute("pos"));
      if (npar > 0) {
        if (pos > 1)
          blank(pos - 1);
        else
          il.append(factory.createConstant(" "));
        args[npar * 2] = STRING;
      }
      word(pos);
      args[npar * 2 + 1] = this.parseMode == ParseMode.TRANSFER ? TRANSFER_WORD : INTERCHUNK_WORD;
      npar++;
    }

    while (npar < macronpar) {
      printErrorMessage("WARNING: Macro " + n + " is invoked with too few parameters. Adding blank parameters ");
      if (npar > 0) {
        il.append(factory.createConstant(""));
        args[npar * 2] = STRING;
      }
      if (this.parseMode == ParseMode.TRANSFER) {
        il.append(factory.createNew("org.apertium.transfer.TransferWord"));
        il.append(DUP);
        il.append(factory.createConstant(""));
        il.append(DUP);
        il.append(factory.createConstant(0));
        il.append(factory.createInvoke("org.apertium.transfer.TransferWord", "<init>", VOID, new Type[]{STRING, STRING, INT}, INVOKESPECIAL));
        args[npar * 2 + 1] = TRANSFER_WORD;
      } else {
        il.append(factory.createNew("org.apertium.interchunk.InterchunkWord"));
        il.append(DUP);
        il.append(factory.createConstant(""));
        il.append(factory.createInvoke("org.apertium.interchunk.InterchunkWord", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
        args[npar * 2 + 1] = INTERCHUNK_WORD;
      }
      npar++;
    }

    il.append(factory.createInvoke(fullClassName, "macro_" + javaIdentifier(n), VOID, args, INVOKESPECIAL));
  }

  private void processChoose(Element e) {
    currentNode = e;
    LinkedList<BranchHandle> branchHandles = null;
    LinkedList<BranchHandle> gotoBranchHandles = new LinkedList<BranchHandle>();
    for (Element whenC : listChildren(e)) {
      if (branchHandles != null) { //Every iteration except the first one
        gotoBranchHandles.push(il.append(new GOTO(null)));
        if (!branchHandles.isEmpty()) {
          InstructionHandle nop = il.append(NOP);
          for (BranchHandle bh : branchHandles)
            bh.setTarget(nop);
        }
      }
      String n = whenC.getTagName();
      Element c0 = getFirstChildElement(whenC);
      if (n.equals("when")) {
        branchHandles = processLogical(getFirstChildElement(c0));
        c0 = findElementSibling(c0.getNextSibling());
      } else {
        branchHandles = new LinkedList<BranchHandle>();
        assert (n.equals("otherwise"));
      }
      while (c0 != null) {
        processInstruction(c0);
        c0 = findElementSibling(c0.getNextSibling());
      }
    }
    InstructionHandle nop = il.append(NOP);
    for (BranchHandle bh : branchHandles)
      bh.setTarget(nop);
    for (BranchHandle bh : gotoBranchHandles)
      bh.setTarget(nop);
  }

  private LinkedList<BranchHandle> processLogical(Element e) {
    return processLogical(e, false);
  }

  private LinkedList<BranchHandle> processLogical(Element e, boolean branchIfTrue) {
    currentNode = e;
    String n = e.getTagName();
    if (n.equals("equal")) {
      processEqual(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("begins-with")) {
      processBeginsWith(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("begins-with-list")) {
      processBeginsWithList(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("ends-with")) {
      processEndsWith(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("ends-with-list")) {
      processEndsWithList(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("contains-substring")) {
      processContainsSubstring(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("in")) {
      processIn(e);
      LinkedList<BranchHandle> result = new LinkedList<BranchHandle>();
      result.push(il.append(branchIfTrue ? new IFNE(null) : new IFEQ(null)));
      return result;
    } else if (n.equals("or") && !branchIfTrue || n.equals("and") && branchIfTrue) {
      LinkedList<BranchHandle> branchHandles = new LinkedList<BranchHandle>();
      Element next = getFirstChildElement(e);
      while (findElementSibling(next.getNextSibling()) != null) {
        branchHandles.addAll(processLogical(next, !branchIfTrue));
        next = findElementSibling(next.getNextSibling());
      }
      LinkedList<BranchHandle> result = processLogical(next, branchIfTrue);
      InstructionHandle nop = il.append(NOP);
      for (BranchHandle bh : branchHandles)
        bh.setTarget(nop);
      return result;
    } else if (n.equals("and") && !branchIfTrue || n.equals("or") && branchIfTrue) {
      LinkedList<BranchHandle> branchHandles = new LinkedList<BranchHandle>();
      Element next = getFirstChildElement(e);
      while (next != null) {
        branchHandles.addAll(processLogical(next, branchIfTrue));
        next = findElementSibling(next.getNextSibling());
      }
      return branchHandles;
    } else if (n.equals("not")) {
      return processLogical(getFirstChildElement(e), !branchIfTrue);
    }
    printErrorMessage("SORRY: not supported yet: processLogical(c0 = " + e);
    return new LinkedList<BranchHandle>();
  }

  private void processEqual(Element e) {
    currentNode = e;
    Element first = findElementSibling(e.getFirstChild());
    Element second = findElementSibling(first.getNextSibling());
    evalString(first);
    evalString(second);
    boolean caseless = "yes".equals(e.getAttribute("caseless"));
    il.append(factory.createInvoke("java.lang.String", caseless ? "equalsIgnoreCase" : "equals", BOOLEAN, new Type[]{caseless ? STRING : OBJECT}, INVOKEVIRTUAL));
  }

  private void processBeginsWith(Element e) {
    currentNode = e;
    Element first = findElementSibling(e.getFirstChild());
    Element second = findElementSibling(first.getNextSibling());
    boolean caseless = "yes".equals(e.getAttribute("caseless"));
    evalString(first);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    evalString(second);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    il.append(factory.createInvoke("java.lang.String", "startsWith", BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void processEndsWith(Element e) {
    currentNode = e;
    Element first = findElementSibling(e.getFirstChild());
    Element second = findElementSibling(first.getNextSibling());
    boolean caseless = "yes".equals(e.getAttribute("caseless"));
    evalString(first);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    evalString(second);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    il.append(factory.createInvoke("java.lang.String", "endsWith", BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void processBeginsWithList(Element e) {
    currentNode = e;
    Element first = getFirstChildElement(e);
    Element second = findElementSibling(first.getNextSibling());
    String listName = list(second.getAttribute("n"));
    il.append(createThis());
    il.append(factory.createGetField(fullClassName, listName, WORD_LIST));
    evalString(first);
    String method = e.getAttribute("caseless").equals("yes") ? "containsIgnoreCaseBeginningWith" : "containsBeginningWith";
    il.append(factory.createInvoke("org.apertium.transfer.WordList", method, BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void processEndsWithList(Element e) {
    currentNode = e;
    Element first = getFirstChildElement(e);
    Element second = findElementSibling(first.getNextSibling());
    String listName = list(second.getAttribute("n"));
    il.append(createThis());
    il.append(factory.createGetField(fullClassName, listName, WORD_LIST));
    evalString(first);
    String method = e.getAttribute("caseless").equals("yes") ? "containsIgnoreCaseEndingWith" : "containsEndingWith";
    il.append(factory.createInvoke("org.apertium.transfer.WordList", method, BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void processContainsSubstring(Element e) {
    currentNode = e;
    Element first = findElementSibling(e.getFirstChild());
    Element second = findElementSibling(first.getNextSibling());
    boolean caseless = "yes".equals(e.getAttribute("caseless"));
    evalString(first);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    evalString(second);
    if (caseless)
      il.append(factory.createInvoke("java.lang.String", "toLowerCase", STRING, NO_ARGS, INVOKEVIRTUAL));
    il.append(factory.createInvoke("java.lang.String", "contains", BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void processIn(Element e) {
    currentNode = e;
    Element first = getFirstChildElement(e);
    Element second = findElementSibling(first.getNextSibling());
    String listName = list(second.getAttribute("n"));
    il.append(createThis());
    il.append(factory.createGetField(fullClassName, listName, WORD_LIST));
    evalString(first);
    String method = e.getAttribute("caseless").equals("yes") ? "containsIgnoreCase" : "contains";
    il.append(factory.createInvoke("org.apertium.transfer.WordList", method, BOOLEAN, new Type[]{STRING}, INVOKEVIRTUAL));
  }

  private void throwParseError(String n) {
    throw new UnsupportedOperationException("Not yet implemented:" + n + getPathAsString(currentNode));
  }
  private boolean error_UNKNOWN_VAR = false;

  private String var(String name) {
    if (varList.contains(name)) {
      return "var_" + javaIdentifier(name);
    }
    printErrorMessage("WARNING variable " + name + " doesent exist. Valid variables are: " + varList
        + "\nReplacing with error_UNKNOWN_VAR");
    error_UNKNOWN_VAR = true;
    return "error_UNKNOWN_VAR";
  }
  private boolean error_UNKNOWN_ATTR = false;

  private String attr(String name) {
    if (attrList.contains(name)) {
      return "attr_" + javaIdentifier(name);
    }
    printErrorMessage("WARNING: Attribute " + name + " is not defined. Valid attributes are: " + attrList
        + "\nReplacing with error_UNKNOWN_ATTR");
    error_UNKNOWN_ATTR = true;
    return "error_UNKNOWN_ATTR";
  }
  private boolean error_UNKNOWN_LIST = false;

  private String list(String name) {
    if (listList.contains(name)) {
      return "list_" + javaIdentifier(name);
    }
    printErrorMessage("WARNING: List " + name + " is not defined. Valid lists are: " + listList
        + "\nReplacing with error_UNKNOWN_LIST");
    error_UNKNOWN_LIST = true;
    return "error_UNKNOWN_LIST";
  }
  /**
   * // in postchunk there is no certain fixed number of words when a rule is invoked
   * // therefore word and blank parameters are implemented as an array
   * // however, macros are the same, so we have to know if we are in a macro or not
   */
  private boolean inMacro = false;

  private void word(int pos) {
    if (parseMode == ParseMode.POSTCHUNK && !inMacro) {
      // in postchunk there is no certain fixed number of words in the rules.
      // therefore its implemented as an array
      // word[0] refers to the chunk lemma and tags
      il.append(createLoad(INTERCHUNK_WORD_ARRAY, 2));
      il.append(factory.createConstant(pos));
      il.append(createArrayLoad(INTERCHUNK_WORD));
    } else if (pos <= currentNumberOfWordInParameterList) {
      il.append(createLoad(this.parseMode == ParseMode.TRANSFER ? TRANSFER_WORD : INTERCHUNK_WORD, pos * 2));
    } else {
      printErrorMessage("WARNING clip pos=" + pos + " is out of range. Replacing with an empty placeholder.");
      if (this.parseMode == ParseMode.TRANSFER) {
        il.append(factory.createNew("org.apertium.transfer.TransferWord"));
        il.append(DUP);
        il.append(factory.createConstant(""));
        il.append(factory.createConstant(""));
        il.append(factory.createConstant(0));
        il.append(factory.createInvoke("org.apertium.transfer.TransferWord", "<init>", VOID, new Type[]{STRING, STRING, INT}, INVOKESPECIAL));
      } else {
        il.append(factory.createNew("org.apertium.interchunk.InterchunkWord"));
        il.append(DUP);
        il.append(factory.createConstant(""));
        il.append(factory.createInvoke("org.apertium.interchunk.InterchunkWord", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
      }
    }
  }

  private void word(String pos) {
    word(Integer.parseInt(pos.trim()));
  }

  private void blank(int pos) {
    if (parseMode == ParseMode.POSTCHUNK && !inMacro) {
      // in postchunk there is no certain fixed number of words in the rules.
      // therefore its implemented as an array
      // TODO: check if index should be shifted one time  (word[0] refers to the chunk lemma and tags)
      il.append(createLoad(STRING_ARRAY, 3));
      il.append(factory.createConstant(pos));
      il.append(createArrayLoad(STRING));
    } else if (pos < currentNumberOfWordInParameterList) {
      il.append(createLoad(STRING, pos * 2 + 1));
    } else {
      printErrorMessage("WARNING blank pos=" + pos + " is out of range. Replacing with a zero-space blank.");
      il.append(factory.createConstant(""));
    }
  }

  private void blank(String pos) {
    blank(Integer.parseInt(pos));
  }

  /**
   * @param txFilename the address of the XML file to be read
   */
  public TransferBytecode(String txFilename) throws IOException, ParserConfigurationException, SAXException {
    /* Don't need to switch this new File() call with an IOUtils version, because this
     * isn't actually opening a file. The purpose of this new File() is to easily split
     * the filename name off from the rest of the path.
     */
    className = javaIdentifier(new File(txFilename).getName());
    fullClassName = "transfer_classes." + className;
    commentHandler = new StringWriter();

    cg = new ClassGen(fullClassName, "org.apertium.transfer.generated.GeneratedTransferBase", "<generated>", ACC_PUBLIC | ACC_SUPER, null);
    cp = cg.getConstantPool();
    il = new InstructionList();
    factory = new InstructionFactory(cg);

    try {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(openFile(txFilename));
      Element root = doc.getDocumentElement();
      String rootTagName = root.getTagName();
      if (rootTagName.equals("transfer")) {
        parseMode = ParseMode.TRANSFER;
        WORD = TRANSFER_WORD;
      } else if (rootTagName.equals("interchunk")) {
        parseMode = ParseMode.INTERCHUNK;
        WORD = INTERCHUNK_WORD;
      } else if (rootTagName.equals("postchunk")) {
        parseMode = ParseMode.POSTCHUNK;
        WORD = INTERCHUNK_WORD;
      } else {
        throw new IllegalArgumentException("illegal rootTagName: " + rootTagName);
      }

      //MethodGen for isOutputChunked
      mg = new MethodGen(ACC_PUBLIC, BOOLEAN, NO_ARGS, null, "isOutputChunked", fullClassName, il, cp);
      il.append(root.getAttribute("default").equals("chunk") ? ICONST_1 : ICONST_0);
      il.append(IRETURN);
      mg.setMaxStack();
      mg.setMaxLocals();
      cg.addMethod(mg.getMethod());
      il.dispose();

      //MethodGen for <init>
      mg = new MethodGen(ACC_PUBLIC, VOID, NO_ARGS, null, "<init>", fullClassName, il, cp);
      il.append(createThis());
      il.append(factory.createInvoke("org.apertium.transfer.generated.GeneratedTransferBase", "<init>", VOID, NO_ARGS, INVOKESPECIAL));
      for (Element c0 : getChildsChildrenElements(root, "section-def-attrs")) {
        String n = c0.getAttribute("n");
        ArrayList<String> items = new ArrayList<String>();
        for (Element c1 : listChildren(c0))
          items.add(c1.getAttribute("tags"));

        /* FIX:
         * java match of (<prn>|<prn><ref>|<prn><itg>|<prn><tn>) on ^what<prn><itg><sp> is '<prn>'
         * pcre match of (<prn>|<prn><ref>|<prn><itg>|<prn><tn>) on ^what<prn><itg><sp> is '<prn><itg>'
         * therefore I reorder so the longest are first.
         */
        Collections.sort(items, new Comparator<String>() {
          @Override
          public int compare(String o1, String o2) {
            return o2.length() - o1.length();
          }
        });

        attrList.add(n);

        fg = new FieldGen(0, APERTIUM_RE, "attr_" + javaIdentifier(n), cp);
        cg.addField(fg.getField());

        il.append(createThis());
        il.append(factory.createNew("org.apertium.transfer.ApertiumRE"));
        il.append(DUP);
        il.append(factory.createConstant(attrItemRegexp(items)));
        il.append(factory.createInvoke("org.apertium.transfer.ApertiumRE", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
        il.append(factory.createPutField(fullClassName, "attr_" + javaIdentifier(n), APERTIUM_RE));
      }

      /*
       * from transfer_data.cc:
       * // adding fixed attr_items
       * attr_items[L"lem"] = L"(([^<]|\"\\<\")+)";
       * attr_items[L"lemq"] = L"\\#[- _][^<]+";
       * attr_items[L"lemh"] = L"(([^<#]|\"\\<\"|\"\\#\")+)";
       * attr_items[L"whole"] = L"(.+)";
       * attr_items[L"tags"] = L"((<[^>]+>)+)";
       * attr_items[L"chname"] = L"({([^/]+)\\/)"; // includes delimiters { and / !!!
       * attr_items[L"chcontent"] = L"(\\{.+)";
       * attr_items[L"content"] = L"(\\{.+)";
       */

      String[][] fixed_attributes = {
        {"lem", "(([^<]|\"\\<\")+)"},
        {"lemq", "\\#[- _][^<]+"},
        {"lemh", "(([^<#]|\"\\<\"|\"\\#\")+)"},
        {"whole", "(.+)"},
        {"tags", "((<[^>]+>)+)"},
        {"chname", "(\\{([^/]+)\\/)"}, // includes delimiters { and / !!!
        {"chcontent", "(\\{.+)"},
        {"content", "(\\{.+)"}, // "\\{(.+)\\}" } would be correct, but wont work as InterchunkWord.chunkPart()
      // requires the match to have the same length as the matched string
      };

      for (String[] nameval : fixed_attributes) {
        if (attrList.add(nameval[0])) {
          fg = new FieldGen(0, APERTIUM_RE, "attr_" + nameval[0], cp);
          cg.addField(fg.getField());

          il.append(createThis());
          il.append(factory.createNew("org.apertium.transfer.ApertiumRE"));
          il.append(DUP);
          il.append(factory.createConstant(nameval[1]));
          il.append(factory.createInvoke("org.apertium.transfer.ApertiumRE", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
          il.append(factory.createPutField(fullClassName, "attr_" + nameval[0], APERTIUM_RE));
        } else {
          printErrorMessage("WARNING: Don't define attribute " + nameval[0] + ", it should keep its predefined value: " + nameval[1]);
        }
      }

      for (Element c0 : getChildsChildrenElements(root, "section-def-vars")) {
        String n = c0.getAttribute("n");
        varList.add(n);
        // fix e.g. <def-var n="nombre" v="&amp;lt;sg&amp;gt;"/> that gives
        // String var_nombre = "&lt;sg&gt;";
        String v = c0.getAttribute("v").replace("&lt;", "<").replace("&gt;", ">");

        fg = new FieldGen(0, STRING, "var_" + javaIdentifier(n), cp);
        cg.addField(fg.getField());

        il.append(createThis());
        il.append(factory.createConstant(v));
        il.append(factory.createPutField(fullClassName, "var_" + javaIdentifier(n), STRING));
      }

      if (parseMode == ParseMode.POSTCHUNK) {
        fg = new FieldGen(0, STRING, "lu_count", cp);
        cg.addField(fg.getField());
      }

      for (Element c0 : getChildsChildrenElements(root, "section-def-lists")) {
        String n = c0.getAttribute("n");
        ArrayList<String> items = new ArrayList<String>();
        for (Element c1 : listChildren(c0))
          items.add(c1.getAttribute("v"));
        listList.add(n);

        fg = new FieldGen(0, WORD_LIST, "list_" + javaIdentifier(n), cp);
        cg.addField(fg.getField());

        il.append(createThis());
        il.append(factory.createNew("org.apertium.transfer.WordList"));
        il.append(DUP);
        il.append(factory.createConstant(items.size()));
        il.append(factory.createNewArray(STRING, (short) 1));

        for (int i = 0; i < items.size(); i++) {
          il.append(DUP);
          il.append(factory.createConstant(i));
          il.append(factory.createConstant(items.get(i)));
          il.append(createArrayStore(STRING));
        }
        il.append(factory.createInvoke("org.apertium.transfer.WordList", "<init>", VOID, new Type[]{new ArrayType(STRING, 1)}, INVOKESPECIAL));
        il.append(factory.createPutField(fullClassName, "list_" + javaIdentifier(n), WORD_LIST));
      }

      il.append(RETURN);
      mg.setMaxStack();
      mg.setMaxLocals();
      cg.addMethod(mg.getMethod());
      il.dispose();


      inMacro = true;
      for (Element c0 : getChildsChildrenElements(root, "section-def-macros")) {
        currentNode = c0;
        String name = c0.getAttribute("n");
        String npars = c0.getAttribute("npar");
        int npar = npars.length() > 0 ? Integer.parseInt(npars) : 0;
        currentNumberOfWordInParameterList = npar;
        macroList.put(name, npar);

        ArrayList<Type> args = new ArrayList<Type>();
        args.add(APPENDABLE);

        if (this.parseMode == ParseMode.TRANSFER) {
          for (int i = 1; i <= npar; i++) {
            if (i != 1)
              args.add(STRING);
            args.add(TRANSFER_WORD);
          }
        } else {
          for (int i = 1; i <= npar; i++) {
            if (i != 1)
              args.add(STRING);
            args.add(INTERCHUNK_WORD);
          }
        }
        String methodName = "macro_" + javaIdentifier(name);

        mg = new MethodGen(ACC_PRIVATE, VOID, args.toArray(new Type[args.size()]), null, methodName, fullClassName, il, cp);
        mg.addException("java.io.IOException");

        il.append(createThis());
        il.append(factory.createGetField(fullClassName, "debug", BOOLEAN));
        BranchHandle ifeq = il.append(new IFEQ(null));
        il.append(createThis());
        il.append(factory.createConstant(methodName));
        il.append(factory.createConstant(currentNumberOfWordInParameterList * 2 - 1));
        il.append(factory.createNewArray(OBJECT, (short) 1));
        for (int i = 0; i < currentNumberOfWordInParameterList * 2 - 1; i++) {
          il.append(DUP);
          il.append(factory.createConstant(i));
          il.append(createLoad(OBJECT, i + 2));
          il.append(createArrayStore(OBJECT));
        }
        il.append(factory.createInvoke(fullClassName, "logCall", VOID, new Type[]{STRING, new ArrayType(OBJECT, 1)}, INVOKEVIRTUAL));
        ifeq.setTarget(il.append(NOP));

        writeMethodBody(c0);

        il.append(RETURN);
        mg.setMaxStack();
        mg.setMaxLocals();
        cg.addMethod(mg.getMethod());
        il.dispose();
      }


      inMacro = false;
      int ruleNo = 0;
      for (Element c0 : getChildsChildrenElements(root, "section-rules")) {
        currentNode = c0;
        ArrayList<String> patternItems = new ArrayList<String>();

        String methodName = "rule" + (ruleNo++);
        for (Element c1 : getChildsChildrenElements(c0, "pattern")) {
          String n = c1.getAttribute("n");
          methodName += "__" + javaIdentifier(n);
          patternItems.add(n);
        }
        currentNumberOfWordInParameterList = patternItems.size();

        ArrayList<Type> args = new ArrayList<Type>();
        args.add(APPENDABLE);

        if (this.parseMode == ParseMode.TRANSFER) {
          for (int i = 1; i <= currentNumberOfWordInParameterList; i++) {
            if (i != 1)
              args.add(STRING);
            args.add(TRANSFER_WORD);
          }
        } else if (this.parseMode == ParseMode.INTERCHUNK) {
          for (int i = 1; i <= currentNumberOfWordInParameterList; i++) {
            if (i != 1)
              args.add(STRING);
            args.add(INTERCHUNK_WORD);
          }
        } else {
          assert (parseMode == ParseMode.POSTCHUNK);
          // in postchunk there is no certain fixed number of words when a rule is invoked
          // therefore its implemented as an array
          // words[0] refers to the chunk lemma (and tags)
          args.add(INTERCHUNK_WORD_ARRAY);
          args.add(STRING_ARRAY);
        }

        mg = new MethodGen(ACC_PUBLIC, VOID, args.toArray(new Type[args.size()]), null, methodName, fullClassName, il, cp);
        mg.addException("java.io.IOException");

        il.append(createThis());
        il.append(factory.createGetField(fullClassName, "debug", BOOLEAN));
        BranchHandle ifeq = il.append(new IFEQ(null));
        il.append(createThis());
        il.append(factory.createConstant(methodName));
        il.append(factory.createConstant(currentNumberOfWordInParameterList * 2 - 1));
        il.append(factory.createNewArray(OBJECT, (short) 1));
        for (int i = 0; i < currentNumberOfWordInParameterList * 2 - 1; i++) {
          il.append(DUP);
          il.append(factory.createConstant(i));
          il.append(createLoad(OBJECT, i + 2));
          il.append(createArrayStore(OBJECT));
        }
        il.append(factory.createInvoke(fullClassName, "logCall", VOID, new Type[]{STRING, new ArrayType(OBJECT, 1)}, INVOKEVIRTUAL));
        ifeq.setTarget(il.append(NOP));

        if (parseMode == ParseMode.POSTCHUNK) {
          il.append(createThis());
          il.append(createLoad(INTERCHUNK_WORD_ARRAY, 2));
          il.append(ARRAYLENGTH);
          il.append(factory.createConstant(1));
          il.append(ISUB);
          il.append(factory.createInvoke("java.lang.Integer", "toString", STRING, new Type[]{INT}, INVOKESTATIC));
          il.append(factory.createPutField(fullClassName, "lu_count", STRING));
        }

        writeMethodBody((Element) getElement(c0, "action"));

        il.append(RETURN);
        mg.setMaxStack();
        mg.setMaxLocals();
        cg.addMethod(mg.getMethod());
        il.dispose();
      }

      // Error handling
      if (error_UNKNOWN_ATTR) {
        fg = new FieldGen(0, APERTIUM_RE, "error_UNKNOWN_ATTR", cp);
        cg.addField(fg.getField());
        mg = new MethodGen(cg.getMethodAt(1), fullClassName, cp);
        il = mg.getInstructionList();
        try {
          il.delete(il.getEnd());
        } catch (TargetLostException ex) {
        }
        il.append(createThis());
        il.append(factory.createNew("org.apertium.transfer.ApertiumRE"));
        il.append(DUP);
        il.append(factory.createConstant("error_UNKNOWN_ATTR"));
        il.append(factory.createInvoke("org.apertium.transfer.ApertiumRE", "<init>", VOID, new Type[]{STRING}, INVOKESPECIAL));
        il.append(factory.createPutField(fullClassName, "error_UNKNOWN_ATTR", APERTIUM_RE));
        il.append(RETURN);
        mg.setMaxStack();
        mg.setMaxLocals();
        cg.setMethodAt(mg.getMethod(), 1);
        il.dispose();
      }

      if (error_UNKNOWN_VAR) {
        fg = new FieldGen(0, STRING, "error_UNKNOWN_VAR", cp);
        cg.addField(fg.getField());
        mg = new MethodGen(cg.getMethodAt(1), fullClassName, cp);
        il = mg.getInstructionList();
        try {
          il.delete(il.getEnd());
        } catch (TargetLostException ex) {
        }
        il.append(createThis());
        il.append(factory.createConstant(""));
        il.append(factory.createPutField(fullClassName, "error_UNKNOWN_VAR", STRING));
        il.append(RETURN);
        mg.setMaxStack();
        mg.setMaxLocals();
        cg.setMethodAt(mg.getMethod(), 1);
        il.dispose();
      }

      if (error_UNKNOWN_LIST) {
        fg = new FieldGen(0, WORD_LIST, "error_UNKNOWN_LIST", cp);
        cg.addField(fg.getField());
        mg = new MethodGen(cg.getMethodAt(1), fullClassName, cp);
        il = mg.getInstructionList();
        try {
          il.delete(il.getEnd());
        } catch (TargetLostException ex) {
        }
        il.append(createThis());
        il.append(factory.createNew("org.apertium.transfer.WordList"));
        il.append(DUP);
        il.append(factory.createConstant(0));
        il.append(factory.createNewArray(STRING, (short) 1));
        il.append(factory.createInvoke("org.apertium.transfer.WordList", "<init>", VOID, new Type[]{new ArrayType(STRING, 1)}, INVOKESPECIAL));
        il.append(factory.createPutField(fullClassName, "error_UNKNOWN_LIST", WORD_LIST));
        il.append(RETURN);
        mg.setMaxStack();
        mg.setMaxLocals();
        cg.setMethodAt(mg.getMethod(), 1);
        il.dispose();
      }

    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error: Cannot open '" + txFilename + "'.");
    }
  }

  private class BytecodeLoader extends ClassLoader {
    public Class getClassFromBytes(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }

  public Class getJavaClass() {
    return new BytecodeLoader().getClassFromBytes(getBytes());
  }

  public byte[] getBytes() {
    return cg.getJavaClass().getBytes();
  }

  public void dump(String filename) throws IOException {
    cg.getJavaClass().dump(filename);
  }

  private static void showHelp(String name) {
    System.out.print(name + CommandLineInterface.PACKAGE_VERSION + ": \n"
        + "USAGE: " + name + " trules  trules-class\n"
        + "  trules     transfer rule (.t1x) source file\n"
        + "  trules-class  Java bytecode compiled transfer rules (.class) output file\n"
        + "");
  }

  public static void main(String[] argv) throws Exception {
    if (argv.length != 2)
      showHelp("apertium-preprocess-transfer-bytecode-j");
    else
      new TransferBytecode(argv[0]).dump(argv[1]);
  }
}
