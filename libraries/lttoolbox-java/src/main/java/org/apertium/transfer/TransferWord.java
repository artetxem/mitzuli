/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer;

/**
 *
 * @author Jacob Nordfalk
 */
public class TransferWord {
  public String source;
  public String target;
  int queue_length;

  @Override
  public String toString() {
    return source + "->" + target + "/" + queue_length;
  }

  public TransferWord(String src, String tgt, int queue) {
    source = src;
    target = tgt;
    queue_length = queue;
  }

  public String sl(ApertiumRE part) {
    return part.match(source);
  }

  /** this is not used in any exitsting language pair (jan 2010) - might delete */
  public String slNoQueue(ApertiumRE part) {
    return part.match(source.substring(0, source.length() - queue_length));
  }

  public String tl(ApertiumRE part) {
    return part.match(target);
  }

  /** this is not used in any exitsting language pair (jan 2010) - might delete */
  public String tlNoQueue(ApertiumRE part) {
    return part.match(target.substring(0, target.length() - queue_length));
  }

  public void slSet(ApertiumRE part, String value) {
    source = part.replace(source, value);
  }

  /** this is not used in any exitsting language pair (jan 2010) - might delete */
  public void slSetNoQueue(ApertiumRE part, String value) {
    String mystring = source.substring(0, source.length() - queue_length);
    mystring = part.replace(mystring, value);
    source = mystring + source.substring(source.length() - queue_length);
  }

  public void tlSet(ApertiumRE part, String value) {
    target = part.replace(target, value);
  }

  /** this is not used in any exitsting language pair (jan 2010) - might delete */
  public void tlSetNoQueue(ApertiumRE part, String value) {
    String mystring = target.substring(0, target.length() - queue_length);
    mystring = part.replace(mystring, value);
    target = mystring + target.substring(target.length() - queue_length);
  }

  /**
   * The C code correspodants - not used anymore
   */
  public String source(ApertiumRE part, boolean with_queue) {
    if (with_queue) {
      return sl(part);
    } else {
      return slNoQueue(part);
    }
  }

  public String target(ApertiumRE part, boolean with_queue) {
    if (with_queue) {
      return tl(part);
    } else {
      return tlNoQueue(part);
    }
  }

  public void setSource(ApertiumRE part, String value, boolean with_queue) {
    if (with_queue) {
      slSet(part, value);
    } else {
      slSetNoQueue(part, value);
    }
  }

  public void setTarget(ApertiumRE part, String value, boolean with_queue) {
    if (with_queue) {
      tlSet(part, value);
    } else {
      tlSetNoQueue(part, value);
    }
  }

  public static String copycase(String s_word, String t_word) {
    // These 2 checks are needed to support buggy tranfer files
    if (s_word.length() == 0)
      return t_word;
    if (t_word.length() == 0)
      return t_word;

    String result;
    boolean firstupper = Character.isUpperCase(s_word.charAt(0));
    boolean uppercase = firstupper && Character.isUpperCase(s_word.charAt(s_word.length() - 1));
    boolean sizeone = s_word.length() == 1;

    if (!uppercase || (sizeone && uppercase)) {
      result = Character.toLowerCase(t_word.charAt(0)) + t_word.substring(1);
      //result = StringUtils::tolower(t_word);
    } else {
      result = t_word.toUpperCase();
    }

    if (firstupper) {
      result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }
    return result;
  }

  public static String caseOf(String s_word) {
    if (s_word.length() == 0)
      return "aa";

    if (s_word.length() > 1) {
      if (!Character.isUpperCase(s_word.charAt(0))) {
        return "aa";
      } else if (!Character.isUpperCase(s_word.charAt(s_word.length() - 1))) {
        return "Aa";
      }
      return "AA";
    } else {
      if (!Character.isUpperCase(s_word.charAt(0))) {
        return "aa";
      }
      return "Aa";
    }
  }

  // fix for apertium/interchunk.cc evalString(): if(ti.getContent() == "content") then we need to strip the brackets
  // it is put here as copycase() and other utility methods are already here too
  public static String stripBrackets(String chunk) {
    // string wf = word[ti.getPos()]->chunkPart(attr_items[ti.getContent()]);
    // return wf.substr(1, wf.length()-2); // trim away the { and }
    return chunk.substring(1, chunk.length() - 1);
  }
}
