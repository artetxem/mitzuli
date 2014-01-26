/*
 * Copyright (C) 2012 Mikel Artetxe
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 */
package org.apertium;

import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apertium.pipeline.Dispatcher;
import org.apertium.pipeline.Mode;
import org.apertium.pipeline.Program;
import org.apertium.tagger.Tagger;
import org.apertium.transfer.ApertiumTransfer;
import org.apertium.utils.IOUtils;
import org.apertium.interchunk.ApertiumInterchunk;
import org.apertium.lttoolbox.LTProc;

/**
 *
 * @author Mikel Artetxe
 */
public class Translator {
  private static Object base;
  private static String modeId;
  private static String[] modeFiles;
  private static Mode mode;
  //Display ambiguity, defaults to false
  private static boolean dispAmb = false;
  //Display marks '*' for unknown words, defaults to true
  private static boolean dispMarks = true;
  private static boolean cacheEnabled = false;
  private static boolean parallelProcessingEnabled = false;
  private static Thread loader;

  private Translator() {
  }

  public static void setDisplayAmbiguity(boolean displayAmbiguity) {
    dispAmb = displayAmbiguity;
  }

  public static void setDisplayMarks(boolean displayMarks) {
    dispMarks = displayMarks;
  }

  public static void setParallelProcessingEnabled(boolean enabled) {
    parallelProcessingEnabled = enabled;
  }

  public static void setDelayedNodeLoadingEnabled(boolean enabled) {
    org.apertium.lttoolbox.process.TransducerExe.DELAYED_NODE_LOADING = enabled;
  }

  /** @deprecated */
  public static void setMemmappingEnabled(boolean enabled) {
    System.err.println("setMemmappingEnabled not supported");
  }

  public static void setCacheEnabled(boolean enabled) {
    cacheEnabled = enabled;
    LTProc.setCacheEnabled(enabled);
    Tagger.setCacheEnabled(enabled);
    ApertiumTransfer.setCacheEnabled(enabled);
    ApertiumInterchunk.setCacheEnabled(enabled);
  }

  public static void clearCache() {
    LTProc.clearCache();
    Tagger.clearCache();
    ApertiumTransfer.clearCache();
    ApertiumInterchunk.clearCache();
    System.gc();
  }

  public static void setJarAsBase() throws Exception {
    if (loader != null) {
      loader.interrupt();
      loader.join();
      loader = null;
    }
    clearCache();

    try {
      IOUtils.setJarAsResourceZip();
      modeFiles = IOUtils.listFilesWithExtension("mode");
      mode = modeFiles.length == 1 ? new Mode(modeFiles[0]) : null;
      if (modeFiles.length != 1)
        modeId = null;
    } catch (Exception e) {
      setBase(Translator.class.getClassLoader());
    }
  }

  public static void setBase(String filename) throws Exception {
    if (filename.equals(base))
      return;

    if (loader != null) {
      loader.interrupt();
      loader.join();
      loader = null;
    }
    clearCache();

    if (filename.endsWith(".mode")) {
      IOUtils.setResourceZip(null);
      IOUtils.setClassLoader(null);
      base = filename;
      modeFiles = new String[1];
      modeFiles[0] = filename;
      setMode(filename);
    } else if (filename.endsWith(".jar") || filename.endsWith(".zip")) {
      IOUtils.setResourceZip(filename);
      base = filename;
      modeFiles = IOUtils.listFilesWithExtension("mode");
      if (modeFiles.length == 1)
        setMode(modeFiles[0]);
      else {
        mode = null;
        modeId = null;
      }
    } else
      throw new Exception("Invalid base file.");
  }

  public static void setBase(ClassLoader classLoader) throws Exception {
    if (classLoader.equals(base))
      return;

    if (loader != null) {
      loader.interrupt();
      loader.join();
      loader = null;
    }
    clearCache();

    IOUtils.setClassLoader(classLoader);
    base = classLoader;
    modeFiles = IOUtils.readFile("modes").split("\n");
    if (modeFiles.length == 1)
      setMode(modeFiles[0]);
    else {
      mode = null;
      modeId = null;
    }
  }

  public static void setBase(String path, ClassLoader classLoader) throws Exception {
    if ((path + classLoader).equals(base))
      return;
    base = path + classLoader;

    if (loader != null) {
      loader.interrupt();
      loader.join();
      loader = null;
    }
    clearCache();

    IOUtils.setBasePathAndClassLoader(path, classLoader);
    modeFiles = IOUtils.listFilesWithExtension("mode");
    if (modeFiles.length == 1)
      setMode(modeFiles[0]);
    else {
      mode = null;
      modeId = null;
    }
  }

  public static void setMode(String mode) throws Exception {
    if (mode.equals(modeId))
      return;

    for (String s : modeFiles)
      if (s.endsWith("/" + mode + ".mode") || s.equals(mode + ".mode") || s.equals(mode)) {
        Translator.modeId = mode;
        Translator.mode = new Mode(s);
        if (cacheEnabled) {
          if (loader != null) {
            loader.interrupt();
            loader.join();
          }
          clearCache();
          (loader = new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                translate("");
              } catch (Exception e) {
              }
            }
          })).start();
        }
        return;
      }
    throw new IllegalArgumentException("Invalid mode. Valid modes are " + Arrays.toString(modeFiles));
  }

  public static String[] getAvailableModes() {
    String modes[] = new String[modeFiles.length];
    for (int i = 0; i < modeFiles.length; i++)
      modes[i] = modeFiles[i].substring(modeFiles[i].lastIndexOf('/') + 1, modeFiles[i].length() - 5);
    return modes;
  }

  public static String getTitle(String id) {
    id = id.substring(id.lastIndexOf('/') + 1);
    if (id.endsWith(".mode") || id.endsWith(".jnlp"))
      id = id.substring(0, id.length() - 5);
    else if (id.endsWith(".jar") || id.endsWith(".zip"))
      id = id.substring(0, id.length() - 4);
    ArrayList<String[]> unidirectionalPairs = new ArrayList<String[]>();
    ArrayList<String[]> bidirectionalPairs = new ArrayList<String[]>();
    String pairs[] = id.split(",");
    for (int i = 0; i < pairs.length; i++) {
      String pair[] = pairs[i].split("-");
      if (pair.length < 2 || pairs.length > 1 && (pair.length > 2 || pair[0].contains("_") || pair[1].contains("_")))
        continue;
      for (int j = 0; j < pair.length; j++)
        pair[j] = pair[j].trim();
      boolean found = false;
      for (int j = 0; j < unidirectionalPairs.size() && !found; j++) {
        if (unidirectionalPairs.get(j)[0].equals(pair[0]) && unidirectionalPairs.get(j)[1].equals(pair[1]))
          found = true;
        else if (unidirectionalPairs.get(j)[0].equals(pair[1]) && unidirectionalPairs.get(j)[1].equals(pair[0])) {
          bidirectionalPairs.add(unidirectionalPairs.remove(j));
          found = true;
        }
      }
      if (!found)
        unidirectionalPairs.add(pair);
    }
    if (unidirectionalPairs.isEmpty() && bidirectionalPairs.isEmpty())
      return id;
    else {
      StringBuilder title = new StringBuilder();
      for (String pair[] : bidirectionalPairs) {
        if (title.length() != 0)
          title.append(", ");
        title.append(getTitleForPair(pair, true));
      }
      for (String pair[] : unidirectionalPairs) {
        if (title.length() != 0)
          title.append(", ");
        title.append(getTitleForPair(pair, false));
      }
      return title.toString();
    }
  }

  private static String getTitleForPair(String[] pair, boolean bidirectional) {
    if (pair.length == 0)
      return "";
    if (pair.length == 1)
      return pair[0];

    StringBuilder title = new StringBuilder();

    String lang[] = pair[0].split("_");
    title.append(getTitleForCode(lang[0]));
    for (int i = 1; i < lang.length; i++)
      title.append("(").append(lang[i].toUpperCase()).append(")");

    title.append(bidirectional ? " ⇆ " : " → ");

    lang = pair[1].split("_");
    title.append(getTitleForCode(lang[0]));
    for (int i = 1; i < lang.length; i++)
      title.append("(").append(lang[i].toUpperCase()).append(")");
    for (int i = 2; i < pair.length; i++)
      title.append(" (").append(pair[i].toUpperCase()).append(")");

    return title.toString();
  }

  private static String getTitleForCode(String code) {
    String title;
    title = new Locale(code).getDisplayLanguage();
    if (code.equals(title)) {
      if (codeToTitle == null)
        initCodeToTitle();
      title = codeToTitle.get(code);
      if (title == null)
        title = code;
    }
    return title;
  }
  private static HashMap<String, String> codeToTitle;

  private static void initCodeToTitle() {
    codeToTitle = new HashMap<String, String>();

    //Trunk
    codeToTitle.put("ast", "Asturian");
    codeToTitle.put("sme", "Northern Sami");
    codeToTitle.put("nob", "Norwegian Bokmål");

    //Incubator
    codeToTitle.put("sco", "Scots");
    codeToTitle.put("eng", "English");
    codeToTitle.put("kaz", "Kazakh");
    codeToTitle.put("tel", "Telugu");
    codeToTitle.put("eus", "Basque");
    codeToTitle.put("fin", "Finnish");
    codeToTitle.put("udm", "Udmurt");
    codeToTitle.put("kaz", "Kazakh");
    codeToTitle.put("tat", "Tatar");
    codeToTitle.put("kpv", "Komi-Zyrian");
    codeToTitle.put("mhr", "Eastern Mari");
    codeToTitle.put("mfe", "Morisyen");
    codeToTitle.put("csb", "Kashubian");
    codeToTitle.put("dsb", "Lower Sorbian");
    codeToTitle.put("hsb", "Upper Sorbian");
    codeToTitle.put("quz", "Cusco Quechua");
    codeToTitle.put("spa", "Spanish");
    codeToTitle.put("rup", "Aromanian");
    codeToTitle.put("deu", "German");
    codeToTitle.put("sma", "Southern Sami");
    codeToTitle.put("tgl", "Tagalog");
    codeToTitle.put("ceb", "Cebuano");

    //Nursery
    codeToTitle.put("ssp", "Spanish sign language");
    codeToTitle.put("smj", "Lule Sami");
    codeToTitle.put("tur", "Turkish");
    codeToTitle.put("rus", "Russian");

    //Staging
    codeToTitle.put("kir", "Kirghiz");
  }

  public static String translate(String text) throws Exception {
    StringWriter output = new StringWriter();
    translate(new StringReader(text), output);
    return output.toString();
  }

  public static String translate(String text, String format) throws Exception {
    StringWriter output = new StringWriter();
    translate(new StringReader(text), output, format);
    return output.toString();
  }

  public static String translate(String text, Program deformatter, Program reformatter) throws Exception {
    StringWriter output = new StringWriter();
    translate(new StringReader(text), output, deformatter, reformatter);
    return output.toString();
  }

  public static void translate(Reader input, Appendable output) throws Exception {
    translate(input, output, "txt");
  }

  public static void translate(Reader input, Appendable output, String format) throws Exception {
    translate(input, output, new Program("apertium-des" + format), new Program("apertium-re" + format));
  }

  public static void translate(Reader input, Appendable output, Program deformatter, Program reformatter) throws Exception {
    translate(input, output, deformatter, reformatter, null);
  }

  public interface TranslationProgressListener {
    public void onTranslationProgress(String subtask, int progress, int maxProgress);
  }

  private static final TranslationProgressListener dummyTranslationProgressListener = new TranslationProgressListener() {
    @Override
    public void onTranslationProgress(String subtask, int progress, int maxProgress) {
      // nothing
    }
  };
  private static Exception processingException = null;

  public static void translate(Reader input, Appendable output, Program deformatter, Program reformatter, TranslationProgressListener progressListener) throws Exception {
    if (Thread.interrupted())
      throw new InterruptedException();
    if (cacheEnabled && loader != null && !Thread.currentThread().equals(loader)) {
      loader.interrupt();
      loader.join();
      loader = null;
    }

    if (progressListener == null)
      progressListener = dummyTranslationProgressListener;
    int progressMax = mode.getPipelineLength() + 2; // +1 for (re)formatting
    progressListener.onTranslationProgress(deformatter.getCommandName(), 0, progressMax);

    if (parallelProcessingEnabled) {
      // Warning: Parallel processing not tested
      PipedWriter intOutput = new PipedWriter();
      dispatch(deformatter, input, intOutput, parallelProcessingEnabled);
      Reader intInput = new PipedReader(intOutput);

      for (int i = 0; i < mode.getPipelineLength(); i++) {
        intOutput = new PipedWriter();
        Program prg = mode.getProgramByIndex(i);
        progressListener.onTranslationProgress(prg.getCommandName(), i + 1, progressMax);
        dispatch(prg, intInput, intOutput, parallelProcessingEnabled);
        intInput = new PipedReader(intOutput);
      }
      progressListener.onTranslationProgress(reformatter.getCommandName(), progressMax - 1, progressMax);
      dispatch(reformatter, intInput, output, false); // wait for it to finish
    } else {
      StringBuilder intOutput = new StringBuilder(1000);
      dispatch(deformatter, input, intOutput, parallelProcessingEnabled);
      Reader intInput = new StringReader(intOutput.toString());

      for (int i = 0; i < mode.getPipelineLength(); i++) {
        intOutput = new StringBuilder(intOutput.length());
        Program prg = mode.getProgramByIndex(i);
        progressListener.onTranslationProgress(prg.getCommandName(), i + 1, progressMax);
        dispatch(prg, intInput, intOutput, parallelProcessingEnabled);
        intInput = new StringReader(intOutput.toString());
      }
      progressListener.onTranslationProgress(reformatter.getCommandName(), progressMax - 1, progressMax);
      dispatch(reformatter, intInput, output, false); // wait for it to finish
    }

    if (processingException != null) {
      Exception aux = processingException;
      processingException = null;
      throw aux;
    }
    progressListener.onTranslationProgress("", progressMax, progressMax);
  }

  private static void dispatch(final Program program, final Reader input, final Appendable output, boolean async) throws Exception {
    if (async)
      new Thread(new Runnable() {
        @Override
        public void run() {
          try {
            Dispatcher.dispatch(program, input, output, dispAmb, dispMarks);
            IOUtils.close(output);
          } catch (Exception ex) {
            processingException = new Exception(program.toString(), ex);
          }
        }
      }).start();
    else
      Dispatcher.dispatch(program, input, output, dispAmb, dispMarks);
  }
}
