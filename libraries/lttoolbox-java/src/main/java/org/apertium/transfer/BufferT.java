package org.apertium.transfer;


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
/**
 * circular character buffer class
 *
 * @author Raah
 */
public class BufferT<E> {
  /**
   * Buffer size.
   */
  int size;
  /**
   * Buffer array.
   */
  E[] buf;
  /**
   * Buffer current position.
   */
  int currentpos;
  /**
   * Last position.
   */
  int lastpos;

  public BufferT() {
    this(64);
  }

  /**
   * Copy the buffer
   *
   * @param b the buffer to copy
   */
  @SuppressWarnings("unchecked")
  public void copy(BufferT<E> b) {
    currentpos = b.currentpos;
    lastpos = b.lastpos;
    size = b.size;
    /* Can't create an array of the generic type E directly.
     * So that's why there's the unchecked cast.
     */
    buf = (E[]) new Object[size];
    System.arraycopy(buf, 0, b.buf, 0, size);
  }

  /**
   * Constructor
   *
   * @param buf_size buffer size
   */
  @SuppressWarnings("unchecked")
  public BufferT(int buf_size) {
    if (buf_size == 0) {
      throw new RuntimeException("Error: Cannot create empty buffer.");
    }
    /*
     * Unchecked cast because E[] can't be created directly.
     */
    buf = (E[]) new Object[buf_size];
    size = buf_size;
    currentpos = 0;
    lastpos = 0;
  }

  /**
   * Copy constructor.
   */
  public BufferT(BufferT<E> b) {
    copy(b);
  }

  /**
   * Add an element to the buffer.
   *
   * @param value the value.
   */
  public E add(E value) {
    if (lastpos == size) {
      lastpos = 0;
    }
    buf[lastpos++] = value;
    currentpos = lastpos;
    return buf[lastpos - 1];
  }

  /**
   * Consume the buffer's current value.
   *
   * @return the current value.
   */
  public E next() {
    if (currentpos != lastpos) {
      if (currentpos == size) {
        currentpos = 0;
      }
      return buf[currentpos++];
    } else {
      return last();
    }
  }

  /**
   * Get the last element of the buffer.
   *
   * @return last element.
   */
  public E last() {
    if (lastpos != 0) {
      return buf[lastpos - 1];
    } else {
      return buf[size - 1];
    }
  }

  /**
   * Get the current buffer position.
   *
   * @return the position.
   */
  public int getPos() {
    return currentpos;
  }

  /**
   * Set the buffer to a new position.
   *
   * @param newpos the new position.
   */
  public void setPos(int newpos) {
    currentpos = newpos;
  }

  /**
   * Return the range size between the buffer current position and a
   * outside stored given position that is previous to the current.
   *
   * @param prevpos the given position.
   * @return the range size.
   */
  public int diffPrevPos(int prevpos) {
    if (prevpos <= currentpos) {
      return currentpos - prevpos;
    } else {
      return currentpos + size - prevpos;
    }
  }

  /**
   * Return the range size between the buffer current position and a
   * outside stored given position that is following to the current
   *
   * @param postpos the given position.
   * @return the range size.
   */
  public int diffPostPos(int postpos) {
    if (postpos >= currentpos) {
      return postpos - currentpos;
    } else {
      return postpos + size - currentpos;
    }
  }

  /**
   * Checks the buffer for emptyness.
   *
   * @return true if the buffer is empty.
   */
  public boolean isEmpty() {
    return currentpos == lastpos;
  }

  /**
   * Gets back 'posback' positions in the buffer.
   *
   * @param posback the amount of position to get back.
   */
  public void back(int posback) {
    if (currentpos > posback) {
      currentpos -= posback;
    } else {
      currentpos = size - (posback - currentpos);
    }
  }

  /**
   * Computes a string representation of the buffer
   *
   * @return the string representation of the buffer
   */
  @Override
  public String toString() {
    String res = new String("content : ");
    for (int i = 0; i < buf.length; i++) {
      res += buf[i];
    }
    res += ", lastpos : " + lastpos + ", currentpos : " + currentpos;
    return res;
  }
}
