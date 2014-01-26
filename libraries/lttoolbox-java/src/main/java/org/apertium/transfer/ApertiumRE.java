/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Jacob Nordfalk
 */
public class ApertiumRE {
  Pattern re;

  // Consider using http://jakarta.apache.org/regexp/apidocs/org/apache/regexp/RE.html
  public ApertiumRE(String regex) {
    re = Pattern.compile(regex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE); // |Pattern.UNICODE_CASE
    // PCRE_DOTALL|PCRE_CASELESS|PCRE_EXTENDED|PCRE_UTF8,
  }

  public String toString() {
    return re.toString();
  }
  public static final String EMPTY_STRING = "";

  public String match(String source) {
    Matcher m = re.matcher(source);
    if (!m.find())
      return ""; // Note: This string is used for checking for object equality in transfer

    //System.err.println("m = " + m);
    //System.err.println("m = " + m.groupCount());

    return m.group();
  }

  public String replace(String source, String value) {
    Matcher m = re.matcher(source);
    if (!m.find())
      return source;
    return source.substring(0, m.start()) + value + source.substring(m.end());
  }
}
