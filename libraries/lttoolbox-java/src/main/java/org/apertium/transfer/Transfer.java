/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import org.apertium.lttoolbox.Pair;
import org.apertium.lttoolbox.process.FSTProcessor;
import org.apertium.utils.IOUtils;
import org.apertium.utils.Timing;
import static org.apertium.utils.IOUtils.openFileAsByteBuffer;

/**
 * Java port of transfer.
 * <pre>
 * The Java port of transfer differs from the C++ version in some very important ways:
 *
 * I will take apertium-eo-en (which works as 'pure' C++ as well as 'Java verson') as example.
 *
 * And here you just *must* be able to debug the Java code step-by-step. Its mandatory to step-by-step the whole TransferEoEnTest.java test class.
 *
 *
 *
 *
 * In transfer three things are happening.
 * 1) words are input in one language (English) and output in another (Esperanto)
 * 2) some rules are matched (for example adjective_noun)
 * 3) for each rule matched a transfer action is being done
 *
 * 1) is called bilingual transfer. Here you have for each word a SL (source language - English) and a TL (target language - Esperanto) - see TransferWord. C++ and Java are same.
 *
 * 2) the rule MATCHING, is done by the FSTProcessor. This is normally used to match letters in a word, but can also be used to match a sequence of words. C++ and Java are same.
 *
 * 3) the rule EXECUTING is done completely different in C++ and Java! In C++ we interpret XML, in Java we execute bytecode.
 *
 *
 *
 *
 * To Compile a transfer file, e.g. apertium-eo-en.en-eo.t1x, together with the bilingual dictionary, apertium-eo-en.eo-en.dix, type:
 *
 * First validate (optional steps):
 * $ apertium-validate-dictionary apertium-eo-en.eo-en.dix
 * $ apertium-validate-transfer apertium-eo-en.en-eo.t1x
 *
 * C++ and Java:
 * $ lt-comp rl apertium-eo-en.eo-en.dix en-eo.autobil.bin
 * (gives en-eo.autobil.bin - the bilingual dictionary, ie translating words from en to eo)
 * $ apertium-preprocess-transfer apertium-eo-en.en-eo.t1x en-eo.t1x.bin
 * (gives en-eo.t1x.bin - the transfer rule MATCHING/EXECUTING file)
 *
 * C++ only: Slow (interpreted) transfer
 * Here is how to execute in C++ (for 'blue cats')
 * $ echo "^blue<adj>$ ^cat<n><pl>$" | apertium-transfer apertium-eo-en.en-eo.t1x en-eo.t1x.bin en-eo.autobil.bin
 * ^preadv?_adj_nom<SN><pl><nom>{^blua<adj><2><3>$ ^kato<n><pl><3>$}$
 *
 * Here step 3 is done by INTERPRETING the XML in the first file (apertium-eo-en.en-eo.t1x)
 *
 *
 * Only Java:
 * $ apertium-preprocess-transfer-bytecode-j apertium-eo-en.en-eo.t1x en-eo.t1x.class
 * (gives en-eo.t1x.class  - the rule EXECUTING file)
 *
 * Java only: Fast (bytecode) transfer
 * $ echo "^blue<adj>$ ^cat<n><pl>$" | apertium-transfer-j en-eo.t1x.class apertium-eo-en.en-eo.t1x en-eo.t1x.bin en-eo.autobil.bin
 * ^preadv?_adj_nom<SN><pl><nom>{^blua<adj><2><3>$ ^kato<n><pl><3>$}$
 * (must give same output, of course)
 *
 * Here step 3 is done by EXECUTING the bytecode in the first file (apertium-eo-en.en-eo.t1x)
 *
 *
 *
 * So, you see:  en-eo.t1x.bin is for rule matching (much like the other .bin files, they are just for matching words).
 * In the end of en-eo.t1x.bin some stuff is added which makes the C++ version run faster (attr_items, variables, macros, lists). We ignore that as its compiled into the bytecode (see Transfer.java public void readData() line 96-141).
 *
 * In Transfer.java public void transfer() you see the main loop, collecting characters.  If you want to see how the rule match works, set debug breakpoints in ms.step() and look.  ms.classifyFinals() gives the rule index that was matched.
 *
 *
 * And now we get to the essential step 3:
 *
 * In Java code, we use that to look up (in Method[] rule_map) some code to invoke.
 *
 * In C++ code there is a pure XML HELL, jumping around like something jumping very badly (and taking aaages of time).
 *
 *
 * So, how do we set up the array of rule_map Method?
 *
 * Well, during compilation the ParseTransferFile.java takes the XML hell of for example apertium-eo-en.en-eo.t1x, and converts it into Java code like the apertium_eo_en_eo_en_t1x java class (in package org.apertium.transfer.generated), which is loaded during runtime.
 *
 * So the array of rule_map Method is taken by introspection, taking all methods beginning with rule<number>, like rule0__la__num_ord__de__monato, rule1__de_ekde__tempo etc etc and kicks them into the array.
 *
 *
 * Now, the transfer code could need a big cleanup. This is the stuff I experimented most with. Rename stuff in the code, comment it as hell, etc. Please make sure more or less evrything I covered above (and what you self found out) gets in somewhere in the documentation.
 * </pre>
 *
 * @author Jacob Nordfalk
 */
public class Transfer extends AbstractTransfer {
  private FSTProcessor fstp = new FSTProcessor();
  private FSTProcessor extended;
  private boolean isExtended;
  //map<xmlNode *, TransferInstr> evalStringCache;
  private boolean useBilingual = true;
  private boolean preBilingual = false;

  /**
   * Read bilingual dictionary
   *
   * @param bilFstFile file name
   */
  private void readBil(String bilFstFile) throws IOException {
    ByteBuffer is = openFileAsByteBuffer(bilFstFile);
    fstp.load(is, bilFstFile);
    fstp.initBiltrans();
  }

  /**
   * What is 'extended mode' ?? Hints:
   * apertium-transfer -x extended trules preproc biltrans [input [output]]
   * -x bindix extended mode with user dictionary
   *
   * @param fstfile
   */
  private void setExtendedDictionary(String fstfile) throws IOException {
    extended = new FSTProcessor();
    ByteBuffer is = openFileAsByteBuffer(fstfile);
    extended.load(is, fstfile);
    extended.initBiltrans();
    isExtended = true;
  }

  /**
   * Reads data
   *
   * @param classFile the file name of the java bytecode file containing the transfer instructions
   * so, preprocessed by, apertium-preprocess-transfer-bytecode-j (.class)
   * @param datafile same file, preprocessed by, apertium-preprocess-transfer (.bin)
   * @param bilFstFile bilingual FST file - might be null
   * @throws ClassNotFoundException
   * @throws IllegalAccessException
   * @throws InstantiationException
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public void read(Class transferClass, String datafile, String bilFstFile) throws Exception {
    super.read(transferClass, datafile);

    if (bilFstFile != null && bilFstFile.length() > 0) {
      readBil(bilFstFile);
      if (IOUtils.timing != null)
        IOUtils.timing.log("Load bilingual transfer transducer " + bilFstFile);
    }
  }

  //private void readTransfer()  and the following methods should not implemented, as we use bytecode compiled transfer
  TransferToken readToken(Reader in) throws IOException {
    if (!input_buffer.isEmpty()) {
      return input_buffer.next();
    }

    String content = "";
    while (true) {
      int val = in.read();
      if (val == -1 || (val == 0 && internal_null_flush)) {
        return input_buffer.add(new TransferToken(content, TransferToken.TransferTokenType.tt_eof));
      }
      if (val == '\\') {
        content += '\\';
        content += (char) in.read();
      } else if (val == '[') {
        content += '[';
        while (true) {
          int val2 = in.read();
          if (val2 == '\\') {
            content += '\\';
            content += (char) in.read();
          } else if (val2 == ']') {
            content += ']';
            break;
          } else {
            content += (char) val2;
          }
        }
      } else if (val == '$') {
        return input_buffer.add(new TransferToken(content, TransferToken.TransferTokenType.tt_word));
      } else if (val == '^') {
        return input_buffer.add(new TransferToken(content, TransferToken.TransferTokenType.tt_blank));
      } else {
        content += (char) val;
      }
    }
  }

  @Override
  public void process(Reader in, Appendable output) throws Exception {
    if (IOUtils.timing != null)
      IOUtils.timing.log("");
    if (getNullFlush()) {
      process_wrapper_null_flush(in, output);
    }

    output = checkIfOutputMustBeWriterCompatible(output, rule_map);

    Method lastMatchedRule = null;
    ArrayList<String> tmpword = new ArrayList<String>();
    ArrayList<String> tmpblank = new ArrayList<String>();
    ArrayList<String> matchedWords = new ArrayList<String>();
    ArrayList<String> matchedBlanks = new ArrayList<String>();

    int lastPos = 0;
    ms.init(me.getInitial());
    if (DO_TIMING)
      timing = new Timing("Transfer");
    while (true) {
      if (ms.size() == 0) {
        if (lastMatchedRule != null) {
          // there was a rule match
          applyRule(output, lastMatchedRule, matchedWords, matchedBlanks);
          lastMatchedRule = null;
          tmpword.clear();
          tmpblank.clear();
          ms.init(me.getInitial());
          input_buffer.setPos(lastPos);
        } else {
          if (tmpword.size() != 0) {
            // no rule match. then default is to just output the stuff word by word
            Pair<String, Integer> tr;
            if (useBilingual && preBilingual == false) {
              if (isExtended && (tmpword.get(0)).charAt(0) == '*') {
                tr = extended.biltransWithQueue((tmpword.get(0)).substring(1), false);
                if (tr.first.charAt(0) == '@') {
                  tr.first = '*' + tr.first.substring(1);
                } else {
                  tr.first = "%" + tr.first;
                }
              } else {
                if (DO_TIMING)
                  timing.log("transfer");
                tr = fstp.biltransWithQueue(tmpword.get(0), false);
                if (DO_TIMING)
                  timing.log("transfer/fstp.biltransWithQueue ");
              }
            } else if (preBilingual) {
              /* input = ^esperanto/english1/english2/english3$
               * <spectei> we can only have one translation in transfer
               * <spectei> so we want ^esperanto/english1 BREAK
               */
              String[] splits = tmpword.get(0).split("/");
              String sl = splits[0];
              String tl = splits.length > 1 ? splits[1] : "";
              // http://freedict.svn.sourceforge.net/viewvc/apertium/trunk/apertium/apertium/transfer.cc?r1=35560&r2=35639
              // tmpword.set(0, sl);
              tr = new Pair<String, Integer>(tl, 0);
            } else {
              tr = new Pair<String, Integer>(tmpword.get(0), 0);
            }

            if (tr.first.length() != 0) {
              if (!transferObject.isOutputChunked()) {
                output.append('^');
                output.append(tr.first);
                output.append('$');
              } else {
                if (tr.first.charAt(0) == '*') {
                  fputws_unlocked("^unknown<unknown>{^", output);
                } else {
                  fputws_unlocked("^default<default>{^", output);
                }
                fputws_unlocked(tr.first, output);
                fputws_unlocked("$}$", output);
              }
            }
            tmpword.clear();
            input_buffer.setPos(lastPos);
            input_buffer.next();
            lastPos = input_buffer.getPos();
            ms.init(me.getInitial());
          } else if (tmpblank.size() != 0) {
            fputws_unlocked(tmpblank.get(0), output);
            tmpblank.clear();
            lastPos = input_buffer.getPos();
            ms.init(me.getInitial());
          }
        }
      }
      if (DO_TIMING)
        timing.log("transfer");
      int val = ms.classifyFinals();
      if (DO_TIMING)
        timing.log("transfer/ms.classifyFinals");
      if (val != -1) {
        // a rule match was found. This might not be the longest match, though.
        // so, we store the stuff to invoke applyRule() later
        lastMatchedRule = rule_map[(val - 1)];
        lastPos = input_buffer.getPos();

        if (DEBUG)
          System.err.println("lastrule = " + (val - 1) + " " + lastMatchedRule.getName());
        if (DEBUG)
          System.err.println("tmpword = " + tmpword.size() + "  tmpblank = " + tmpblank.size());
        if (DEBUG)
          System.err.println("tmpword = " + tmpword + "  tmpblank = " + tmpblank);
        matchedWords.clear();
        matchedBlanks.clear();
        matchedWords.addAll(tmpword);
        matchedBlanks.addAll(tmpblank);
      }

      if (DO_TIMING)
        timing.log("transfer");
      TransferToken current = readToken(in);
      if (DO_TIMING)
        timing.log("readToken");

      switch (current.type) {
        case tt_word:
          applyWord(current.content);
          tmpword.add(current.content);
          break;

        case tt_blank:
          ms.step(' ');
          tmpblank.add(current.content);
          break;

        case tt_eof:
          if (tmpword.size() != 0) {
            tmpblank.add(current.content);
            ms.clear();
          } else {
            fputws_unlocked(current.content, output);
            if (DO_TIMING) {
              timing.log("transfer");
              timing.report();
            }
            if (IOUtils.timing != null)
              IOUtils.timing.log("Process interchunk/postchunk");
            return;
          }
          break;

        default:

          System.err.println("Error: Unknown input token.");
          return;
      }
    }
  }

  private void applyRule(Appendable output, Method rule, ArrayList<String> words, ArrayList<String> blanks)
      throws Exception {
    if (DEBUG)
      System.err.println("applyRule(" + rule + ", " + words + ", " + blanks);
    if (DO_TIMING)
      timing.log("other1");


    int limit = words.size(); // number of words

    Object[] args = new Object[1 + limit + limit - 1]; // number of arguments out:1, words:limit, blanks:limit-1
    int argn = 0;
    args[argn++] = output;


    for (int i = 0; i != limit; i++) {
      if (i > 0)
        args[argn++] = blanks.get(i - 1);

      Pair<String, Integer> tr;
      if (useBilingual && preBilingual == false) {
        if (DO_TIMING)
          timing.log("applyRule 1");
        tr = fstp.biltransWithQueue(words.get(i), false);
        if (DO_TIMING)
          timing.log("applyRule/fstp.biltransWithQueue ");
      } else if (preBilingual) {
        /* input = ^esperanto/english1/english2/english3$
         * <spectei> we can only have one translation in transfer
         * <spectei> so we want ^esperanto/english1 BREAK
         */
        String[] splits = words.get(i).split("/");
        String sl = splits[0];
        String tl = splits.length > 1 ? splits[1] : "";
        // http://freedict.svn.sourceforge.net/viewvc/apertium/trunk/apertium/apertium/transfer.cc?r1=35560&r2=35639
        //words.set(i, sl);
        tr = new Pair<String, Integer>(tl, 0);
      } else {
        // If no bilingual dictionary is used (i.e. for apertium-transfer -n, for apertium-interchunk and for apertium-postchunk), then the sl and tl values will be the same.
        tr = new Pair<String, Integer>(words.get(i), 0);
      }

      args[argn++] = new TransferWord(words.get(i), tr.first, tr.second);
    }

    //here was in C++: processRule(lastrule) to interpret XML, but we use Java bytecode via Java Method Invocation
    if (DEBUG)
      System.err.println("#args = " + args.length);
    if (DEBUG)
      System.err.println("processRule:" + rule.getName() + "(" + Arrays.toString(args));
    try {
      if (DO_TIMING)
        timing.log("applyRule 1");
      rule.invoke(transferObject, args);
      if (DO_TIMING)
        timing.log("rule invoke");
    } catch (Exception e) {
      System.err.println("Error during invokation of " + rule);
      System.err.println("#args = " + args.length);
      System.err.println("processRule:" + rule.getName() + "(" + Arrays.toString(args));
      throw e;
    }


    if (DO_TIMING)
      timing.log("applyRule 1");
  }

  void applyWord(String word_str) {
    if (DO_TIMING)
      timing.log("other");
    ms.step('^');
    for (int i = 0, limit = word_str.length(); i < limit; i++) {
      switch (word_str.charAt(i)) {
        case '\\':
          i++;
          ms.step(Character.toLowerCase(word_str.charAt(i)), any_char);
          break;

        case '/':
          i = limit;
          break;

        case '<':
          for (int j = i + 1; j != limit; j++) {
            if (word_str.charAt(j) == '>') {
              int symbol = alphabet.cast(word_str.substring(i, j + 1));
              if (symbol != 0) {
                ms.step(symbol, any_tag);
              } else {
                ms.step(any_tag);
              }
              i = j;
              break;
            }
          }
          break;

        default:
          ms.step(Character.toLowerCase(word_str.charAt(i)), any_char);
          break;
      }
    }
    ms.step('$');
    if (DO_TIMING)
      timing.log("applyWord");
  }

  void setCaseSensitiveMode(boolean b) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public void setUseBilingual(boolean useBilingual) {
    this.useBilingual = useBilingual;
  }

  public void setPreBilingual(boolean preBilingual) {
    this.preBilingual = preBilingual;
  }

  //TODO: Cleanup -- unnecessary method
  private void fputwc_unlocked(char c, Appendable output) throws IOException {
    output.append(c);
  }

  //TODO: Cleanup -- unnecessary method
  private void fputws_unlocked(String first, Appendable output) throws IOException {
    output.append(first);
  }
}
