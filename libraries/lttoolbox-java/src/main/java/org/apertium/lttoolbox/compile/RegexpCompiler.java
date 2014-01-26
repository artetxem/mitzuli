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
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiler that builds a transducer to identify regular expressions.
 * This compiler is a recursive descendent parser (RDP).
 *
 * @author Raah
 */
public class RegexpCompiler {
  private static final int FIN_FICHERO = -1;
  /**
   * Last token
   */
  int token;
  /**
   * Input string
   */
  String input;
  /**
   * Alphabet to encode symbols
   */
  private CompileAlphabet alphabet;
  /** this lookup is needed very often and thus cached */
  private int alphabet_cast00;
  /**
   * Transducer to store analysis
   */
  TransducerComp transducer;
  /**
   * Current state
   */
  Integer state;
  /**
   * Current letter
   */
  int letter;
  /**
   * Post-operator: '+', '?', '*'
   */
  String postop;
  /**
   *
   */
  Set<Integer> brackets;

  /**
   * The constructor
   */
  RegexpCompiler() {
    transducer = new TransducerComp();
    brackets = new TreeSet<Integer>();
  }

  /**
   * Detect if t is a reserved token
   *
   * @param t the token to check
   * @return true if the token is reserved
   */
  boolean isReserved(int t) {
    switch (t) {
      case '(':
      case ')':
      case '[':
      case ']':
      case '*':
      case '?':
      case '+':
      case '-':
      case '^':
      case '\\':
      case '|':
      case FIN_FICHERO:
        return true;
      default:
        return false;
    }
  }

  /**
   * Error message function
   */
  void error() {
    throw new RuntimeException("Error parsing regexp");
  }

  /**
   * Error message function
   *
   * @param t the token being consumed
   */
  void errorConsuming(int t) {
    throw new RuntimeException("Error parsing regexp");
  }

  /**
   * Consume the input
   *
   * @param t the input to be consumed
   */
  void consume(int t) {
    if (token == t) {
      input = input.substring(1);
      if (input.equals("")) {
        token = FIN_FICHERO;
      } else {
        token = input.charAt(0);
      }
    } else {
      errorConsuming(t);
    }
  }

  /**
   * Function that parses a regular expression and produces a transducer
   *
   * @param er the regular expression
   */
  void compile(String er) {
    input = er;
    token = (int) (input.charAt(0));
    state = transducer.getInitial();
    S();
    transducer.setFinal(state);
  }

  /**
   * RDP top function
   */
  void S() {
    if (token == '(' || token == '[' || !isReserved(token) || token == '\\') {
      RExpr();
      Cola();
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void RExpr() {
    if (token == '(' || token == '[' || !isReserved(token) || token == '\\') {
      Term();
      RExprp();
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Cola() {
    if (token == FIN_FICHERO || token == ')') {
    } else if (token == '|') {
      Integer e = state;
      state = transducer.getInitial();
      consume('|');
      RExpr();
      Cola();
      state = transducer.insertNewSingleTransduction(alphabet_cast00, state);
      transducer.linkStates(e, state, alphabet_cast00);
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Term() {
    if (!isReserved(token) || token == '\\') {
      TransducerComp t = new TransducerComp();
      t.setEpsilon_Tag(alphabet_cast00);
      Integer e = t.getInitial();
      Letra();
      e = t.insertNewSingleTransduction(alphabet.cast(letter, letter), e);
      t.setFinal(e);
      Postop();
      if (postop.equals("*")) {
        t.zeroOrMore();
      } else if (postop.equals("+")) {
        t.oneOrMore();
      } else if (postop.equals("?")) {
        t.optional();
      }
      postop = "";
      state = transducer.insertTransducer(state, t);
    } else if (token == '(') {
      TransducerComp t = transducer;
      t.setEpsilon_Tag(alphabet_cast00);
      Integer e = state;
      transducer = new TransducerComp();
      state = transducer.getInitial();
      consume('(');
      S();
      consume(')');
      transducer.setFinal(state);
      Postop();
      if (postop.equals("*")) {
        transducer.zeroOrMore();
      } else if (postop.equals("+")) {
        transducer.oneOrMore();
      } else if (postop.equals("?")) {
        transducer.optional();
      }
      postop = "";
      state = t.insertTransducer(e, transducer);
      transducer = t;
    } else if (token == '[') {
      consume('[');
      Esp();
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void RExprp() {
    if (token == '(' || token == '[' || !isReserved(token) || token == '\\') {
      Term();
      RExprp();
    } else if (token == '|' || token == FIN_FICHERO || token == ')') {
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Letra() {
    if (!isReserved(token)) {
      letter = token;
      consume(token);
    } else if (token == '\\') {
      consume('\\');
      letter = token;
      Reservado();
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Postop() {
    if (token == '*') {
      consume('*');
      postop = "*";
    } else if (token == '?') {
      consume('?');
      postop = "?";
    } else if (token == '+') {
      consume('+');
      postop = "+";
    } else if (token == '(' || token == '[' || !isReserved(token)
        || token == '\\' || token == '|' || token == FIN_FICHERO
        || token == ')') {
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Esp() {
    TransducerComp t = new TransducerComp();
    t.setEpsilon_Tag(alphabet_cast00);
    if (!isReserved(token) || token == '\\' || token == ']') {
      Lista();
      consume(']');
      Postop();

      for (Integer it : brackets) {
        Integer mystate = t.getInitial();
        mystate = t.insertNewSingleTransduction(alphabet_cast00, mystate);
        mystate = t.insertNewSingleTransduction(alphabet.cast(it, it), mystate);
        t.setFinal(mystate);
      }
      t.joinFinals();
    } else if (token == '^') {
      consume('^');
      Lista();
      consume(']');
      Postop();

      for (int i = 0; i < 256; i++) {
        if (!brackets.contains(i)) {
          Integer mystate = t.getInitial();
          mystate = t.insertNewSingleTransduction(alphabet_cast00, mystate);
          mystate = t.insertNewSingleTransduction(alphabet.cast(i, i), mystate);
          t.setFinal(mystate);
        }
      }
      t.joinFinals();
    } else {
      error();
    }

    if (postop.equals("+")) {
      t.oneOrMore();
    } else if (postop.equals("*")) {
      t.zeroOrMore();
    } else if (postop.equals("?")) {
      t.optional();
    }
    brackets.clear();
    postop = "";

    state = transducer.insertTransducer(state, t);
  }

  /**
   * RDP function
   */
  void Lista() {
    if (!isReserved(token) || token == '\\') {
      Elem();
      Lista();
    } else if (token == ']') {
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Reservado() {
    if (isReserved(token)) {
      consume(token);
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void Elem() {
    if (!isReserved(token) || token == '\\') {
      Letra();
      int rango1 = letter;
      ColaLetra();
      int rango2 = letter;

      if (rango1 > rango2) {
        error();
      } else {
        for (int i = rango1; i <= rango2; i++) {
          brackets.add(i);
        }
      }
    } else {
      error();
    }
  }

  /**
   * RDP function
   */
  void ColaLetra() {
    if (token == '-') {
      consume('-');
      Letra();
    } else if (!isReserved(token) || token == '\\' || token == ']') {
    } else {
      error();
    }
  }

  /**
   * Set the decoder of symbols
   *
   * @param a the alphabet
   */
  public void setAlphabet(CompileAlphabet a) {
    alphabet = a;
    alphabet_cast00 = alphabet.cast(0, 0);
  }

  /**
   * Get the transducer built
   *
   * @return the transducer
   */
  public TransducerComp getTransducer() {
    return transducer;
  }

  /**
   * Initialize the compiler
   *
   * @param a the alphabet
   */
  void initialize(CompileAlphabet a) {
    setAlphabet(a);

    transducer.clear();
    brackets.clear();
    postop = "";
  }
}
