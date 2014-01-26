/*
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
package org.apertium.pipeline;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import static org.apertium.utils.MiscUtils.getLineSeparator;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicReference;
import org.apertium.formatter.OmegatFormatter;
import org.apertium.formatter.TextFormatter;
import org.apertium.interchunk.ApertiumInterchunk;
import org.apertium.interchunk.Interchunk;
import org.apertium.lttoolbox.LTProc;
import org.apertium.postchunk.ApertiumPostchunk;
import org.apertium.postchunk.Postchunk;
import org.apertium.pretransfer.PreTransfer;
import org.apertium.tagger.Tagger;
import org.apertium.transfer.ApertiumTransfer;
import org.apertium.utils.IOUtils;

/**
 * @author Stephen Tigner
 *
 */
public class Dispatcher {
  private static final String splitPattern = "[ ]+";

  private static void doInterchunk(Program prog, Reader input, Appendable output) throws Exception {
    ApertiumInterchunk.CommandLineParams par = new ApertiumInterchunk.CommandLineParams();
    /* Parse the command line. The passed-in CommandLineParams object
     * will be modified by this method.
     */
    String[] args = prog.getParameters().split(splitPattern);
    if (!ApertiumInterchunk.parseCommandLine(args, par, "Interchunk", true)) {
      throw new IllegalArgumentException("Failed to parse Interchunk arguments.");
    }
    /* Assume internal i/o, don't allow for specifying external temp
     * files for i/o.
     */
    par.input = input;
    par.output = output;

    ApertiumInterchunk.doMain(par, new Interchunk());
  }

  private static void doPostchunk(Program prog, Reader input, Appendable output)
      throws Exception {
    /* Yes, there's duplicate code here with the method above, but
     * there's only a few lines of actual code here, and I ran into issues
     * trying to reduce the duplication further than this.
     */

    ApertiumPostchunk.CommandLineParams par =
        new ApertiumPostchunk.CommandLineParams();
    /* Parse the command line. The passed-in CommandLineParams object
     * will be modified by this method.
     */
    String[] args = prog.getParameters().split(splitPattern);
    if (!ApertiumPostchunk.parseCommandLine(args, par, "Interchunk", true)) {
      throw new IllegalArgumentException("Failed to parse Postchunk arguments." + args);
    }
    /* Assume internal I/O, don't allow for specifying external temp
     * files for I/O.
     * External input and output files are used only at the beginning
     * and end of the chain, and are handled by the code that calls the
     * dispatcher.
     */
    par.input = input;
    par.output = output;

    ApertiumPostchunk.doMain(par, new Postchunk());
  }

  private static void doPretransfer(Program prog, Reader input, Appendable output)
      throws IOException {
    PreTransfer.CommandLineParams params = new PreTransfer.CommandLineParams();
    String[] args = prog.getParameters().split(splitPattern);
    PreTransfer.parseArgs(args, params, true);

    /* Assume internal I/O, don't allow for specifying external temp
     * files for I/O.
     * External input and output files are used only at the beginning
     * and end of the chain, and are handled by the code that calls the
     * dispatcher.
     */
    PreTransfer.processStream(input, output, params.nullFlush);
  }

  private static void doTagger(Program prog, Reader input, Appendable output,
      boolean dispAmb) {
    String paramString = prog.getParameters();
    String replacement = (dispAmb ? "-m" : "");
    paramString = paramString.replaceAll("\\$2", replacement);

    String[] args = paramString.split(splitPattern);
    Tagger.taggerDispatch(args, input, output);
  }

  private static void doTextFormat(Program prog, Reader input, Appendable output,
      boolean deformatMode) throws Exception {
    String paramString = prog.getParameters();

    if (deformatMode) {
      /* Since the same class is used for deformatting and re-formatting, but the
       * .mode files aren't setup like that, so prepending "-d" to set it to
       * deformatting mode.
       */
      paramString = "-d " + paramString;
    } else {
      /* If not in deformatting mode, must be in reformatting mode.
       * So prepend with "-r" instead.
       */
      paramString = "-r " + paramString;
    }

    TextFormatter formatter = new TextFormatter();

    String[] args = paramString.split(splitPattern);
    formatter.doMain(args, input, output);
  }

  private static void doOmegatFormat(Program prog, Reader input, Appendable output,
      boolean deformatMode) throws Exception {
    String paramString = prog.getParameters();

    if (deformatMode) {
      /* Since the same class is used for deformatting and re-formatting, but the
       * .mode files aren't setup like that, so prepending "-d" to set it to
       * deformatting mode.
       */
      paramString = "-d " + paramString;
    } else {
      /* If not in deformatting mode, must be in reformatting mode.
       * So prepend with "-r" instead.
       */
      paramString = "-r " + paramString;
    }

    OmegatFormatter formatter = new OmegatFormatter();

    String[] args = paramString.split(splitPattern);
    formatter.doMain(args, input, output);
  }

  private static void doTransfer(Program prog, Reader input, Appendable output)
      throws Exception {
    String[] args = prog.getParameters().split("[ ]+");
    ApertiumTransfer.doMain(args, input, output);
  }

  private static void doLTProc(Program prog, Reader input, Appendable output,
      boolean dispMarks) throws IOException {
    String paramString = prog.getParameters();
    String replacement = (dispMarks ? "-g" : "-n");
    paramString = paramString.replaceAll("\\$1", replacement);

    String[] args = paramString.split(splitPattern);
    LTProc.doMain(args, input, output);
  }

  private static void doUnknown(Program prog, final Reader input, Appendable output, boolean dispAmb, boolean dispMarks) throws Exception {
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    // As we have *no* idea what it might be, we will try to treat each parameter as a file
    // name and copy it to temp dir, just in case the command needs it to be present on the
    // working directory or in a subdirectory of the working directory
    for (String filename : prog.getParameters().split(" ")) {
      try {
        BufferedInputStream bis = new BufferedInputStream(IOUtils.openInFileStream(filename));
        File dest = new File(tempDir, filename);
        dest.getParentFile().mkdirs();
        int b;
        byte buffer[] = new byte[1024];
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest), 1024);
        while ((b = bis.read(buffer, 0, 1024)) != -1)
          bos.write(buffer, 0, b);
        bos.flush();
        bos.close();
        bis.close();
      } catch (Exception e) {
      } // Ignore errors comming from that it wasn't a file name
    }
    final Process extProcess = Runtime.getRuntime().exec(prog.getFullPath() + " " + prog.getParameters().replaceAll("\\$1", dispMarks ? "-g" : "-n").replaceAll("\\$2", dispAmb ? "-m" : ""), null, tempDir);

    // We will create a new thread to copy from the input Reader to the OutputStream of the
    // external process (note that we must convert the input to UTF-8)
    // The following variable is used to be able to propagate an exception that might happen
    // inside the new thread
    final AtomicReference<Exception> writingException = new AtomicReference<Exception>();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          char buffer[] = new char[1024];
          int count;
          while ((count = input.read(buffer)) != -1)
            extProcess.getOutputStream().write(new String(buffer, 0, count).getBytes("UTF-8"));
          extProcess.getOutputStream().close();
        } catch (Exception ex) {
          writingException.set(ex);
        }
      }
    }).start();

    // We copy from the OutputStream of the external process to the output Appendable that
    // we were given (note that we must convert the output to UTF-16)
    byte buffer[] = new byte[1024];
    int count;
    while ((count = extProcess.getInputStream().read(buffer)) != -1)
      output.append(new String(buffer, 0, count, "UTF-8"));

    // We wait for the external process to end (its InputStream is surely closed, but the
    // process might still be running)
    extProcess.waitFor();

    // We check the exit value of the external process
    if (extProcess.exitValue() != 0) {
      //Assume process follows convention of 0 == Success
      String errorString = prog.getCommandName() + " (Unknown) -- "
          + "External program failed, returned non-zero value: "
          + extProcess.exitValue();
      throw new Exception(errorString);
    }

    // We check that there hasn't been any error while writing to the OutputStream of the
    // external process
    if (writingException.get() != null)
      throw writingException.get();

  }

  public static void dispatch(Program prog, Reader input, Appendable output,
      boolean dispAmb, boolean dispMarks) throws Exception {
    switch (prog.getProgram()) {
      case INTERCHUNK:
        doInterchunk(prog, input, output);
        break;
      case LT_PROC:
        doLTProc(prog, input, output, dispMarks);
        break;
      case POSTCHUNK:
        doPostchunk(prog, input, output);
        break;
      case PRETRANSFER:
        doPretransfer(prog, input, output);
        break;
      case TAGGER:
        doTagger(prog, input, output, dispAmb);
        break;
      case TRANSFER:
        doTransfer(prog, input, output);
        break;
      case TXT_DEFORMAT:
        doTextFormat(prog, input, output, true);
        break;
      case TXT_REFORMAT:
        doTextFormat(prog, input, output, false);
        break;
      case OMEGAT_DEFORMAT:
        doOmegatFormat(prog, input, output, true);
        break;
      case OMEGAT_REFORMAT:
        doOmegatFormat(prog, input, output, false);
        break;
      case UNKNOWN:
        doUnknown(prog, input, output, dispAmb, dispMarks);
        break;
      default:
        //We should never get here.
        throw new IllegalArgumentException("Unrecognized ProgEnum: " + prog.getProgram());
    }
    IOUtils.flush(output);
  }
}

