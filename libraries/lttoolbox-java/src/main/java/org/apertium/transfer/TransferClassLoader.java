package org.apertium.transfer;

import static org.apertium.utils.IOUtils.openFile;
import static org.apertium.utils.IOUtils.getFilenameMinusExtension;
import static org.apertium.utils.IOUtils.addTrailingSlash;
import static org.apertium.utils.IOUtils.loadByteArray;
import static org.apertium.utils.IOUtils.getLoader;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.apertium.transfer.generation.TransferBytecode;
import org.apertium.utils.IOUtils;
import org.xml.sax.SAXException;

public class TransferClassLoader extends ClassLoader {
  public TransferClassLoader() {
    super(TransferClassLoader.class.getClassLoader());
  }

  @SuppressWarnings("unchecked")
  public Class loadClassFile(String filename) throws ClassNotFoundException, IOException {
    byte data[] = loadByteArray(filename);
    return defineClass(null, data, 0, data.length);
  }

  @SuppressWarnings("unchecked")
  public static Class loadTxClass(String txOrClassFile, String binFile)
      throws ClassNotFoundException, IOException {
    if (IOUtils.timing != null)
      IOUtils.timing.log("");
    try { // We first try to load the class directly
      String className = txOrClassFile.replace('.', '_').replace('-', '_').replace('/', '.').replace('\\', '.');
      if (className.startsWith("data."))
        className = "transfer_classes" + className.substring(4);
      if (className.endsWith("_class"))
        className = className.substring(0, className.length() - 6);
      ClassLoader loader = getLoader() != null ? getLoader() : TransferClassLoader.class.getClassLoader();
      return loader.loadClass(className);
    } catch (Exception e) {
    } //If it fails we will keep trying and, if necessary, generate it
    finally {
      if (IOUtils.timing != null)
        IOUtils.timing.log("Load transfer class1 for " + binFile);
    }
    return loadTxClass(txOrClassFile, binFile, new TransferClassLoader());
  }

  @SuppressWarnings("unchecked")
  public static Class loadTxClass(String txOrClassFile, String binFile, TransferClassLoader tcl)
      throws ClassNotFoundException, IOException {


    try {
      //If we have been given a class, we load it
      if (txOrClassFile.endsWith(".class") && openFile(txOrClassFile).exists())
        return tcl.loadClassFile(txOrClassFile);

      // Even if we haven't been given a class, the corresponding class might already exist...
      // let's try to load it! (This is necessary when working with compressed files, since
      // resources inside them cannot be accessed as Files and, thus, the following attempts
      // that are using them will fail)
      String classFile = txOrClassFile.replace('.', '_').replace('-', '_');
      if (classFile.startsWith("data/") || classFile.startsWith("data\\"))
        classFile = "transfer_classes" + classFile.substring(4);
      if (!classFile.endsWith(".class"))
        classFile += ".class";
      return tcl.loadClassFile(classFile);
    } catch (Exception e) {
    } //We will keep trying!
    finally {
      if (IOUtils.timing != null)
        IOUtils.timing.log("Load transfer class2 for " + binFile);
    }


    //OK, it seems that we will finally have to build the class...
    try {
      return buildAndLoadClass(openFile(txOrClassFile), openFile(binFile), tcl);
    } finally {
      if (IOUtils.timing != null)
        IOUtils.timing.log("Build transfer class for " + binFile);
    }
  }

  @SuppressWarnings("unchecked")
  private static Class buildAndLoadClass(File txFile, File binFile,
      TransferClassLoader tcl) throws ClassNotFoundException, IOException {

    String baseBinFilename = getFilenameMinusExtension(binFile.getName());
    String classFilename = baseBinFilename + ".class";

    /* If I made this a File, it would lose the slash or backslash at the end
     * and there's no reason to make this a File object, so keeping it just as
     * a string.
     */

    String tempDir = IOUtils.cacheDir.getAbsolutePath();
    tempDir = addTrailingSlash(tempDir);

    File classFile = openFile(addTrailingSlash(binFile.getParent()) + classFilename);
    // If it doesn't exist in the binFile directory, try the temp directory
    if (!classFile.exists()) {
      classFile = openFile(tempDir + classFilename);
    }
    // If it doesn't exist there either, switch back to the binFile
    // directory
    if (!classFile.exists()) {
      classFile = openFile(addTrailingSlash(binFile.getParent()) + classFilename);
    }

    // If the class file exists already, try and load it.
    if (classFile.exists()) {
      return tcl.loadClassFile(classFile.getPath());
    }

    // Generate and return the class file
    try {
      TransferBytecode tb = new TransferBytecode(txFile.getPath());
      try { //Try to dump the class to avoid having to create it again next time
        tb.dump(addTrailingSlash(txFile.getParent()) + classFilename);
      } catch (Exception e1) {
        try { //Try in the temporary directory
          tb.dump(tempDir + classFilename);
        } catch (Exception e2) {
        } //Do nothing (the class will be generated again next time)
      }
      return tb.getJavaClass();
    } catch (ParserConfigurationException e) {
      throw new IOException("TX File (" + txFile + ") parsing failed -- " + e.getLocalizedMessage());
    } catch (SAXException e) {
      throw new IOException("TX File (" + txFile + ") parsing failed -- " + e.getLocalizedMessage());
    }

  }
}
