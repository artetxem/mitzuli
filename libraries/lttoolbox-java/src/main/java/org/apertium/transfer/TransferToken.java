/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer;

/**
 *
 * @author Jacob Nordfalk
 */
public class TransferToken {
  public enum TransferTokenType {
    tt_eof,
    tt_word,
    tt_blank
  };

  //The way this is used in the existing code, these should be public.
  public TransferTokenType type;
  public String content;

  public TransferToken(String content, TransferTokenType type) {
    this.content = content;
    this.type = type;
  }

  public String toString() {
    return content;
  }
}
