/*
 * Copyright (C) 2010 Stephen Tigner
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
package org.apertium.utils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.*;

/**
 * @author Stephen Tigner
 *
 */
public class IOUtils {
  private static final boolean DEBUG = false;
  private static ZipFile zip;
  private static ClassLoader loader;
  private static File parent;
  /** Path to location of cached node indexes */
  public static File cacheDir = new File("/tmp/apertium-index-cache");

  static {
    try {
      cacheDir = new File(System.getProperty("java.io.tmpdir"), "apertium-index-cache");
    } catch (Throwable t) {
    } // ignore
  }
  /** Set this to a new Timing object to collect stats about how long stuff is taking */
  public static Timing timing;

  public static void setJarAsResourceZip() throws IOException {
    zip = new ZipFile(new File(IOUtils.class.getProtectionDomain().getCodeSource().getLocation().getFile()));
    parent = null;
    loader = null;
  }

  public static void setResourceZip(String filename) throws IOException {
    zip = filename != null ? new ZipFile(openFile(filename)) : null;
    parent = null;
    loader = null;
  }

  public static void setClassLoader(ClassLoader classLoader) {
    loader = classLoader;
    parent = null;
    zip = null;
  }

  public static void setBasePathAndClassLoader(String basePath, ClassLoader classLoader) throws Exception {
    File f = new File(basePath);
    if (!f.exists())
      throw new FileNotFoundException();
    if (!f.isDirectory())
      throw new Exception(basePath + " is not a directory");
    parent = f;
    loader = classLoader;
    zip = null;
  }

  public static ClassLoader getLoader() {
    return loader;
  }

  public static String[] listFilesWithExtension(String extension) {
    final String ext = extension.startsWith(".") ? extension : "." + extension;
    if (zip != null) {
      ArrayList<String> list = new ArrayList();
      Enumeration entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement();
        if (entry.getName().endsWith(ext))
          list.add(entry.getName());
      }
      return list.toArray(new String[list.size()]);
    } else if (parent != null) {
      FilenameFilter filter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(ext);
        }
      };
      // We will look for files in some expected directories relative to parent
      ArrayList<String> list = new ArrayList();
      File dir = parent;
      if (dir.exists() && dir.isDirectory())
        for (File f : dir.listFiles(filter))
          list.add(f.getPath());
      dir = new File(parent, "modes");
      if (dir.exists() && dir.isDirectory())
        for (File f : dir.listFiles(filter))
          list.add(f.getPath());
      dir = new File(new File(parent, "data"), "modes");
      if (dir.exists() && dir.isDirectory())
        for (File f : dir.listFiles(filter))
          list.add(f.getPath());
      return list.toArray(new String[list.size()]);
    } else
      return null;
  }

  public static byte[] loadByteArray(String filename) throws FileNotFoundException, IOException {
    byte byteArray[];
    if (zip != null || (loader != null && parent == null)) {
      InputStream is = openInFileStream(filename);
      byte buffer[] = new byte[1024];
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      int b;
      while ((b = is.read(buffer, 0, 1024)) != -1)
        bos.write(buffer, 0, b);
      bos.flush();
      bos.close();
      is.close();
      byteArray = bos.toByteArray();
    } else {
      File fileToRead = openFile(filename);
      FileInputStream fis = new FileInputStream(fileToRead);
      byteArray = new byte[(int) fileToRead.length()];
      fis.read(byteArray);
      fis.close();
    }
    return byteArray;
  }

  public static String getFilenameMinusExtension(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    return filename.substring(0, dotIndex);
  }

  /**
   * This method checks to see if there is a a trailing slash on the string
   * path given and if not, adds it. Checks for both forward and backslashes,
   * but only adds a forward slash.
   *
   * @param path -- The filename (path) string to check
   * @return The path string with a trailing slash added if one was missing,
   * or the same string if it was already there.
   */
  public static String addTrailingSlash(String path) {
    char lastChar = path.charAt(path.length() - 1);
    if (lastChar != '/' && lastChar != '\\') {
      /* If there is not a forward slash (unix) or backward
       * slash (Windows) at the end of the path, add a slash.
       * Java can handle mixed slashes, so only need to worry
       * about adding a forward slash.
       * The reason we aren't just using the pathSeparator system
       * property is that we might have a unix-style path on a
       * Windows system in the case of cygwin.
       */
      path += "/";
    }
    return path;
  }

  public static String readFile(String path) throws IOException {
    return readFile(path, "UTF-8");
  }

  public static String readFile(String path, String encoding) throws IOException {
    /* File fileToRead = openFile(path);
     * FileInputStream fis = new FileInputStream(fileToRead);
     * byte[] byteArray = new byte[(int) fileToRead.length()];
     * fis.read(byteArray);
     * fis.close(); */

    byte[] byteArray = loadByteArray(path);

    /* If we don't do it this way, by explicitly setting UTF-8 encoding
     * when reading in a file, we get mojibake (scrambled character encodings).
     */
    String fileContents = new String(byteArray, encoding);
    return fileContents;
  }

  public static void writeFile(String path, String data) throws IOException {
    writeFile(path, data, "UTF-8");
  }

  public static void writeFile(String path, String data, String encoding)
      throws IOException {
    Writer output = openOutFileWriter(path);
    output.write(data);
    output.close();
  }

  /**
   *
   * @return A reader for System.in with the default encoding of UTF-8.
   * @throws UnsupportedEncodingException
   */
  public static Reader getStdinReader() throws UnsupportedEncodingException {
    return getStdinReader("UTF-8");
  }

  public static Reader getStdinReader(String encoding) throws UnsupportedEncodingException {
    return new BufferedReader(new InputStreamReader(System.in, encoding));
  }

  /**
   *
   * @return A writer for System.out with the default encoding of UTF-8.
   * @throws UnsupportedEncodingException
   */
  public static Writer getStdoutWriter() throws UnsupportedEncodingException {
    return getStdoutWriter("UTF-8");
  }

  public static Writer getStdoutWriter(String encoding) throws UnsupportedEncodingException {
    return new BufferedWriter(new OutputStreamWriter(System.out, encoding));
  }

  /**
   * Loads an input stream fully <b>keeping the whole file in memory</b>.
   * <b>WARNING</b>This method should be avoided on Android and other memory constrained devices, unless you are discarding the returned ByteBuffer
   *
   * @param input will be read and closed
   * @return data read
   */
  public static ByteBuffer inputStreamToByteBuffer(InputStream input) throws IOException {
    ByteArrayOutputStream bas = new ByteArrayOutputStream(1000000); // 1MB is a waste, but this method should be avoided on memory-constrained devices anyway
    byte[] buf = new byte[1024];
    int n;
    while ((n = input.read(buf)) != -1)
      bas.write(buf, 0, n);
    input.close();
    buf = bas.toByteArray();
    ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
    return byteBuffer;
  }

  /**
   * Takes a filename string and a command-line label and attempts to open an input
   * stream to the file given in the filename.
   *
   * @param filename - A string with the filename to open
   * @return An InputStream for reading from the file specified.
   * @throws FileNotFoundException
   */
  public static InputStream openInFileStream(String filename)
      throws FileNotFoundException {

    BufferedInputStream bis = null;
    if (zip != null) {
      try {
        if (filename.startsWith("/"))
          filename = filename.substring(1);
        ZipEntry entry = zip.getEntry(filename);
        //I we don't find the file at the given path, we will look for it somewhere else
        if (entry == null) {
          filename = filename.substring(filename.lastIndexOf('/') + 1);
          Enumeration entries = zip.entries();
          while (entries.hasMoreElements()) {
            ZipEntry entryAux = (ZipEntry) entries.nextElement();
            if (entryAux.getName().endsWith("/" + filename) || entryAux.getName().equals(filename)) {
              entry = entryAux;
              break;
            }
          }
        }
        if (entry == null)
          throw new FileNotFoundException("File: " + filename);
        bis = new BufferedInputStream(zip.getInputStream(entry));
        if (DEBUG)
          System.err.println("openInFileStream in ZIP " + filename);
      } catch (IOException ex) {
        ex.printStackTrace();
      }
    } else if (loader != null && parent == null) {
      if (filename.startsWith("/"))
        filename = filename.substring(1);
      InputStream is = loader.getResourceAsStream(filename);
      if (is == null)
        throw new FileNotFoundException("File: " + filename);
      bis = new BufferedInputStream(is);
      if (DEBUG)
        System.err.println("openInFileStream in ClassLoader " + filename);
    } else {
      File file = null;
      try {
        file = openFile(filename);
        bis = new BufferedInputStream(new FileInputStream(file));
        if (DEBUG)
          System.err.println("openInFileStream File " + filename);
      } catch (FileNotFoundException e) {
        throw new FileNotFoundException("File: " + file.getPath()
            + " ( " + filename + ") -- " + e.getLocalizedMessage());
      }
    }
    return bis;
  }

  /**
   * Opens a file as ByteBuffer
   *
   * @param filename - the file to open
   * @return the buffer containing the file
   */
  public static ByteBuffer openFileAsByteBuffer(String filename) throws IOException {
    if (!memMappingAvailable()) {
      // FAIL, revert to memory intensive processing :-(
      return inputStreamToByteBuffer(openInFileStream(filename));
    } else {
      MappedByteBuffer bb = memmap(filename);
      return bb;
    }
  }

  /**
   *
   * @param filename -- The file to open for reading.
   * @return A reader for the file with the default UTF-8 encoding.
   * @throws UnsupportedEncodingException
   * @throws FileNotFoundException
   */
  public static Reader openInFileReader(String filename)
      throws UnsupportedEncodingException, FileNotFoundException {
    return openInFileReader(filename, "UTF-8");
  }

  public static Reader openInFileReader(String filename, String encoding)
      throws UnsupportedEncodingException, FileNotFoundException {
    return new InputStreamReader(openInFileStream(filename), encoding);
  }

  /**
   * Takes a filename string and a command-line label and attempts to open an output
   * stream to the file given in the filename.
   *
   * @param filename - A string with the filename to open
   * @return An OutputStream for writing to the file specified.
   * @throws FileNotFoundException
   */
  public static OutputStream openOutFileStream(String filename) throws FileNotFoundException {
    File file = null;
    BufferedOutputStream bos = null;

    try {
      file = openFile(filename);
      bos = new BufferedOutputStream(new FileOutputStream(file));
    } catch (FileNotFoundException e) {
      throw new FileNotFoundException("File: " + file.getPath()
          + " ( " + filename + ") -- " + e.getLocalizedMessage());
    }

    return bos;
  }

  /**
   *
   * @param filename -- The file to open for writing.
   * @return A writer for the file with the default UTF-8 encoding.
   * @throws FileNotFoundException
   */
  public static Writer openOutFileWriter(String filename) throws FileNotFoundException {
    return openOutFileWriter(filename, "UTF-8");
  }

  public static Writer openOutFileWriter(String filename, String encoding) throws FileNotFoundException {
    return new OutputStreamWriter(openOutFileStream(filename));
  }

  public static FilenameFilter getExtensionFilter(final String extension) {
    FilenameFilter filter = new FilenameFilter() {
      private String _extension = extension;

      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(_extension);
      }
    };
    return filter;
  }

  public static String[] listFilesInDir(String path) {
    return listFilesInDir(path, null);
  }

  public static String[] listFilesInDir(String path, String extension) {
    File directory = openFile(path);
    String[] fileList;
    if (extension == null) {
      fileList = directory.list();
    } else {
      fileList = directory.list(getExtensionFilter(extension));
    }
    return fileList;
  }

  public static File openFile(String filename) {
    filename = filename.trim();
    File file = new File(filename);
    if (!file.exists()) {
      File aux = new File(parent, filename);
      if (aux.exists())
        file = aux;
    }
    try {
      if (!file.exists() && System.getProperty("os.name").startsWith("Windows")) {
        if (DEBUG) {
          System.err.println("*** DEBUG: Trying cygwin path...");
        }
        filename = getWindowsPathFromCygwin(filename);
        if (DEBUG) {
          System.err.println("*** DEBUG: Cygwin path -- " + filename);
        }
        if (filename != null) {
          File winFile = new File(filename);
          if (DEBUG) {
            System.err.println("*** DEBUG: winFile.exists() -- "
                + winFile.exists());
            System.err.println("*** DEBUG: winFile.getAbsolutePath() -- "
                + winFile.getAbsolutePath());
          }
          if (winFile.exists()) {
            file = winFile;
          }
          /* If trying to run it through cygwin fails, just return the
           * original file object, created with the original path.
           */
        }
      }
    } catch (Exception e) {
    }
    return file;
  }

  /**
   * Given a cygwin unix-style path, this calls the external cygwin utility
   * "cygpath" to return the equivalent Windows path.
   * This assumes that "cygpath" is on the user's path. It should be if they have
   * cygwin installed, but if it is not on the user's path, then we can't run it.
   *
   * @param filename -- The cygwin unix-style path and filename to convert
   * @return A windows-style path for that same filename that was input.
   */
  public static String getWindowsPathFromCygwin(String filename) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      Process extProcess = Runtime.getRuntime().exec("cygpath -m " + filename);
      extProcess.waitFor();
      if (extProcess.exitValue() != 0) {
        /* Assume process follows convention of 0 == Success.
         * Thus if the exit value is != 0, it failed
         */
        return null;
      }
      int currByte;
      while ((currByte = extProcess.getInputStream().read()) != -1) {
        output.write(currByte);
      }
      return output.toString("UTF-8");
    } catch (Exception e) { //catch all exceptions and discard them, returning null
      e.printStackTrace();
      return null;
    }
  }

  /** Flush if possible */
  public static void flush(Appendable output) throws IOException {
    if (output instanceof Flushable)
      ((Flushable) output).flush();
  }

  /** Close if possible */
  public static void close(Appendable output) throws IOException {
    if (output instanceof Closeable)
      ((Closeable) output).close();
  }

  public static boolean memMappingAvailable() {
    return !(zip != null || (loader != null && parent == null));
  }

  public static MappedByteBuffer memmap(String filename) throws IOException, FileNotFoundException {
    // YES, we have a file we can map!
    RandomAccessFile raf = new RandomAccessFile(openFile(filename), "r");
    MappedByteBuffer bb = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
    return bb;
  }

  public static ByteBuffer mapByteBuffer(File cachedFile, int cacheFileSize) throws IOException {
    ByteBuffer byteBufferPositions = null;

    if (cacheFileSize > 1024) // don't cache tiny files
      try {
        if (cachedFile.canRead() && cachedFile.length() == cacheFileSize) {
          RandomAccessFile raf = new RandomAccessFile(cachedFile, "r");
          byteBufferPositions = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileSize);
          return byteBufferPositions;
        }

        if (cachedFile.exists())
          cachedFile.delete();
        cachedFile.getParentFile().mkdirs();
        cachedFile.createNewFile();
        if (cachedFile.canWrite()) {
          RandomAccessFile raf = new RandomAccessFile(cachedFile, "rw");
          byteBufferPositions = raf.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, cacheFileSize);
          return byteBufferPositions;
        }
      } catch (Exception e) {
      }

    byteBufferPositions = ByteBuffer.allocate(cacheFileSize); //int[number_of_statesl];
    return byteBufferPositions;
  }
}
