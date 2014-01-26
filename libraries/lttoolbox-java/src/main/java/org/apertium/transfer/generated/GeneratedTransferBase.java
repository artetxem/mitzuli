/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.transfer.generated;

import java.util.Arrays;

/**
 *
 * @author Jacob Nordfalk
 */
public abstract class GeneratedTransferBase {
  public void init() {
  }

  public abstract boolean isOutputChunked();
  public boolean debug;

  protected void logCall(String met) {
    System.err.println("call:" + met);
  }

  protected void logCall(String met, Object... args) {
    System.err.println("call:" + met + " " + Arrays.toString(args));
  }
  /**
   * Is used by the runtime to detect obsoleted bytecode that needs to be regenerated
   * This superclass method should return 0, as this makes old code return 0.
   * Generated classes should define this method to return a number.
   * Each time an inompatible change is made the number must be increased.
   *
   * @return The loaded class' version number. Must match runtime to be compatible,
   * future work; currently not used
   * public int getCompatibilityVersionNumber() {
   * return 0;
   * }
   */
  /**
   * The runtime (not the loaded class)'s version number
   * Each time an inompatible change is made the number must be increased.
   *
   * @return Version number. Must match runtime to be compatible,
   * future work; currently not used
   * public static int getRuntimeVersionNumber() {
   * return 1;
   * }
   */
}
