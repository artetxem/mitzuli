package org.apertium.lttoolbox;

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
 * Created by Nic Cottrell, Jan 27, 2009 6:05:38 PM
 */
public class Pair<P, Q> {
  public P first;
  public Q second;

  public Pair(P obj1, Q obj2) {
    this.first = obj1;
    this.second = obj2;
  }

  public P getFirst() {
    return first;
  }

  public void setFirst(P first) {
    this.first = first;
  }

  public Q getSecond() {
    return second;
  }

  public void setSecond(Q second) {
    this.second = second;
  }

  @Override
  public String toString() {
    return "<" + first.toString() + "," + second.toString() + ">";
  }
}
