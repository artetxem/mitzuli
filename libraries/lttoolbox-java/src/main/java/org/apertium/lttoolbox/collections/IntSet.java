/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apertium.lttoolbox.collections;

import java.util.Collection;

/**
 *
 * @author Jacob Nordfalk
 */
public interface IntSet extends Iterable<Integer> {
  public void clear();

  public int size();

  public void add(int i);

  public void remove(int i);

  public boolean contains(int i);

  public int firstInt();

  public boolean addAll(Collection<? extends Integer> c);

  public boolean addAll(IntSet c);
}
