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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * Alphabet class.
 * Encodes pairs of symbols into an integer.
 *
 * @author Raah
 */
public class Alphabet {
  /**
   * Class to represent a pair of integers
   *
   * @author Raah
   */
  public static class IntegerPair {
    public int first;
    public int second;

    public IntegerPair(Integer i1, Integer i2) {
      first = i1;
      second = i2;
    }

    @Override
    public int hashCode() {
      return first + second * 0x8000;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (o instanceof IntegerPair) {
        IntegerPair p = (IntegerPair) o;
        return ((first == p.first) && (second == p.second));
      } else
        return false;
    }

    @Override
    public String toString() {
      return ("<" + (first > 0 ? "" + (char) first : first) + "," + (second > 0 ? "" + (char) second : second) + ">");
    }
  }

  /**
   * Tag -> symbol mapping
   * example: {<vblex>=-1, <pp>=-2, <n>=-3, <sg>=-4, <pl>=-5}
   */
  private final HashMap<String, Integer> slexic;
  /**
   * Symbol -> tag mapping. Note that symbols are represented as negative integers, så really the mapping is slexicinv.get(-symbol - 1);
   * example: [<vblex>, <pp>, <n>, <sg>, <pl>]
   */
  private final ArrayList<String> slexicinv;
  /**
   * Pair index -> pair object mapping. SHOULD NOT BE ACCESSED OUTSIDE CompileAlphabet.
   * example [<0,0>, <w,w>, <o,o>, <u,u>, <n,n>, <d,d>, <0,-3>, <0,-4>, <s,-3>, <0,-5>, <o,i>, <u,n>, <n,d>, <d,-1>, <0,-2>]
   * for testdata/bilingual/apertium-eo-en.eo-en.dix :
   * [<0,0>, < , >, <!,!>, <",">, <#,#>, <$,$>, <%,%>, <&,&>, <','>, <(,(>, <),)>, <*,*>, <+,+>, <,,,>, <-,->, <.,.>, <A,A>, <B,B>, <C,C>, <D,D>, <E,E>, <F,F>, <G,G>, <H,H>, <I,I>, <J,J>, <K,K>, <L,L>, <M,M>, <N,N>, <O,O>, <P,P>, <Q,Q>, <R,R>, <S,S>, <T,T>, <U,U>, <V,V>, <W,W>, <X,X>, <Y,Y>, <Z,Z>, <a,a>, <b,b>, <c,c>, <d,d>, <e,e>, <f,f>, <g,g>, <h,h>, <i,i>, <j,j>, <k,k>, <l,l>, <m,m>, <n,n>, <o,o>, <p,p>, <q,q>, <r,r>, <s,s>, <t,t>, <u,u>, <v,v>, <w,w>, <x,x>, <y,y>, <z,z>, <À,À>, <Á,Á>, <Â,Â>, <Ä,Ä>, <Å,Å>, <Æ,Æ>, <Ç,Ç>, <È,È>, <É,É>, <Ê,Ê>, <Ë,Ë>, <Ì,Ì>, <Í,Í>, <Î,Î>, <Ï,Ï>, <Ñ,Ñ>, <Ò,Ò>, <Ó,Ó>, <Ô,Ô>, <Ö,Ö>, <Ø,Ø>, <Ù,Ù>, <Ú,Ú>, <Û,Û>, <Ü,Ü>, <à,à>, <á,á>, <â,â>, <ã,ã>, <ä,ä>, <å,å>, <æ,æ>, <ç,ç>, <è,è>, <é,é>, <ê,ê>, <ë,ë>, <ì,ì>, <í,í>, <î,î>, <ï,ï>, <ñ,ñ>, <ò,ò>, <ó,ó>, <ô,ô>, <õ,õ>, <ö,ö>, <ø,ø>, <ù,ù>, <ú,ú>, <û,û>, <ü,ü>, <Ĉ,Ĉ>, <ĉ,ĉ>, <Ĝ,Ĝ>, <ĝ,ĝ>, <Ĥ,Ĥ>, <ĥ,ĥ>, <Ĵ,Ĵ>, <ĵ,ĵ>, <Ŝ,Ŝ>, <ŝ,ŝ>, <Ŭ,Ŭ>, <ŭ,ŭ>, <ŵ,ŵ>, <-81,-81>, <-30,-30>, <b,e>, <e,b>, <#,l>, < ,i>, <p,-16>, <o,0>, <s,0>, <i,0>, <b,0>, <l,0>, <e,0>, <-33,0>, <b,n>, <#, >, < ,e>, <n,b>, <o,l>, <t,i>, < ,-16>, <p,0>, <b,f>, <e,i>, <#,n>, <o,-16>, <v,0>, <r,0>, <t,e>, <h,s>, <e,t>, <r,i>, <e,-33>, < ,0>, <-16,0>, <c,p>, <a,o>, <n,v>, <-21,i>, <0,-21>, <w,v>, <i,o>, <l,i>, <-21,-21>, <u,l>, <d,-21>, <-21,0>, <m,p>, <y,v>, <s,d>, <h,e>, <a,v>, <l,-21>, <-21,-13>, <-26,0>, <o,v>, <u,i>, <d,-54>, <m,d>, <u,e>, <s,v>, <-26,-29>, <d,0>, <m,e>, <i,b>, <g,l>, <h,i>, <t,-21>, <h,d>, <a,e>, <#,-21>, <t,0>, <e,s>, <-33,t>, <0,i>, <0,-33>, <o,s>, < ,t>, <b,i>, <d,f>, <o,a>, <-16,r>, <0,-16>, <-16,-16>, <-36,r>, <-50,-16>, <f,p>, <l,e>, <l,n>, <n,o>, <e,-68>, <-68,0>, <e,m>, <m,o>, <s,t>, <b,m>, <j,o>, <c,0>, <o,e>, <p,m>, <c,-68>, <s,a>, <u,b>, <b,o>, <s,n>, <c,o>, <r,-68>, <n,0>, <3,3>, <-68,-68>, <-93,-93>, <c,k>, <l,-49>, <-49,0>, <P,p>, <i,a>, <a,-49>, <a,n>, <m,t>, <u,r>, <l,a>, <t,n>, <i,s>, <t,c>, <n,-49>, <a,0>, <b,k>, <r,s>, <o,t>, <d,r>, <-63,l>, <0,a>, <0,n>, <0,d>, <0,e>, <0,-63>, <s,p>, <o,i>, <n, >, <e,f>, <-63,u>, <w,l>, <a,t>, <d,o>, <y,a>, <-49,-49>, <-1,-1>, <:,:>, </,/>, <0,0>, <1,1>, <2,2>, <4,4>, <5,5>, <6,6>, <7,7>, <8,8>, <9,9>, <_,_>, <n,u>, < ,n>, <M,d>, <n,-63>, <y,0>, <-63,0>, <o,m>, <n,a>, < ,r>, <T,d>, <e,-63>, <n,e>, <W,k>, <e,r>, <d,e>, <n,d>, <s,-63>, <o,ĵ>, < ,ŭ>, <u,-63>, <F,d>, <i,e>, <y,-63>, < ,b>, <S,a>, <o,d>, <n,i>, < ,m>, <u,n>, <n,ĉ>, <a,-63>, <f,e>, <o,k>, <r,z>, <i,m>, <n,p>, <s,l>, <a,-75>, <-75,0>, <i,t>, <-75,-75>, <.,k>, <g,z>, <t,o>, <m,l>, <s,-75>, <d,t>, < ,a>, <b, >, <y,p>, <-75,e>, <0,r>, <0,-75>, <h,t>, <a,r>, <d,n>, <#,s>, < ,d>, <v,n>, <r,-16>, <-86,0>, <k,a>, <e,n>, < ,p>, <o,r>, <v,e>, <r,o>, < ,v>, <d,k>, <o,j>, <v, >, <e,d>, <r,e>, <-63,n>, <0,o>, <0,v>, <#,-16>, <P,0>, <R,0>, <N,0>, <u,0>, <n,-16>, <g,v>, <t,k>, <#,i>, <P,-16>, <#,ĝ>, <u,-16>, < ,ĝ>, <w,e>, <a,k>, <k,i>, <e,ĝ>, <-16,i>, <g,r>, <t,a>, <#,k>, <P,i>, <R,r>, <N,i>, <k,0>, <t,v>, <#,e>, <a,-16>, <s,k>, <e,a>, <l,p>, <#,t>, < ,u>, <u,a>, <-86,-16>, <s,f>, <e,o>, <l,r>, <l,v>, <u,d>, <p,i>, <#,u>, <c, >, <k,e>, < ,l>, <x,k>, <-9,s>, <-80,o>, <0,-9>, <0,-80>, <C,K>, <T,t>, <V,e>, <-68,l>, <0,-68>, <-73,-73>, <-38,-38>, <;,;>, <?,?>, <-17,-17>, <[,[>, <-37,-37>, <],]>, <-28,-28>, <-18,-18>, <-5,-5>, <z,n>, <e,u>, <r,l>, <o,u>, <t,d>, <w,u>, <o,-81>, <-81,0>, <h,r>, <e,-81>, <f,k>, <i,v>, <v,i>, <x,s>, <v,p>, <i,k>, <g,-81>, <h,0>, <n,ŭ>, <n,k>, <e,k>, <-81,u>, <0,-81>, <l, >, <v,d>, <r, >, <n,-81>, <f,d>, <u,k>, <e,v>, <n,r>, <t, >, <-81,n>, <n,s>, <v,k>, <e, >, <e,p>, <g,k>, <h, >, <e,ŭ>, <y,-81>, <r,d>, <y,k>, <r,a>, <t,r>, <y,d>, <-81,e>, <0,k>, <f,i>, <y,e>, <-81,k>, <g,d>, <h,c>, <r,-81>, <o, >, < ,c>, <n,t>, <d,-81>, <e,c>, <h,n>, <u,t>, <n,c>, <g, >, <u,-81>, <t,m>, <h,l>, <t,l>, <h,-81>, <n,m>, <h,m>, <t,-81>, <o,n>, <a,m>, <l,o>, <m,i>, <i,l>, <i,n>, <n,j>, <-81,j>, <i,j>, <-81,o>, <b,l>, <i,d>, <-68,o>, <y, >, <-,u>, <-,d>, <t,u>, <w,-81>, <-,t>, <-,k>, <f,v>, <-,s>, <s,e>, <x,-81>, <v,-81>, <-,o>, <i,-81>, <g,0>, <-,n>, <i,ŭ>, <-, >, <-,e>, <0,u>, <w, >, <r,t>, <r,v>, <-81,a>, <i, >, <-81,i>, <0,s>, <v,s>, <g,o>, <h,k>, <-81,ŭ>, <w,d>, <f, >, <u,v>, <-81,r>, <s, >, <x,e>, <-81,s>, < ,ĉ>, <f,u>, < ,j>, <u,-87>, <s,-3>, <-87,0>, <-3,0>, <y,-87>, <o,-3>, <a,i>, <o,ĉ>, <t,j>, <h,-87>, <e,-3>, <m,0>, <e,ĉ>, <r,-87>, <y,-3>, <c,u>, <-87,-3>, <-87,p>, <-3,a>, <-41,r>, <0,t>, <0,-87>, <0,-3>, <0,-30>, <n,l>, <r,-30>, <-30,0>, <a,p>, <-87,k>, <b,a>, <t,b>, <h,a>, <-87,ŭ>, <-3,-87>, <-87,u>, <a,ĉ>, <l,u>, <-87,-87>, <-3,-3>, <-24,-41>, <-7,j>, <0,-7>, <-87,a>, <-41,-3>, <0,-41>, <e,-87>, <w,k>, <v,j>, <v,a>, <e,j>, <r,n>, <-3,j>, <h,j>, <n,-87>, <g,-3>, <s,i>, <m,-87>, <w,0>, <w,n>, <-87,-42>, <-42,0>, <-42,-42>, <i,u>, <c,-87>, <h,-42>, <t,-87>, <w,o>, <u,-42>, <m, >, <a,d>, <e,l>, <-87,i>, <-42,-87>, <0,-42>, <m,-46>, <-46,0>, <y,-46>, <-46,-42>, <d,u>, <-3,-30>, <a,u>, <t,-91>, <-91,-84>, <-84,0>, <-87,ĉ>, <-3,i>, <-30,-87>, <t,ĉ>, <-3,u>, <y,t>, <-63,-87>, <0,-95>, <v,u>, <a,j>, <l,-87>, <s,u>, <h,-84>, <a,-41>, <-91,0>, <-12,0>, <-60,0>, <-41,0>, <w,-91>, <o,-41>, <-91,-91>, <y,i>, <s,-87>, <e,-79>, <l,-10>, <f,-60>, <-87,-30>, <-79,0>, <-10,0>, <r,-79>, <s,-8>, <e,-60>, <l,-30>, <f,0>, <-8,0>, <s,-79>, <e,-6>, <l,-72>, <f,-30>, <-6,0>, <-72,0>, <l,-65>, <-65,0>, <l,-6>, <f,-4>, <-4,0>, <e,-10>, <l,-60>, <v,-41>, <l,-41>, <t,s>, <m,-79>, <s,-6>, <s,m>, <l,m>, <f,-87>, <-87,-79>, <-53,-10>, <-10,-60>, <-60,-30>, <-53,-8>, <-8,-60>, <-53,-6>, <-6,-65>, <-65,-30>, <-6,-72>, <-72,-30>, <-6,-4>, <-4,-30>, <-60,-41>, <-6,-60>, <-60,-24>, <-24,0>, <-88,-10>, <-88,-8>, <-88,-6>, <b,ĉ>, <c,r>, <h,u>, <l,-75>, <r,-75>, <u,ĝ>, <i,-75>, <0,l>, <c,d>, <o,-75>, <g,m>, <h,-75>, <h,v>, <-75,m>, <u,j>, <-75,t>, <y,s>, <-75, >, <0,m>, <t,-75>, <f,o>, <-75,k>, <f,t>, <r,ŭ>, <-75,o>, <i,r>, <-75,i>, <0, >, <d,p>, < ,o>, < ,-75>, <p,s>, <w,ĉ>, <e,-75>, <f,-75>, <w,m>, <l,k>, <a,l>, <g,a>, <d,a>, <b,ŭ>, <y,-69>, <-69,0>, <g,b>, <-69,n>, <0,-69>, <s,ĝ>, <u,s>, <a,-69>, <y,r>, <-69,i>, <-69,o>, <a,-32>, <-32,0>, <d,j>, <-62,-62>, <-62,ŭ>, <0,-62>, <b,s>, <a,-62>, <-62,0>, <t,-62>, <r,k>, <r,p>, <h,f>, <-62,e>, <e,-46>, <-46,-56>, <-56,0>, <-46,e>, <-19,t>, <0,_>, <0,-46>, <0,-19>, <p,-46>, <l,-59>, <-59,0>, <-46,p>, <-59,a>, <0,#>, <0,-59>, <#,p>, <-46,o>, <-59,#>, <y,n>, <-46,a>, <-19,-46>, <r,-19>, <-19,0>, <w,a>, <v,-19>, <h,-46>, <e,-19>, <r,-46>, <-46,-19>, <o,-49>, <-49,f>, <0,-49>, <a,-68>, <-68,f>, <o,-68>, <-49,a>, <r,u>, <t,-49>, <c,a>, <d,-49>, <h,-49>, <l,d>, <t,p>, <e,-49>, <h,o>, <s,-49>, <-63,e>, <t,-63>, <c,e>, <o,-63>, <d,-63>, <h,-63>, <-63,-63>, <g,e>, <-63,p>, <-46,ŭ>, <-19,-30>, <y,-19>, <-46,-30>, <-59,-46>, <-24,-59>, <-30,-59>, <-41,-59>, <f,l>, <-46,-46>, <-59,-59>, <-46,t>, <-30,-46>, <f,m>, <-46,m>, <-59,u>, <-41,l>, <c,l>, <-59, >, <-30,d>, <f,a>, < ,-46>, <o,-59>, <-46,l>, <-59,i>, <-46,i>, <o,-46>, <f,-59>, <p,b>, <-46,n>, <-19,i>, <-46,d>, <t,-46>, <-46,-61>, <-61,0>, <-61,u>, <0,-61>, <c,-46>, <h,-59>, <n,-46>, <y,-59>, <-46,-41>, <u, >, <a, >, <y,u>, <-59,t>, <c,m>, <m,-59>, <o,f>, <g,ĉ>, <-46, >, <-59,d>, <q,s>, <i,f>, <t,-59>, <-15,-46>, <0,-15>, <-15,-15>, <-46,-15>, <-15,0>, <h,ŝ>, <i,ĝ>, <r,-49>, <',a>, <s,-46>, <v,t>, <y,-20>, <-20,0>, <-20,-20>, <l,-20>, <e,-20>, <-20,e>, <0,-20>, <j,t>, < ,-20>, <-20,m>, <-20,a>, <0,j>, <-94,r>, <0,-94>, <-63,r>, <n,-94>, <_,0>, <-94,0>, <-94,-94>, <o,-94>, <e,-94>, <-94,a>, <0,ŭ>, <w,s>, <h,-94>, <d,b>, <a,s>, <b,u>, <o,p>, <b,r>, <u,-94>, <d,-94>, <t,ŭ>, <i,-94>, <-94,e>, <s,-94>, <t,-94>, <p,l>, <w,-94>, <c,ĉ>, <-63,ŭ>, <d,ŭ>, <r,ĉ>, <-94,ŭ>, <',d>, <-23,-23>, <-23,e>, <0,-23>, <b,d>, <f,-94>, <r,m>, <x,r>, <p,-94>, <-94,m>, <m,n>, <r,-94>, <c,ŭ>, <b,p>, <a,-94>, < ,-94>, <c,n>, <g,n>, <a,c>, <s,o>, <r,c>, <d, >, <-94,l>, <f,c>, <-94,n>, <c,f>, <p,e>, <f,n>, <-94,t>, < ,s>, <x,-94>, <p,r>, < ,k>, <u,m>, <s,g>, <m,-94>, <-94,d>, <i,p>, <p,t>, <d,l>, <w,r>, <W,V>, <-9,-9>, <-80,-80>, <N,A>, <L,N>, <b,c>, <t,L>, <o,b>, <A,i>, <r,ĝ>, <y,-9>, <-9,-80>, <-80,0>, <s,-9>, <t,-27>, <-9,0>, <-27,0>, <-27,-27>, <-9,o>, <-27,-9>, <0,-27>, <g,ĝ>, <-9,-27>, <s,j>, <c,z>, <t,A>, <A,j>, <n,-9>, <d,-27>, <a,-9>, <E,A>, <d,-9>, <u,ŭ>, <C,Ĉ>, <p,f>, <a,b>, <y,o>, <s,z>, <I,B>, <u,o>, <m,-9>, <s,r>, <A,-9>, <i,-27>, <z,c>, <U,B>, < ,-9>, <K,-27>, <t,B>, <U,i>, <i,-9>, <K,0>, <G,B>, <B,-9>, <r,-27>, <s,ŝ>, <e,ŝ>, <d,i>, <r,-9>, <k,-27>, <-9,i>, <-27,o>, <P,F>, <p,n>, <e,-9>, <s,-27>, <t,F>, <p,-9>, <p,-27>, <-9,d>, <c,g>, <c,i>, <G,Ĝ>, <S,H>, <-9,n>, <-27,i>, <c,s>, <Y,J>, <I,j>, <l,-27>, <n,-27>, <a,g>, <G,K>, <a,->, <n,L>, <L,n>, <o,-9>, <Q,K>, < ,->, <h,->, < ,A>, <A,m>, <t,->, <h,A>, <A,e>, <m,r>, <c,-9>, <a,-27>, <l,A>, <H,N>, <-27,d>, <t,N>, <N,r>, <l,-9>, <y,g>, <y,j>, <-22,-9>, <0,-22>, <i,->, <A,r>, <W,K>, <z,e>, <e,ĥ>, <R,-27>, <k,o>, <k,h>, < ,f>, <A,f>, <f,r>, <E,O>, <A,t>, <f,->, <r,A>, <-9,k>, <W,O>, <s,c>, <c,A>, <a,f>, <-9,r>, <z,l>, < ,-27>, <S,-30>, <t,U>, <U,o>, <t,-30>, <S,0>, <A,0>, <n,g>, <g,t>, <c,t>, <i,z>, <i,g>, <J,Ĵ>, < ,K>, <C,a>, < ,D>, <D,a>, <y,l>, <d,->, <e,I>, <I,s>, <h,K>, <C,r>, <h,D>, <D,k>, <k,t>, <t,-9>, <e,-27>, <n,V>, <-9,g>, <L,M>, <S,r>, <x, >, <o,S>, <c,ŝ>, <g,j>, <J,j>, <F,f>, <M,m>, <A,a>, <S,s>, <O,o>, <N,n>, <D,d>, <M,l>, <y,-68>, <T,m>, <W,m>, <T,ĵ>, <d,-68>, <F,v>, <-68,d>, <S,d>, <y,ĉ>, <C,k>, <G,g>, <G,ĝ>, <c,-49>, <i,-49>, <d,h>, <-9,-22>, <-22,0>, <B,b>, <H,h>, <E,o>, <W,o>, <-49,i>, <E,a>, <A,u>, <m,s>, <E,-49>, <h,g>, <o,->, <-,-49>, <I,i>, <n,-68>, < ,z>, <s,->, <a,z>, <s,-68>, <C,ĉ>, <D,n>, <-49,l>, <0,-72>, <-68,n>, <0,-65>, <E,e>, <A,l>, <k,f>, <0,g>, <P,f>, <i,-68>, <P,k>, <-49,g>, <-68,g>, <S,h>, <-49,d>, <M,i>, <m,a>, <L,l>, <-68,a>, <U,-9>, <k,r>, <K,k>, <C,c>, <V,v>, <Y,j>, <h,-68>, <G,k>, <j,ĝ>, <k,ĥ>, <U,u>, <q,k>, <a,ŝ>, <-68,i>, <-68,-65>, <o,ŭ>, <w,-68>, <o,-65>, <h,-65>, <C,l>, <S,l>, <p,a>, <n,h>, <h,p>, <-63,a>, <t,h>, <l,s>, <S,-63>, <P,l>, <e,g>, <O,l>, <-63,t>, <G,l>, <-63,k>, <v,o>, <ç,i>, <F,l>, <n,f>, <0,c>, <I,l>, <u,f>, <d,s>, <R,l>, <0,ĉ>, <E,l>, <E,g>, <l,-63>, <B,l>, <E,n>, <i,-63>, <l,t>, <-63,o>, <m,g>, <-63,m>, <D,l>, <-63,d>, <m,f>, < ,-63>, <G,0>, <W,l>, <l,g>, <q,e>, <N,l>, <v,l>, <H,l>, <g,h>, <l,b>, <U,l>, <z,a>, <-63,ĥ>, <-63,v>, <r,b>, <T,l>, <K,l>, <-63,b>, <z, >, <r,h>, <w,b>, <e,-65>, <-68,-72>, <m,-68>, <a,-65>, <k,p>, <s,-72>, <d,-65>, <d,-72>, <r,-72>, <t,-65>, <p,v>, <w,-72>, <-68,b>, <n,ĝ>, <q,r>, <s,-65>, <-68,t>, <p,d>, <-68,-45>, <0,-45>, <-68,r>, <g,s>, <n,-72>, <-68,j>, <n,-65>, <p,-68>, <p,g>, <p,u>, < ,-68>, <c,b>, <-68,s>, <a,h>, <t,-68>, <t,-69>, <k, >, <o,-69>, <k,ĉ>, <k,n>, <J, >, <l,J>, <-69, >, <0,b>, <g,u>, <l,-69>, <m,j>, <g,i>, <k,b>, <b,g>, <-49,n>, <-63, >, <M,s>, <r,->, <-22,-22>, <-60,-60>, <M,f>, <-81,-48>, <-48,0>, <A,P>, <D,.>, <-81,K>, <0,.>, <0,K>, <-68,K>, <-30,.>, <A,p>, <-63,K>, <B,a>, <C,.>, <E,K>, <-63,E>, <B,K>, <C,E>, <E,-63>, <c,-94>, <p,.>, <p,-63>, <x,0>, <.,0>, <-63,s>, <w,c>, <y,-49>, <d,m>, <W,a>, <I,n>, <W, >, <I,d>, <I,m>, <r,f>, <0,ĝ>, <-68,e>, <-16,e>, <w,t>, <-49,e>, <h,ĉ>, <-63,i>, <x,o>, <k,d>, <0,p>, <v,ĉ>, <f,ĵ>, <r,ĵ>, <r,-91>, <w,i>, <c,-91>, <d,ĉ>, <r,-63>, <e,-42>, <-42,-63>, <n,->, <-,-63>, <j,n>, <f,s>, <v,-63>, <m,u>, <r,j>, <a,ŭ>, <h,ŭ>, <y,ŭ>, <y,h>, <z,t>, <b,t>, <-49,ĝ>, <w,-63>, <-68,u>, <#,a>, <#,r>, <o,ĝ>, <t,-16>, <c,v>, <p,z>, <g,p>, <b,-68>, <w,g>, <l,-68>, <-68,k>, <o,g>, <u,p>, < ,g>, <#,ŭ>, <u,g>, <r,ŝ>, <f,-16>, <t,f>, <#,j>, <d,-16>, <#,0>, <p,k>, <#,o>, <t,g>, <g,-16>, <-16,a>, <i,-16>, <k,#>, <e,z>, <#,g>, <c,-16>, <y,-16>, <#,v>, <m,v>, <b,-16>, <h,-16>, <l,-16>, <w,-16>, <s,-16>, <b,v>, <q,0>, <a,#>, <#,m>, <s,ŭ>, <s,b>, <-,0>, <m,-63>, <i,ĉ>, <j,ĉ>, <n,z>, <c,-63>, <d,g>, <x,p>, <I,a>, <l,c>, <l,f>, <j,p>, <l,h>, <j,0>, <w,p>, < ,ŝ>, <u,z>, <o,ŝ>, <t,z>, <-16,n>, <é,o>, <p,o>, <0,ĥ>, <-68,ĥ>, <s,ĥ>, <k,-68>, <.,n>, <a,-46>, <l,-19>, <-19,-19>, <g,ŭ>, <l,ŭ>, <-94,o>, <-94,j>, <g,-94>, <-63,->, <0,f>, <m,-91>, <f,j>, <f,h>, <m,ĉ>, <m,c>, <-63,c>, <f,-63>, <-75,n>, <-75,ŭ>, <g,-75>, <s,h>, <z,g>, <g,-68>, <A,o>, <S,-9>, <A,-27>, <U,S>, <S,o>, <S,v>, <R,e>, <-9,t>, <-27,->, <0,U>, <t,S>, <U,t>, <S,->, <S,U>, <R,n>, <K,r>, <-27,t>, <K,o>, <-27,n>, <V,A>, <A,V>, <T,I>, <-93,o>, <0,-93>, <u,ĉ>, <',0>, <é,0>, <â,0>, <-9,j>, <t,P>, <P,n>, <k,j>, <t,M>, <M,t>, <',ŭ>, <p,h>, <z,i>, <a,ĝ>, <-16,t>, <-16,g>, <-16,k>, <m,-16>, <c,ĝ>, <u,c>, <y,c>, <e,-16>, <q,n>, <q,i>, <-16,p>, <-16,o>, <v,ĵ>, <j,u>, <d,v>, <-49,r>, <r,g>, <-49,t>, <-16,j>, <m,k>, <-68,p>, <o,h>, <k,l>, <l,j>, <-16,s>, <-68,z>, <g,-63>, <u,-49>, <h,z>, <C, >, <C,e>, <C,t>, <0,ĵ>, <-68,m>, <-,-68>, <x,l>, <p,j>, <p,c>, <i,ĵ>, <x,i>, <-68,ĵ>, <k,s>, <k,m>, <m,b>, <o,c>, <m,z>, <m,ŭ>, <y,ĵ>, <k,-16>, <0,z>, <-68,c>, <v,r>, <-49,u>, <x,a>, <k,u>, <-49,b>, <m,ĝ>, <-16,c>, <u,-68>, <f,-68>, <v,-16>, <g,c>, <b,z>, <l,ĵ>, <-68,v>, <-16,v>, <k,c>, <-16,u>, <-68,ĝ>, <d,ĝ>, <c,j>, <z,o>, <t,ŝ>, <d,ŝ>, <b,ĝ>, <o,z>, <-16,l>, <t,ĝ>, <l,ĝ>, <-49,m>, <b,ŝ>, <k,ĝ>, <y,m>, <-16,m>, <z,r>, <i,ŝ>, <c,ĵ>, <-16,z>, <t,ĵ>, <e,h>, <q,o>, <s,ĵ>, <-,v>, <p,-49>, <i,c>, <c,ĥ>, <s,ĉ>, <k,z>, <-68,ĉ>, <c,h>, <w,ŭ>, <q,v>, <-16,ŭ>, <b,-49>, <b,-63>, <d,c>, <-49,s>, <n,ŝ>, <m,ĵ>, <q,-68>, <v,g>, <v,m>, <-49,o>, <l,z>, <-49,z>, <v,b>, <-16,ĝ>, <-63,g>, <v,-49>, <-16,d>, <g,f>, <a,ĵ>, <y,ĝ>, <d,z>, <z,k>, <p,ĵ>, <y,z>, <j,i>, <q,g>, <x,u>, <x,n>, <x,f>, <x,d>, <x,t>, <f,g>, <-49,c>, <f,ŝ>, <f,b>, <f,ĉ>, <u,ĵ>, <v,-68>, <w,f>, <g,-49>, <y,ŝ>, <w,ĵ>, <f,-49>, <m,-49>, <l,ŝ>, <-,i>, <k,-49>, <q,a>, <b,ĵ>, <j,-27>, <-,l>, <S, >, <l,Ŝ>, <z,u>, <h,b>, <h,ĝ>, <g,ĵ>, <-63,ĝ>, <e,ĵ>, <K,u>, <u,ŝ>, <h,-9>, <i,h>, <j,l>, <j,b>, <-,g>, <j,ĵ>, <j,a>, <j,k>, <j,ŝ>, <j,v>, <j,s>, <k,ŝ>, <k,g>, <w,ĝ>, <w,z>, <y,b>, <n,ĵ>, <d,ĵ>, <q,u>, <-30,-68>, <-41,e>, <x,c>, <m,h>, <w,j>, <-49,v>, <x,m>, <z,ŝ>, <p,ŝ>, <v,ĝ>, <-94,s>, <0,ŝ>, <q,d>, <-49,p>, <f,z>, <x,-68>, <-29,-16>, <0,-11>, <-11,-16>, <l,ĉ>, <',-49>, <p,ĝ>, <p,ĉ>, <v,h>, <z,m>, <b,j>, <f,ĝ>, <-49,k>, <q,p>, <q,c>, <e,->, <q,t>, <-68,ŝ>, <-16,ĉ>, <-,c>, <-16,b>, <z,-16>, <-,a>, <j,m>, <q,-63>, <-16,ŝ>, <-68,h>, <v,f>, <v,ŝ>, <-,r>, <w,h>, <-41,o>, <y,f>, <w,-49>, <k,ŭ>, <-68,ŭ>, <',-68>, <v,c>, <-68, >, <ç,s>, <n,-30>, <â,a>, <é,t>, <è,e>, <-,f>, <-41,-41>, <s,-41>, <q,ŝ>, <j,d>, <-49,ĉ>, <ï,i>, <z,-49>, <-16,f>, <-63,j>, <z,-63>, <z,-68>, <v,z>, <g,ŝ>, <-63,z>, <x,g>, <b,h>, <h,ĵ>, <-,z>, <c,->, <q,-49>, <x,v>, <-49,h>, <-16,h>, <k,v>, <w,ŝ>, <z,v>, <q,m>, <-49,ŝ>, <z,p>, <f,ŭ>, <z,d>, <z,b>, <q,j>, <-,p>, <j,e>, <j,g>, <J,I>, <-9,b>, <j,r>, <L,S>, <0,h>, <z,0>, <f,R>, <e,-41>, <-,m>, <k,ĵ>, <q,l>, <p,ŭ>, <p,->, <-9,a>, <-63,ŝ>, <m,ŝ>, <-49,->, <z,f>, <z,s>, <ú,u>, <-72,-72>, <X,K>, <-65,-65>, <',e>, <',-63>, <x,-63>, <j,ŭ>, <m,->, <h,ĥ>, <u,h>, <j,f>, <j,h>, <Z,e>, <k,-63>, <-68,->, <q,ĉ>, <q,f>, <-49,ĵ>, <q,ŭ>, <-,ŭ>, <x,z>, <m,ĥ>, <O,0>]
   */
  protected final ArrayList<IntegerPair> spairinv;

  /**
   * The constructor
   */
  public Alphabet() {
    slexic = new HashMap<String, Integer>();
    slexicinv = new ArrayList<String>();
    spairinv = new ArrayList<IntegerPair>();
  }

  /**
   * Copy constructor, creates a new copy of Alphabet based on the passed-in
   * Alphabet object.
   *
   * @param o - The Alphabet object to copy.
   */
  public Alphabet(Alphabet o) {
    slexic = new HashMap<String, Integer>(o.slexic);
    slexicinv = new ArrayList<String>(o.slexicinv);
//        spair = new HashMap<IntegerPair, Integer>(o.spair);
    spairinv = new ArrayList<IntegerPair>(o.spairinv);
  }

  /**
   * Include a symbol into the alphabet.
   *
   * @param s the symbol to include
   */
  public void includeSymbol(String s) {
    if (!slexic.containsKey(s)) {
      int slexic_size = slexic.size();
      slexic.put(s, -(slexic_size + 1));
      slexicinv.add(s);
    }
  }

  /**
   * Gets the individual symbol identifier.
   *
   * @param s symbol to be identified.
   * @return the symbol identifier.
   */
  public int cast(String s) {

    //System.err.println(this+" "+slexic+"cast s = " + s);
    Integer i = slexic.get(s);

    if (i == null)
      return 0; // unknown symbol - this happens in transfer

    return (int) i;
  }

  /**
   * Check wether the symbol is defined in the alphabet.
   *
   * @param s the symbol to check.
   * @return true if the symbol is defined.
   */
  public boolean isSymbolDefined(String s) {
    return slexic.containsKey(s);
  }

  /**
   * Give the size of the alphabet.
   *
   * @return the number of symbols of the alphabet.
   */
  public int size() {
    return slexic.size();
  }

  /**
   * Write the alphabet to a stream.
   *
   * @param output the outputstream.
   * @throws java.io.IOException.
   */
  public void write(OutputStream output) throws IOException {
    // First, we write the taglist
    Compression.multibyte_write(slexicinv.size(), output);

    for (int i = 0, limit = slexicinv.size(); i < limit; i++) {
      Compression.String_write(slexicinv.get(i).substring(1, 1 + slexicinv.get(i).length() - 2), output);
    }

    // Then we write the list of pairs
    // All numbers are biased + slexicinv.size() to be positive or zero
    int bias = slexicinv.size();

    Compression.multibyte_write(spairinv.size(), output);
    for (int i = 0, limit = spairinv.size(); i != limit; i++) {
      Compression.multibyte_write(spairinv.get(i).first + bias, output);
      Compression.multibyte_write(spairinv.get(i).second + bias, output);
    }
  }

  /**
   * Read an alphabet from an input stream. The alphabet is the symbols (<sdef n="n" /> in the .dix file)
   *
   * @param input the stream to read from
   * @return the alphabet read from th input
   * @throws java.io.IOException
   */
  public static Alphabet read(InputStream input) throws IOException {
    Alphabet a_new = new Alphabet();

    // the following examples and numbers corresponds to testdata/wound-example.dix
    // Reading of taglist.
    int tam = Compression.multibyte_read(input); // 5 tags  (95 for eo-en.dix)
    while (tam > 0) {
      tam--;
      String mytag = "<" + Compression.String_read(input) + ">";
      a_new.slexicinv.add(mytag);
      a_new.slexic.put(mytag, -a_new.slexicinv.size());
    }
    // slexicinv [<vblex>, <pp>, <n>, <sg>, <pl>]
    // slexic = {<vblex>=-1, <pp>=-2, <n>=-3, <sg>=-4, <pl>=-5}

    // for testdata/bilingual/apertium-eo-en.eo-en.dix  :
    // [<re>, <alpha>, <tn>, <nt>, <percent>, <p3>, <predet>, <p2>, <np>, <p1>, <imp>, <nn>, <fti>, <vbmod>, <pos>, <vblex>, <sent>, <apos>, <ind>, <preadv>, <vaux>, <ant>, <gen>, <sp>, <ger>, <inf>, <top>, <rpar>, <pres>, <sg>, <url>, <cnjsub>, <vbser>, <fts>, <unc>, <vbdo>, <lpar>, <cm>, <email>, <ifi>, <pl>, <itg>, <ND>, <nomi>, <GD>, <det>, <pprs>, <ord>, <adj>, <vbhaver>, <sym>, <sup>, <subj>, <cni>, <atn>, <def>, <subs>, <MULT>, <qnt>, <mf>, <dem>, <cnjcoo>, <adv>, <enc>, <f>, <comp>, <prs>, <n>, <ij>, <pron>, <web>, <m>, <guio>, <past>, <cnjadv>, <acc>, <detnt>, <pii>, <ref>, <al>, <num>, <pri>, <cog>, <an>, <pro>, <sep>, <prn>, <obj>, <pis>, <aa>, <rel>, <pp>, <acr>, <pr>, <nom>]
    // System.out.println(a_new.slexicinv);

    // Reading of pairlist
    int bias = a_new.slexicinv.size();
    tam = Compression.multibyte_read(input); // 15 pairs  (1880 for eo-en.dix)
    while (tam > 0) {
      tam--;
      int first = Compression.multibyte_read(input);
      int second = Compression.multibyte_read(input);
      IntegerPair tmp2 = new IntegerPair(first - bias, second - bias);
      a_new.spairinv.add(tmp2);
      // not used a_new.spair.put(tmp2, spair.size());
    }


    // spair {<0,0>=0, <w,w>=1, <o,o>=2, <u,u>=3, <n,n>=4, <d,d>=5, <0,-3>=6, <0,-4>=7, <s,-3>=8, <0,-5>=9, <o,i>=10, <u,n>=11, <n,d>=12, <d,-1>=13, <0,-2>=14}
    // spairinv [<0,0>, <w,w>, <o,o>, <u,u>, <n,n>, <d,d>, <0,-3>, <0,-4>, <s,-3>, <0,-5>, <o,i>, <u,n>, <n,d>, <d,-1>, <0,-2>]
    // System.out.println(a_new.spairinv);

    // for testdata/bilingual/apertium-eo-en.eo-en.dix  :
    // [<0,0>, < , >, <!,!>, <",">, <#,#>, <$,$>, <%,%>, <&,&>, <','>, <(,(>, <),)>, <*,*>, <+,+>, <,,,>, <-,->, <.,.>, <A,A>, <B,B>, <C,C>, <D,D>, <E,E>, <F,F>, <G,G>, <H,H>, <I,I>, <J,J>, <K,K>, <L,L>, <M,M>, <N,N>, <O,O>, <P,P>, <Q,Q>, <R,R>, <S,S>, <T,T>, <U,U>, <V,V>, <W,W>, <X,X>, <Y,Y>, <Z,Z>, <a,a>, <b,b>, <c,c>, <d,d>, <e,e>, <f,f>, <g,g>, <h,h>, <i,i>, <j,j>, <k,k>, <l,l>, <m,m>, <n,n>, <o,o>, <p,p>, <q,q>, <r,r>, <s,s>, <t,t>, <u,u>, <v,v>, <w,w>, <x,x>, <y,y>, <z,z>, <À,À>, <Á,Á>, <Â,Â>, <Ä,Ä>, <Å,Å>, <Æ,Æ>, <Ç,Ç>, <È,È>, <É,É>, <Ê,Ê>, <Ë,Ë>, <Ì,Ì>, <Í,Í>, <Î,Î>, <Ï,Ï>, <Ñ,Ñ>, <Ò,Ò>, <Ó,Ó>, <Ô,Ô>, <Ö,Ö>, <Ø,Ø>, <Ù,Ù>, <Ú,Ú>, <Û,Û>, <Ü,Ü>, <à,à>, <á,á>, <â,â>, <ã,ã>, <ä,ä>, <å,å>, <æ,æ>, <ç,ç>, <è,è>, <é,é>, <ê,ê>, <ë,ë>, <ì,ì>, <í,í>, <î,î>, <ï,ï>, <ñ,ñ>, <ò,ò>, <ó,ó>, <ô,ô>, <õ,õ>, <ö,ö>, <ø,ø>, <ù,ù>, <ú,ú>, <û,û>, <ü,ü>, <Ĉ,Ĉ>, <ĉ,ĉ>, <Ĝ,Ĝ>, <ĝ,ĝ>, <Ĥ,Ĥ>, <ĥ,ĥ>, <Ĵ,Ĵ>, <ĵ,ĵ>, <Ŝ,Ŝ>, <ŝ,ŝ>, <Ŭ,Ŭ>, <ŭ,ŭ>, <ŵ,ŵ>, <-81,-81>, <-30,-30>, <b,e>, <e,b>, <#,l>, < ,i>, <p,-16>, <o,0>, <s,0>, <i,0>, <b,0>, <l,0>, <e,0>, <-33,0>, <b,n>, <#, >, < ,e>, <n,b>, <o,l>, <t,i>, < ,-16>, <p,0>, <b,f>, <e,i>, <#,n>, <o,-16>, <v,0>, <r,0>, <t,e>, <h,s>, <e,t>, <r,i>, <e,-33>, < ,0>, <-16,0>, <c,p>, <a,o>, <n,v>, <-21,i>, <0,-21>, <w,v>, <i,o>, <l,i>, <-21,-21>, <u,l>, <d,-21>, <-21,0>, <m,p>, <y,v>, <s,d>, <h,e>, <a,v>, <l,-21>, <-21,-13>, <-26,0>, <o,v>, <u,i>, <d,-54>, <m,d>, <u,e>, <s,v>, <-26,-29>, <d,0>, <m,e>, <i,b>, <g,l>, <h,i>, <t,-21>, <h,d>, <a,e>, <#,-21>, <t,0>, <e,s>, <-33,t>, <0,i>, <0,-33>, <o,s>, < ,t>, <b,i>, <d,f>, <o,a>, <-16,r>, <0,-16>, <-16,-16>, <-36,r>, <-50,-16>, <f,p>, <l,e>, <l,n>, <n,o>, <e,-68>, <-68,0>, <e,m>, <m,o>, <s,t>, <b,m>, <j,o>, <c,0>, <o,e>, <p,m>, <c,-68>, <s,a>, <u,b>, <b,o>, <s,n>, <c,o>, <r,-68>, <n,0>, <3,3>, <-68,-68>, <-93,-93>, <c,k>, <l,-49>, <-49,0>, <P,p>, <i,a>, <a,-49>, <a,n>, <m,t>, <u,r>, <l,a>, <t,n>, <i,s>, <t,c>, <n,-49>, <a,0>, <b,k>, <r,s>, <o,t>, <d,r>, <-63,l>, <0,a>, <0,n>, <0,d>, <0,e>, <0,-63>, <s,p>, <o,i>, <n, >, <e,f>, <-63,u>, <w,l>, <a,t>, <d,o>, <y,a>, <-49,-49>, <-1,-1>, <:,:>, </,/>, <0,0>, <1,1>, <2,2>, <4,4>, <5,5>, <6,6>, <7,7>, <8,8>, <9,9>, <_,_>, <n,u>, < ,n>, <M,d>, <n,-63>, <y,0>, <-63,0>, <o,m>, <n,a>, < ,r>, <T,d>, <e,-63>, <n,e>, <W,k>, <e,r>, <d,e>, <n,d>, <s,-63>, <o,ĵ>, < ,ŭ>, <u,-63>, <F,d>, <i,e>, <y,-63>, < ,b>, <S,a>, <o,d>, <n,i>, < ,m>, <u,n>, <n,ĉ>, <a,-63>, <f,e>, <o,k>, <r,z>, <i,m>, <n,p>, <s,l>, <a,-75>, <-75,0>, <i,t>, <-75,-75>, <.,k>, <g,z>, <t,o>, <m,l>, <s,-75>, <d,t>, < ,a>, <b, >, <y,p>, <-75,e>, <0,r>, <0,-75>, <h,t>, <a,r>, <d,n>, <#,s>, < ,d>, <v,n>, <r,-16>, <-86,0>, <k,a>, <e,n>, < ,p>, <o,r>, <v,e>, <r,o>, < ,v>, <d,k>, <o,j>, <v, >, <e,d>, <r,e>, <-63,n>, <0,o>, <0,v>, <#,-16>, <P,0>, <R,0>, <N,0>, <u,0>, <n,-16>, <g,v>, <t,k>, <#,i>, <P,-16>, <#,ĝ>, <u,-16>, < ,ĝ>, <w,e>, <a,k>, <k,i>, <e,ĝ>, <-16,i>, <g,r>, <t,a>, <#,k>, <P,i>, <R,r>, <N,i>, <k,0>, <t,v>, <#,e>, <a,-16>, <s,k>, <e,a>, <l,p>, <#,t>, < ,u>, <u,a>, <-86,-16>, <s,f>, <e,o>, <l,r>, <l,v>, <u,d>, <p,i>, <#,u>, <c, >, <k,e>, < ,l>, <x,k>, <-9,s>, <-80,o>, <0,-9>, <0,-80>, <C,K>, <T,t>, <V,e>, <-68,l>, <0,-68>, <-73,-73>, <-38,-38>, <;,;>, <?,?>, <-17,-17>, <[,[>, <-37,-37>, <],]>, <-28,-28>, <-18,-18>, <-5,-5>, <z,n>, <e,u>, <r,l>, <o,u>, <t,d>, <w,u>, <o,-81>, <-81,0>, <h,r>, <e,-81>, <f,k>, <i,v>, <v,i>, <x,s>, <v,p>, <i,k>, <g,-81>, <h,0>, <n,ŭ>, <n,k>, <e,k>, <-81,u>, <0,-81>, <l, >, <v,d>, <r, >, <n,-81>, <f,d>, <u,k>, <e,v>, <n,r>, <t, >, <-81,n>, <n,s>, <v,k>, <e, >, <e,p>, <g,k>, <h, >, <e,ŭ>, <y,-81>, <r,d>, <y,k>, <r,a>, <t,r>, <y,d>, <-81,e>, <0,k>, <f,i>, <y,e>, <-81,k>, <g,d>, <h,c>, <r,-81>, <o, >, < ,c>, <n,t>, <d,-81>, <e,c>, <h,n>, <u,t>, <n,c>, <g, >, <u,-81>, <t,m>, <h,l>, <t,l>, <h,-81>, <n,m>, <h,m>, <t,-81>, <o,n>, <a,m>, <l,o>, <m,i>, <i,l>, <i,n>, <n,j>, <-81,j>, <i,j>, <-81,o>, <b,l>, <i,d>, <-68,o>, <y, >, <-,u>, <-,d>, <t,u>, <w,-81>, <-,t>, <-,k>, <f,v>, <-,s>, <s,e>, <x,-81>, <v,-81>, <-,o>, <i,-81>, <g,0>, <-,n>, <i,ŭ>, <-, >, <-,e>, <0,u>, <w, >, <r,t>, <r,v>, <-81,a>, <i, >, <-81,i>, <0,s>, <v,s>, <g,o>, <h,k>, <-81,ŭ>, <w,d>, <f, >, <u,v>, <-81,r>, <s, >, <x,e>, <-81,s>, < ,ĉ>, <f,u>, < ,j>, <u,-87>, <s,-3>, <-87,0>, <-3,0>, <y,-87>, <o,-3>, <a,i>, <o,ĉ>, <t,j>, <h,-87>, <e,-3>, <m,0>, <e,ĉ>, <r,-87>, <y,-3>, <c,u>, <-87,-3>, <-87,p>, <-3,a>, <-41,r>, <0,t>, <0,-87>, <0,-3>, <0,-30>, <n,l>, <r,-30>, <-30,0>, <a,p>, <-87,k>, <b,a>, <t,b>, <h,a>, <-87,ŭ>, <-3,-87>, <-87,u>, <a,ĉ>, <l,u>, <-87,-87>, <-3,-3>, <-24,-41>, <-7,j>, <0,-7>, <-87,a>, <-41,-3>, <0,-41>, <e,-87>, <w,k>, <v,j>, <v,a>, <e,j>, <r,n>, <-3,j>, <h,j>, <n,-87>, <g,-3>, <s,i>, <m,-87>, <w,0>, <w,n>, <-87,-42>, <-42,0>, <-42,-42>, <i,u>, <c,-87>, <h,-42>, <t,-87>, <w,o>, <u,-42>, <m, >, <a,d>, <e,l>, <-87,i>, <-42,-87>, <0,-42>, <m,-46>, <-46,0>, <y,-46>, <-46,-42>, <d,u>, <-3,-30>, <a,u>, <t,-91>, <-91,-84>, <-84,0>, <-87,ĉ>, <-3,i>, <-30,-87>, <t,ĉ>, <-3,u>, <y,t>, <-63,-87>, <0,-95>, <v,u>, <a,j>, <l,-87>, <s,u>, <h,-84>, <a,-41>, <-91,0>, <-12,0>, <-60,0>, <-41,0>, <w,-91>, <o,-41>, <-91,-91>, <y,i>, <s,-87>, <e,-79>, <l,-10>, <f,-60>, <-87,-30>, <-79,0>, <-10,0>, <r,-79>, <s,-8>, <e,-60>, <l,-30>, <f,0>, <-8,0>, <s,-79>, <e,-6>, <l,-72>, <f,-30>, <-6,0>, <-72,0>, <l,-65>, <-65,0>, <l,-6>, <f,-4>, <-4,0>, <e,-10>, <l,-60>, <v,-41>, <l,-41>, <t,s>, <m,-79>, <s,-6>, <s,m>, <l,m>, <f,-87>, <-87,-79>, <-53,-10>, <-10,-60>, <-60,-30>, <-53,-8>, <-8,-60>, <-53,-6>, <-6,-65>, <-65,-30>, <-6,-72>, <-72,-30>, <-6,-4>, <-4,-30>, <-60,-41>, <-6,-60>, <-60,-24>, <-24,0>, <-88,-10>, <-88,-8>, <-88,-6>, <b,ĉ>, <c,r>, <h,u>, <l,-75>, <r,-75>, <u,ĝ>, <i,-75>, <0,l>, <c,d>, <o,-75>, <g,m>, <h,-75>, <h,v>, <-75,m>, <u,j>, <-75,t>, <y,s>, <-75, >, <0,m>, <t,-75>, <f,o>, <-75,k>, <f,t>, <r,ŭ>, <-75,o>, <i,r>, <-75,i>, <0, >, <d,p>, < ,o>, < ,-75>, <p,s>, <w,ĉ>, <e,-75>, <f,-75>, <w,m>, <l,k>, <a,l>, <g,a>, <d,a>, <b,ŭ>, <y,-69>, <-69,0>, <g,b>, <-69,n>, <0,-69>, <s,ĝ>, <u,s>, <a,-69>, <y,r>, <-69,i>, <-69,o>, <a,-32>, <-32,0>, <d,j>, <-62,-62>, <-62,ŭ>, <0,-62>, <b,s>, <a,-62>, <-62,0>, <t,-62>, <r,k>, <r,p>, <h,f>, <-62,e>, <e,-46>, <-46,-56>, <-56,0>, <-46,e>, <-19,t>, <0,_>, <0,-46>, <0,-19>, <p,-46>, <l,-59>, <-59,0>, <-46,p>, <-59,a>, <0,#>, <0,-59>, <#,p>, <-46,o>, <-59,#>, <y,n>, <-46,a>, <-19,-46>, <r,-19>, <-19,0>, <w,a>, <v,-19>, <h,-46>, <e,-19>, <r,-46>, <-46,-19>, <o,-49>, <-49,f>, <0,-49>, <a,-68>, <-68,f>, <o,-68>, <-49,a>, <r,u>, <t,-49>, <c,a>, <d,-49>, <h,-49>, <l,d>, <t,p>, <e,-49>, <h,o>, <s,-49>, <-63,e>, <t,-63>, <c,e>, <o,-63>, <d,-63>, <h,-63>, <-63,-63>, <g,e>, <-63,p>, <-46,ŭ>, <-19,-30>, <y,-19>, <-46,-30>, <-59,-46>, <-24,-59>, <-30,-59>, <-41,-59>, <f,l>, <-46,-46>, <-59,-59>, <-46,t>, <-30,-46>, <f,m>, <-46,m>, <-59,u>, <-41,l>, <c,l>, <-59, >, <-30,d>, <f,a>, < ,-46>, <o,-59>, <-46,l>, <-59,i>, <-46,i>, <o,-46>, <f,-59>, <p,b>, <-46,n>, <-19,i>, <-46,d>, <t,-46>, <-46,-61>, <-61,0>, <-61,u>, <0,-61>, <c,-46>, <h,-59>, <n,-46>, <y,-59>, <-46,-41>, <u, >, <a, >, <y,u>, <-59,t>, <c,m>, <m,-59>, <o,f>, <g,ĉ>, <-46, >, <-59,d>, <q,s>, <i,f>, <t,-59>, <-15,-46>, <0,-15>, <-15,-15>, <-46,-15>, <-15,0>, <h,ŝ>, <i,ĝ>, <r,-49>, <',a>, <s,-46>, <v,t>, <y,-20>, <-20,0>, <-20,-20>, <l,-20>, <e,-20>, <-20,e>, <0,-20>, <j,t>, < ,-20>, <-20,m>, <-20,a>, <0,j>, <-94,r>, <0,-94>, <-63,r>, <n,-94>, <_,0>, <-94,0>, <-94,-94>, <o,-94>, <e,-94>, <-94,a>, <0,ŭ>, <w,s>, <h,-94>, <d,b>, <a,s>, <b,u>, <o,p>, <b,r>, <u,-94>, <d,-94>, <t,ŭ>, <i,-94>, <-94,e>, <s,-94>, <t,-94>, <p,l>, <w,-94>, <c,ĉ>, <-63,ŭ>, <d,ŭ>, <r,ĉ>, <-94,ŭ>, <',d>, <-23,-23>, <-23,e>, <0,-23>, <b,d>, <f,-94>, <r,m>, <x,r>, <p,-94>, <-94,m>, <m,n>, <r,-94>, <c,ŭ>, <b,p>, <a,-94>, < ,-94>, <c,n>, <g,n>, <a,c>, <s,o>, <r,c>, <d, >, <-94,l>, <f,c>, <-94,n>, <c,f>, <p,e>, <f,n>, <-94,t>, < ,s>, <x,-94>, <p,r>, < ,k>, <u,m>, <s,g>, <m,-94>, <-94,d>, <i,p>, <p,t>, <d,l>, <w,r>, <W,V>, <-9,-9>, <-80,-80>, <N,A>, <L,N>, <b,c>, <t,L>, <o,b>, <A,i>, <r,ĝ>, <y,-9>, <-9,-80>, <-80,0>, <s,-9>, <t,-27>, <-9,0>, <-27,0>, <-27,-27>, <-9,o>, <-27,-9>, <0,-27>, <g,ĝ>, <-9,-27>, <s,j>, <c,z>, <t,A>, <A,j>, <n,-9>, <d,-27>, <a,-9>, <E,A>, <d,-9>, <u,ŭ>, <C,Ĉ>, <p,f>, <a,b>, <y,o>, <s,z>, <I,B>, <u,o>, <m,-9>, <s,r>, <A,-9>, <i,-27>, <z,c>, <U,B>, < ,-9>, <K,-27>, <t,B>, <U,i>, <i,-9>, <K,0>, <G,B>, <B,-9>, <r,-27>, <s,ŝ>, <e,ŝ>, <d,i>, <r,-9>, <k,-27>, <-9,i>, <-27,o>, <P,F>, <p,n>, <e,-9>, <s,-27>, <t,F>, <p,-9>, <p,-27>, <-9,d>, <c,g>, <c,i>, <G,Ĝ>, <S,H>, <-9,n>, <-27,i>, <c,s>, <Y,J>, <I,j>, <l,-27>, <n,-27>, <a,g>, <G,K>, <a,->, <n,L>, <L,n>, <o,-9>, <Q,K>, < ,->, <h,->, < ,A>, <A,m>, <t,->, <h,A>, <A,e>, <m,r>, <c,-9>, <a,-27>, <l,A>, <H,N>, <-27,d>, <t,N>, <N,r>, <l,-9>, <y,g>, <y,j>, <-22,-9>, <0,-22>, <i,->, <A,r>, <W,K>, <z,e>, <e,ĥ>, <R,-27>, <k,o>, <k,h>, < ,f>, <A,f>, <f,r>, <E,O>, <A,t>, <f,->, <r,A>, <-9,k>, <W,O>, <s,c>, <c,A>, <a,f>, <-9,r>, <z,l>, < ,-27>, <S,-30>, <t,U>, <U,o>, <t,-30>, <S,0>, <A,0>, <n,g>, <g,t>, <c,t>, <i,z>, <i,g>, <J,Ĵ>, < ,K>, <C,a>, < ,D>, <D,a>, <y,l>, <d,->, <e,I>, <I,s>, <h,K>, <C,r>, <h,D>, <D,k>, <k,t>, <t,-9>, <e,-27>, <n,V>, <-9,g>, <L,M>, <S,r>, <x, >, <o,S>, <c,ŝ>, <g,j>, <J,j>, <F,f>, <M,m>, <A,a>, <S,s>, <O,o>, <N,n>, <D,d>, <M,l>, <y,-68>, <T,m>, <W,m>, <T,ĵ>, <d,-68>, <F,v>, <-68,d>, <S,d>, <y,ĉ>, <C,k>, <G,g>, <G,ĝ>, <c,-49>, <i,-49>, <d,h>, <-9,-22>, <-22,0>, <B,b>, <H,h>, <E,o>, <W,o>, <-49,i>, <E,a>, <A,u>, <m,s>, <E,-49>, <h,g>, <o,->, <-,-49>, <I,i>, <n,-68>, < ,z>, <s,->, <a,z>, <s,-68>, <C,ĉ>, <D,n>, <-49,l>, <0,-72>, <-68,n>, <0,-65>, <E,e>, <A,l>, <k,f>, <0,g>, <P,f>, <i,-68>, <P,k>, <-49,g>, <-68,g>, <S,h>, <-49,d>, <M,i>, <m,a>, <L,l>, <-68,a>, <U,-9>, <k,r>, <K,k>, <C,c>, <V,v>, <Y,j>, <h,-68>, <G,k>, <j,ĝ>, <k,ĥ>, <U,u>, <q,k>, <a,ŝ>, <-68,i>, <-68,-65>, <o,ŭ>, <w,-68>, <o,-65>, <h,-65>, <C,l>, <S,l>, <p,a>, <n,h>, <h,p>, <-63,a>, <t,h>, <l,s>, <S,-63>, <P,l>, <e,g>, <O,l>, <-63,t>, <G,l>, <-63,k>, <v,o>, <ç,i>, <F,l>, <n,f>, <0,c>, <I,l>, <u,f>, <d,s>, <R,l>, <0,ĉ>, <E,l>, <E,g>, <l,-63>, <B,l>, <E,n>, <i,-63>, <l,t>, <-63,o>, <m,g>, <-63,m>, <D,l>, <-63,d>, <m,f>, < ,-63>, <G,0>, <W,l>, <l,g>, <q,e>, <N,l>, <v,l>, <H,l>, <g,h>, <l,b>, <U,l>, <z,a>, <-63,ĥ>, <-63,v>, <r,b>, <T,l>, <K,l>, <-63,b>, <z, >, <r,h>, <w,b>, <e,-65>, <-68,-72>, <m,-68>, <a,-65>, <k,p>, <s,-72>, <d,-65>, <d,-72>, <r,-72>, <t,-65>, <p,v>, <w,-72>, <-68,b>, <n,ĝ>, <q,r>, <s,-65>, <-68,t>, <p,d>, <-68,-45>, <0,-45>, <-68,r>, <g,s>, <n,-72>, <-68,j>, <n,-65>, <p,-68>, <p,g>, <p,u>, < ,-68>, <c,b>, <-68,s>, <a,h>, <t,-68>, <t,-69>, <k, >, <o,-69>, <k,ĉ>, <k,n>, <J, >, <l,J>, <-69, >, <0,b>, <g,u>, <l,-69>, <m,j>, <g,i>, <k,b>, <b,g>, <-49,n>, <-63, >, <M,s>, <r,->, <-22,-22>, <-60,-60>, <M,f>, <-81,-48>, <-48,0>, <A,P>, <D,.>, <-81,K>, <0,.>, <0,K>, <-68,K>, <-30,.>, <A,p>, <-63,K>, <B,a>, <C,.>, <E,K>, <-63,E>, <B,K>, <C,E>, <E,-63>, <c,-94>, <p,.>, <p,-63>, <x,0>, <.,0>, <-63,s>, <w,c>, <y,-49>, <d,m>, <W,a>, <I,n>, <W, >, <I,d>, <I,m>, <r,f>, <0,ĝ>, <-68,e>, <-16,e>, <w,t>, <-49,e>, <h,ĉ>, <-63,i>, <x,o>, <k,d>, <0,p>, <v,ĉ>, <f,ĵ>, <r,ĵ>, <r,-91>, <w,i>, <c,-91>, <d,ĉ>, <r,-63>, <e,-42>, <-42,-63>, <n,->, <-,-63>, <j,n>, <f,s>, <v,-63>, <m,u>, <r,j>, <a,ŭ>, <h,ŭ>, <y,ŭ>, <y,h>, <z,t>, <b,t>, <-49,ĝ>, <w,-63>, <-68,u>, <#,a>, <#,r>, <o,ĝ>, <t,-16>, <c,v>, <p,z>, <g,p>, <b,-68>, <w,g>, <l,-68>, <-68,k>, <o,g>, <u,p>, < ,g>, <#,ŭ>, <u,g>, <r,ŝ>, <f,-16>, <t,f>, <#,j>, <d,-16>, <#,0>, <p,k>, <#,o>, <t,g>, <g,-16>, <-16,a>, <i,-16>, <k,#>, <e,z>, <#,g>, <c,-16>, <y,-16>, <#,v>, <m,v>, <b,-16>, <h,-16>, <l,-16>, <w,-16>, <s,-16>, <b,v>, <q,0>, <a,#>, <#,m>, <s,ŭ>, <s,b>, <-,0>, <m,-63>, <i,ĉ>, <j,ĉ>, <n,z>, <c,-63>, <d,g>, <x,p>, <I,a>, <l,c>, <l,f>, <j,p>, <l,h>, <j,0>, <w,p>, < ,ŝ>, <u,z>, <o,ŝ>, <t,z>, <-16,n>, <é,o>, <p,o>, <0,ĥ>, <-68,ĥ>, <s,ĥ>, <k,-68>, <.,n>, <a,-46>, <l,-19>, <-19,-19>, <g,ŭ>, <l,ŭ>, <-94,o>, <-94,j>, <g,-94>, <-63,->, <0,f>, <m,-91>, <f,j>, <f,h>, <m,ĉ>, <m,c>, <-63,c>, <f,-63>, <-75,n>, <-75,ŭ>, <g,-75>, <s,h>, <z,g>, <g,-68>, <A,o>, <S,-9>, <A,-27>, <U,S>, <S,o>, <S,v>, <R,e>, <-9,t>, <-27,->, <0,U>, <t,S>, <U,t>, <S,->, <S,U>, <R,n>, <K,r>, <-27,t>, <K,o>, <-27,n>, <V,A>, <A,V>, <T,I>, <-93,o>, <0,-93>, <u,ĉ>, <',0>, <é,0>, <â,0>, <-9,j>, <t,P>, <P,n>, <k,j>, <t,M>, <M,t>, <',ŭ>, <p,h>, <z,i>, <a,ĝ>, <-16,t>, <-16,g>, <-16,k>, <m,-16>, <c,ĝ>, <u,c>, <y,c>, <e,-16>, <q,n>, <q,i>, <-16,p>, <-16,o>, <v,ĵ>, <j,u>, <d,v>, <-49,r>, <r,g>, <-49,t>, <-16,j>, <m,k>, <-68,p>, <o,h>, <k,l>, <l,j>, <-16,s>, <-68,z>, <g,-63>, <u,-49>, <h,z>, <C, >, <C,e>, <C,t>, <0,ĵ>, <-68,m>, <-,-68>, <x,l>, <p,j>, <p,c>, <i,ĵ>, <x,i>, <-68,ĵ>, <k,s>, <k,m>, <m,b>, <o,c>, <m,z>, <m,ŭ>, <y,ĵ>, <k,-16>, <0,z>, <-68,c>, <v,r>, <-49,u>, <x,a>, <k,u>, <-49,b>, <m,ĝ>, <-16,c>, <u,-68>, <f,-68>, <v,-16>, <g,c>, <b,z>, <l,ĵ>, <-68,v>, <-16,v>, <k,c>, <-16,u>, <-68,ĝ>, <d,ĝ>, <c,j>, <z,o>, <t,ŝ>, <d,ŝ>, <b,ĝ>, <o,z>, <-16,l>, <t,ĝ>, <l,ĝ>, <-49,m>, <b,ŝ>, <k,ĝ>, <y,m>, <-16,m>, <z,r>, <i,ŝ>, <c,ĵ>, <-16,z>, <t,ĵ>, <e,h>, <q,o>, <s,ĵ>, <-,v>, <p,-49>, <i,c>, <c,ĥ>, <s,ĉ>, <k,z>, <-68,ĉ>, <c,h>, <w,ŭ>, <q,v>, <-16,ŭ>, <b,-49>, <b,-63>, <d,c>, <-49,s>, <n,ŝ>, <m,ĵ>, <q,-68>, <v,g>, <v,m>, <-49,o>, <l,z>, <-49,z>, <v,b>, <-16,ĝ>, <-63,g>, <v,-49>, <-16,d>, <g,f>, <a,ĵ>, <y,ĝ>, <d,z>, <z,k>, <p,ĵ>, <y,z>, <j,i>, <q,g>, <x,u>, <x,n>, <x,f>, <x,d>, <x,t>, <f,g>, <-49,c>, <f,ŝ>, <f,b>, <f,ĉ>, <u,ĵ>, <v,-68>, <w,f>, <g,-49>, <y,ŝ>, <w,ĵ>, <f,-49>, <m,-49>, <l,ŝ>, <-,i>, <k,-49>, <q,a>, <b,ĵ>, <j,-27>, <-,l>, <S, >, <l,Ŝ>, <z,u>, <h,b>, <h,ĝ>, <g,ĵ>, <-63,ĝ>, <e,ĵ>, <K,u>, <u,ŝ>, <h,-9>, <i,h>, <j,l>, <j,b>, <-,g>, <j,ĵ>, <j,a>, <j,k>, <j,ŝ>, <j,v>, <j,s>, <k,ŝ>, <k,g>, <w,ĝ>, <w,z>, <y,b>, <n,ĵ>, <d,ĵ>, <q,u>, <-30,-68>, <-41,e>, <x,c>, <m,h>, <w,j>, <-49,v>, <x,m>, <z,ŝ>, <p,ŝ>, <v,ĝ>, <-94,s>, <0,ŝ>, <q,d>, <-49,p>, <f,z>, <x,-68>, <-29,-16>, <0,-11>, <-11,-16>, <l,ĉ>, <',-49>, <p,ĝ>, <p,ĉ>, <v,h>, <z,m>, <b,j>, <f,ĝ>, <-49,k>, <q,p>, <q,c>, <e,->, <q,t>, <-68,ŝ>, <-16,ĉ>, <-,c>, <-16,b>, <z,-16>, <-,a>, <j,m>, <q,-63>, <-16,ŝ>, <-68,h>, <v,f>, <v,ŝ>, <-,r>, <w,h>, <-41,o>, <y,f>, <w,-49>, <k,ŭ>, <-68,ŭ>, <',-68>, <v,c>, <-68, >, <ç,s>, <n,-30>, <â,a>, <é,t>, <è,e>, <-,f>, <-41,-41>, <s,-41>, <q,ŝ>, <j,d>, <-49,ĉ>, <ï,i>, <z,-49>, <-16,f>, <-63,j>, <z,-63>, <z,-68>, <v,z>, <g,ŝ>, <-63,z>, <x,g>, <b,h>, <h,ĵ>, <-,z>, <c,->, <q,-49>, <x,v>, <-49,h>, <-16,h>, <k,v>, <w,ŝ>, <z,v>, <q,m>, <-49,ŝ>, <z,p>, <f,ŭ>, <z,d>, <z,b>, <q,j>, <-,p>, <j,e>, <j,g>, <J,I>, <-9,b>, <j,r>, <L,S>, <0,h>, <z,0>, <f,R>, <e,-41>, <-,m>, <k,ĵ>, <q,l>, <p,ŭ>, <p,->, <-9,a>, <-63,ŝ>, <m,ŝ>, <-49,->, <z,f>, <z,s>, <ú,u>, <-72,-72>, <X,K>, <-65,-65>, <',e>, <',-63>, <x,-63>, <j,ŭ>, <m,->, <h,ĥ>, <u,h>, <j,f>, <j,h>, <Z,e>, <k,-63>, <-68,->, <q,ĉ>, <q,f>, <-49,ĵ>, <q,ŭ>, <-,ŭ>, <x,z>, <m,ĥ>, <O,0>]

    return a_new;
  }

  public static Alphabet read(ByteBuffer input) throws IOException {
    Alphabet a_new = new Alphabet();

    // the following examples and numbers corresponds to testdata/wound-example.dix
    // Reading of taglist.
    int tam = Compression.multibyte_read(input); // 5 tags  (95 for eo-en.dix)
    while (tam > 0) {
      tam--;
      String mytag = "<" + Compression.String_read(input) + ">";
      a_new.slexicinv.add(mytag);
      a_new.slexic.put(mytag, -a_new.slexicinv.size());
    }
    // slexicinv [<vblex>, <pp>, <n>, <sg>, <pl>]
    // slexic = {<vblex>=-1, <pp>=-2, <n>=-3, <sg>=-4, <pl>=-5}

    // for testdata/bilingual/apertium-eo-en.eo-en.dix  :
    // [<re>, <alpha>, <tn>, <nt>, <percent>, <p3>, <predet>, <p2>, <np>, <p1>, <imp>, <nn>, <fti>, <vbmod>, <pos>, <vblex>, <sent>, <apos>, <ind>, <preadv>, <vaux>, <ant>, <gen>, <sp>, <ger>, <inf>, <top>, <rpar>, <pres>, <sg>, <url>, <cnjsub>, <vbser>, <fts>, <unc>, <vbdo>, <lpar>, <cm>, <email>, <ifi>, <pl>, <itg>, <ND>, <nomi>, <GD>, <det>, <pprs>, <ord>, <adj>, <vbhaver>, <sym>, <sup>, <subj>, <cni>, <atn>, <def>, <subs>, <MULT>, <qnt>, <mf>, <dem>, <cnjcoo>, <adv>, <enc>, <f>, <comp>, <prs>, <n>, <ij>, <pron>, <web>, <m>, <guio>, <past>, <cnjadv>, <acc>, <detnt>, <pii>, <ref>, <al>, <num>, <pri>, <cog>, <an>, <pro>, <sep>, <prn>, <obj>, <pis>, <aa>, <rel>, <pp>, <acr>, <pr>, <nom>]
    // System.out.println(a_new.slexicinv);

    // Reading of pairlist
    int bias = a_new.slexicinv.size();
    tam = Compression.multibyte_read(input); // 15 pairs  (1880 for eo-en.dix)
    while (tam > 0) {
      tam--;
      int first = Compression.multibyte_read(input);
      int second = Compression.multibyte_read(input);
      IntegerPair tmp2 = new IntegerPair(first - bias, second - bias);
      a_new.spairinv.add(tmp2);
      // not used a_new.spair.put(tmp2, spair.size());
    }


    // spair {<0,0>=0, <w,w>=1, <o,o>=2, <u,u>=3, <n,n>=4, <d,d>=5, <0,-3>=6, <0,-4>=7, <s,-3>=8, <0,-5>=9, <o,i>=10, <u,n>=11, <n,d>=12, <d,-1>=13, <0,-2>=14}
    // spairinv [<0,0>, <w,w>, <o,o>, <u,u>, <n,n>, <d,d>, <0,-3>, <0,-4>, <s,-3>, <0,-5>, <o,i>, <u,n>, <n,d>, <d,-1>, <0,-2>]
    // System.out.println(a_new.spairinv);

    // for testdata/bilingual/apertium-eo-en.eo-en.dix  :
    // [<0,0>, < , >, <!,!>, <",">, <#,#>, <$,$>, <%,%>, <&,&>, <','>, <(,(>, <),)>, <*,*>, <+,+>, <,,,>, <-,->, <.,.>, <A,A>, <B,B>, <C,C>, <D,D>, <E,E>, <F,F>, <G,G>, <H,H>, <I,I>, <J,J>, <K,K>, <L,L>, <M,M>, <N,N>, <O,O>, <P,P>, <Q,Q>, <R,R>, <S,S>, <T,T>, <U,U>, <V,V>, <W,W>, <X,X>, <Y,Y>, <Z,Z>, <a,a>, <b,b>, <c,c>, <d,d>, <e,e>, <f,f>, <g,g>, <h,h>, <i,i>, <j,j>, <k,k>, <l,l>, <m,m>, <n,n>, <o,o>, <p,p>, <q,q>, <r,r>, <s,s>, <t,t>, <u,u>, <v,v>, <w,w>, <x,x>, <y,y>, <z,z>, <À,À>, <Á,Á>, <Â,Â>, <Ä,Ä>, <Å,Å>, <Æ,Æ>, <Ç,Ç>, <È,È>, <É,É>, <Ê,Ê>, <Ë,Ë>, <Ì,Ì>, <Í,Í>, <Î,Î>, <Ï,Ï>, <Ñ,Ñ>, <Ò,Ò>, <Ó,Ó>, <Ô,Ô>, <Ö,Ö>, <Ø,Ø>, <Ù,Ù>, <Ú,Ú>, <Û,Û>, <Ü,Ü>, <à,à>, <á,á>, <â,â>, <ã,ã>, <ä,ä>, <å,å>, <æ,æ>, <ç,ç>, <è,è>, <é,é>, <ê,ê>, <ë,ë>, <ì,ì>, <í,í>, <î,î>, <ï,ï>, <ñ,ñ>, <ò,ò>, <ó,ó>, <ô,ô>, <õ,õ>, <ö,ö>, <ø,ø>, <ù,ù>, <ú,ú>, <û,û>, <ü,ü>, <Ĉ,Ĉ>, <ĉ,ĉ>, <Ĝ,Ĝ>, <ĝ,ĝ>, <Ĥ,Ĥ>, <ĥ,ĥ>, <Ĵ,Ĵ>, <ĵ,ĵ>, <Ŝ,Ŝ>, <ŝ,ŝ>, <Ŭ,Ŭ>, <ŭ,ŭ>, <ŵ,ŵ>, <-81,-81>, <-30,-30>, <b,e>, <e,b>, <#,l>, < ,i>, <p,-16>, <o,0>, <s,0>, <i,0>, <b,0>, <l,0>, <e,0>, <-33,0>, <b,n>, <#, >, < ,e>, <n,b>, <o,l>, <t,i>, < ,-16>, <p,0>, <b,f>, <e,i>, <#,n>, <o,-16>, <v,0>, <r,0>, <t,e>, <h,s>, <e,t>, <r,i>, <e,-33>, < ,0>, <-16,0>, <c,p>, <a,o>, <n,v>, <-21,i>, <0,-21>, <w,v>, <i,o>, <l,i>, <-21,-21>, <u,l>, <d,-21>, <-21,0>, <m,p>, <y,v>, <s,d>, <h,e>, <a,v>, <l,-21>, <-21,-13>, <-26,0>, <o,v>, <u,i>, <d,-54>, <m,d>, <u,e>, <s,v>, <-26,-29>, <d,0>, <m,e>, <i,b>, <g,l>, <h,i>, <t,-21>, <h,d>, <a,e>, <#,-21>, <t,0>, <e,s>, <-33,t>, <0,i>, <0,-33>, <o,s>, < ,t>, <b,i>, <d,f>, <o,a>, <-16,r>, <0,-16>, <-16,-16>, <-36,r>, <-50,-16>, <f,p>, <l,e>, <l,n>, <n,o>, <e,-68>, <-68,0>, <e,m>, <m,o>, <s,t>, <b,m>, <j,o>, <c,0>, <o,e>, <p,m>, <c,-68>, <s,a>, <u,b>, <b,o>, <s,n>, <c,o>, <r,-68>, <n,0>, <3,3>, <-68,-68>, <-93,-93>, <c,k>, <l,-49>, <-49,0>, <P,p>, <i,a>, <a,-49>, <a,n>, <m,t>, <u,r>, <l,a>, <t,n>, <i,s>, <t,c>, <n,-49>, <a,0>, <b,k>, <r,s>, <o,t>, <d,r>, <-63,l>, <0,a>, <0,n>, <0,d>, <0,e>, <0,-63>, <s,p>, <o,i>, <n, >, <e,f>, <-63,u>, <w,l>, <a,t>, <d,o>, <y,a>, <-49,-49>, <-1,-1>, <:,:>, </,/>, <0,0>, <1,1>, <2,2>, <4,4>, <5,5>, <6,6>, <7,7>, <8,8>, <9,9>, <_,_>, <n,u>, < ,n>, <M,d>, <n,-63>, <y,0>, <-63,0>, <o,m>, <n,a>, < ,r>, <T,d>, <e,-63>, <n,e>, <W,k>, <e,r>, <d,e>, <n,d>, <s,-63>, <o,ĵ>, < ,ŭ>, <u,-63>, <F,d>, <i,e>, <y,-63>, < ,b>, <S,a>, <o,d>, <n,i>, < ,m>, <u,n>, <n,ĉ>, <a,-63>, <f,e>, <o,k>, <r,z>, <i,m>, <n,p>, <s,l>, <a,-75>, <-75,0>, <i,t>, <-75,-75>, <.,k>, <g,z>, <t,o>, <m,l>, <s,-75>, <d,t>, < ,a>, <b, >, <y,p>, <-75,e>, <0,r>, <0,-75>, <h,t>, <a,r>, <d,n>, <#,s>, < ,d>, <v,n>, <r,-16>, <-86,0>, <k,a>, <e,n>, < ,p>, <o,r>, <v,e>, <r,o>, < ,v>, <d,k>, <o,j>, <v, >, <e,d>, <r,e>, <-63,n>, <0,o>, <0,v>, <#,-16>, <P,0>, <R,0>, <N,0>, <u,0>, <n,-16>, <g,v>, <t,k>, <#,i>, <P,-16>, <#,ĝ>, <u,-16>, < ,ĝ>, <w,e>, <a,k>, <k,i>, <e,ĝ>, <-16,i>, <g,r>, <t,a>, <#,k>, <P,i>, <R,r>, <N,i>, <k,0>, <t,v>, <#,e>, <a,-16>, <s,k>, <e,a>, <l,p>, <#,t>, < ,u>, <u,a>, <-86,-16>, <s,f>, <e,o>, <l,r>, <l,v>, <u,d>, <p,i>, <#,u>, <c, >, <k,e>, < ,l>, <x,k>, <-9,s>, <-80,o>, <0,-9>, <0,-80>, <C,K>, <T,t>, <V,e>, <-68,l>, <0,-68>, <-73,-73>, <-38,-38>, <;,;>, <?,?>, <-17,-17>, <[,[>, <-37,-37>, <],]>, <-28,-28>, <-18,-18>, <-5,-5>, <z,n>, <e,u>, <r,l>, <o,u>, <t,d>, <w,u>, <o,-81>, <-81,0>, <h,r>, <e,-81>, <f,k>, <i,v>, <v,i>, <x,s>, <v,p>, <i,k>, <g,-81>, <h,0>, <n,ŭ>, <n,k>, <e,k>, <-81,u>, <0,-81>, <l, >, <v,d>, <r, >, <n,-81>, <f,d>, <u,k>, <e,v>, <n,r>, <t, >, <-81,n>, <n,s>, <v,k>, <e, >, <e,p>, <g,k>, <h, >, <e,ŭ>, <y,-81>, <r,d>, <y,k>, <r,a>, <t,r>, <y,d>, <-81,e>, <0,k>, <f,i>, <y,e>, <-81,k>, <g,d>, <h,c>, <r,-81>, <o, >, < ,c>, <n,t>, <d,-81>, <e,c>, <h,n>, <u,t>, <n,c>, <g, >, <u,-81>, <t,m>, <h,l>, <t,l>, <h,-81>, <n,m>, <h,m>, <t,-81>, <o,n>, <a,m>, <l,o>, <m,i>, <i,l>, <i,n>, <n,j>, <-81,j>, <i,j>, <-81,o>, <b,l>, <i,d>, <-68,o>, <y, >, <-,u>, <-,d>, <t,u>, <w,-81>, <-,t>, <-,k>, <f,v>, <-,s>, <s,e>, <x,-81>, <v,-81>, <-,o>, <i,-81>, <g,0>, <-,n>, <i,ŭ>, <-, >, <-,e>, <0,u>, <w, >, <r,t>, <r,v>, <-81,a>, <i, >, <-81,i>, <0,s>, <v,s>, <g,o>, <h,k>, <-81,ŭ>, <w,d>, <f, >, <u,v>, <-81,r>, <s, >, <x,e>, <-81,s>, < ,ĉ>, <f,u>, < ,j>, <u,-87>, <s,-3>, <-87,0>, <-3,0>, <y,-87>, <o,-3>, <a,i>, <o,ĉ>, <t,j>, <h,-87>, <e,-3>, <m,0>, <e,ĉ>, <r,-87>, <y,-3>, <c,u>, <-87,-3>, <-87,p>, <-3,a>, <-41,r>, <0,t>, <0,-87>, <0,-3>, <0,-30>, <n,l>, <r,-30>, <-30,0>, <a,p>, <-87,k>, <b,a>, <t,b>, <h,a>, <-87,ŭ>, <-3,-87>, <-87,u>, <a,ĉ>, <l,u>, <-87,-87>, <-3,-3>, <-24,-41>, <-7,j>, <0,-7>, <-87,a>, <-41,-3>, <0,-41>, <e,-87>, <w,k>, <v,j>, <v,a>, <e,j>, <r,n>, <-3,j>, <h,j>, <n,-87>, <g,-3>, <s,i>, <m,-87>, <w,0>, <w,n>, <-87,-42>, <-42,0>, <-42,-42>, <i,u>, <c,-87>, <h,-42>, <t,-87>, <w,o>, <u,-42>, <m, >, <a,d>, <e,l>, <-87,i>, <-42,-87>, <0,-42>, <m,-46>, <-46,0>, <y,-46>, <-46,-42>, <d,u>, <-3,-30>, <a,u>, <t,-91>, <-91,-84>, <-84,0>, <-87,ĉ>, <-3,i>, <-30,-87>, <t,ĉ>, <-3,u>, <y,t>, <-63,-87>, <0,-95>, <v,u>, <a,j>, <l,-87>, <s,u>, <h,-84>, <a,-41>, <-91,0>, <-12,0>, <-60,0>, <-41,0>, <w,-91>, <o,-41>, <-91,-91>, <y,i>, <s,-87>, <e,-79>, <l,-10>, <f,-60>, <-87,-30>, <-79,0>, <-10,0>, <r,-79>, <s,-8>, <e,-60>, <l,-30>, <f,0>, <-8,0>, <s,-79>, <e,-6>, <l,-72>, <f,-30>, <-6,0>, <-72,0>, <l,-65>, <-65,0>, <l,-6>, <f,-4>, <-4,0>, <e,-10>, <l,-60>, <v,-41>, <l,-41>, <t,s>, <m,-79>, <s,-6>, <s,m>, <l,m>, <f,-87>, <-87,-79>, <-53,-10>, <-10,-60>, <-60,-30>, <-53,-8>, <-8,-60>, <-53,-6>, <-6,-65>, <-65,-30>, <-6,-72>, <-72,-30>, <-6,-4>, <-4,-30>, <-60,-41>, <-6,-60>, <-60,-24>, <-24,0>, <-88,-10>, <-88,-8>, <-88,-6>, <b,ĉ>, <c,r>, <h,u>, <l,-75>, <r,-75>, <u,ĝ>, <i,-75>, <0,l>, <c,d>, <o,-75>, <g,m>, <h,-75>, <h,v>, <-75,m>, <u,j>, <-75,t>, <y,s>, <-75, >, <0,m>, <t,-75>, <f,o>, <-75,k>, <f,t>, <r,ŭ>, <-75,o>, <i,r>, <-75,i>, <0, >, <d,p>, < ,o>, < ,-75>, <p,s>, <w,ĉ>, <e,-75>, <f,-75>, <w,m>, <l,k>, <a,l>, <g,a>, <d,a>, <b,ŭ>, <y,-69>, <-69,0>, <g,b>, <-69,n>, <0,-69>, <s,ĝ>, <u,s>, <a,-69>, <y,r>, <-69,i>, <-69,o>, <a,-32>, <-32,0>, <d,j>, <-62,-62>, <-62,ŭ>, <0,-62>, <b,s>, <a,-62>, <-62,0>, <t,-62>, <r,k>, <r,p>, <h,f>, <-62,e>, <e,-46>, <-46,-56>, <-56,0>, <-46,e>, <-19,t>, <0,_>, <0,-46>, <0,-19>, <p,-46>, <l,-59>, <-59,0>, <-46,p>, <-59,a>, <0,#>, <0,-59>, <#,p>, <-46,o>, <-59,#>, <y,n>, <-46,a>, <-19,-46>, <r,-19>, <-19,0>, <w,a>, <v,-19>, <h,-46>, <e,-19>, <r,-46>, <-46,-19>, <o,-49>, <-49,f>, <0,-49>, <a,-68>, <-68,f>, <o,-68>, <-49,a>, <r,u>, <t,-49>, <c,a>, <d,-49>, <h,-49>, <l,d>, <t,p>, <e,-49>, <h,o>, <s,-49>, <-63,e>, <t,-63>, <c,e>, <o,-63>, <d,-63>, <h,-63>, <-63,-63>, <g,e>, <-63,p>, <-46,ŭ>, <-19,-30>, <y,-19>, <-46,-30>, <-59,-46>, <-24,-59>, <-30,-59>, <-41,-59>, <f,l>, <-46,-46>, <-59,-59>, <-46,t>, <-30,-46>, <f,m>, <-46,m>, <-59,u>, <-41,l>, <c,l>, <-59, >, <-30,d>, <f,a>, < ,-46>, <o,-59>, <-46,l>, <-59,i>, <-46,i>, <o,-46>, <f,-59>, <p,b>, <-46,n>, <-19,i>, <-46,d>, <t,-46>, <-46,-61>, <-61,0>, <-61,u>, <0,-61>, <c,-46>, <h,-59>, <n,-46>, <y,-59>, <-46,-41>, <u, >, <a, >, <y,u>, <-59,t>, <c,m>, <m,-59>, <o,f>, <g,ĉ>, <-46, >, <-59,d>, <q,s>, <i,f>, <t,-59>, <-15,-46>, <0,-15>, <-15,-15>, <-46,-15>, <-15,0>, <h,ŝ>, <i,ĝ>, <r,-49>, <',a>, <s,-46>, <v,t>, <y,-20>, <-20,0>, <-20,-20>, <l,-20>, <e,-20>, <-20,e>, <0,-20>, <j,t>, < ,-20>, <-20,m>, <-20,a>, <0,j>, <-94,r>, <0,-94>, <-63,r>, <n,-94>, <_,0>, <-94,0>, <-94,-94>, <o,-94>, <e,-94>, <-94,a>, <0,ŭ>, <w,s>, <h,-94>, <d,b>, <a,s>, <b,u>, <o,p>, <b,r>, <u,-94>, <d,-94>, <t,ŭ>, <i,-94>, <-94,e>, <s,-94>, <t,-94>, <p,l>, <w,-94>, <c,ĉ>, <-63,ŭ>, <d,ŭ>, <r,ĉ>, <-94,ŭ>, <',d>, <-23,-23>, <-23,e>, <0,-23>, <b,d>, <f,-94>, <r,m>, <x,r>, <p,-94>, <-94,m>, <m,n>, <r,-94>, <c,ŭ>, <b,p>, <a,-94>, < ,-94>, <c,n>, <g,n>, <a,c>, <s,o>, <r,c>, <d, >, <-94,l>, <f,c>, <-94,n>, <c,f>, <p,e>, <f,n>, <-94,t>, < ,s>, <x,-94>, <p,r>, < ,k>, <u,m>, <s,g>, <m,-94>, <-94,d>, <i,p>, <p,t>, <d,l>, <w,r>, <W,V>, <-9,-9>, <-80,-80>, <N,A>, <L,N>, <b,c>, <t,L>, <o,b>, <A,i>, <r,ĝ>, <y,-9>, <-9,-80>, <-80,0>, <s,-9>, <t,-27>, <-9,0>, <-27,0>, <-27,-27>, <-9,o>, <-27,-9>, <0,-27>, <g,ĝ>, <-9,-27>, <s,j>, <c,z>, <t,A>, <A,j>, <n,-9>, <d,-27>, <a,-9>, <E,A>, <d,-9>, <u,ŭ>, <C,Ĉ>, <p,f>, <a,b>, <y,o>, <s,z>, <I,B>, <u,o>, <m,-9>, <s,r>, <A,-9>, <i,-27>, <z,c>, <U,B>, < ,-9>, <K,-27>, <t,B>, <U,i>, <i,-9>, <K,0>, <G,B>, <B,-9>, <r,-27>, <s,ŝ>, <e,ŝ>, <d,i>, <r,-9>, <k,-27>, <-9,i>, <-27,o>, <P,F>, <p,n>, <e,-9>, <s,-27>, <t,F>, <p,-9>, <p,-27>, <-9,d>, <c,g>, <c,i>, <G,Ĝ>, <S,H>, <-9,n>, <-27,i>, <c,s>, <Y,J>, <I,j>, <l,-27>, <n,-27>, <a,g>, <G,K>, <a,->, <n,L>, <L,n>, <o,-9>, <Q,K>, < ,->, <h,->, < ,A>, <A,m>, <t,->, <h,A>, <A,e>, <m,r>, <c,-9>, <a,-27>, <l,A>, <H,N>, <-27,d>, <t,N>, <N,r>, <l,-9>, <y,g>, <y,j>, <-22,-9>, <0,-22>, <i,->, <A,r>, <W,K>, <z,e>, <e,ĥ>, <R,-27>, <k,o>, <k,h>, < ,f>, <A,f>, <f,r>, <E,O>, <A,t>, <f,->, <r,A>, <-9,k>, <W,O>, <s,c>, <c,A>, <a,f>, <-9,r>, <z,l>, < ,-27>, <S,-30>, <t,U>, <U,o>, <t,-30>, <S,0>, <A,0>, <n,g>, <g,t>, <c,t>, <i,z>, <i,g>, <J,Ĵ>, < ,K>, <C,a>, < ,D>, <D,a>, <y,l>, <d,->, <e,I>, <I,s>, <h,K>, <C,r>, <h,D>, <D,k>, <k,t>, <t,-9>, <e,-27>, <n,V>, <-9,g>, <L,M>, <S,r>, <x, >, <o,S>, <c,ŝ>, <g,j>, <J,j>, <F,f>, <M,m>, <A,a>, <S,s>, <O,o>, <N,n>, <D,d>, <M,l>, <y,-68>, <T,m>, <W,m>, <T,ĵ>, <d,-68>, <F,v>, <-68,d>, <S,d>, <y,ĉ>, <C,k>, <G,g>, <G,ĝ>, <c,-49>, <i,-49>, <d,h>, <-9,-22>, <-22,0>, <B,b>, <H,h>, <E,o>, <W,o>, <-49,i>, <E,a>, <A,u>, <m,s>, <E,-49>, <h,g>, <o,->, <-,-49>, <I,i>, <n,-68>, < ,z>, <s,->, <a,z>, <s,-68>, <C,ĉ>, <D,n>, <-49,l>, <0,-72>, <-68,n>, <0,-65>, <E,e>, <A,l>, <k,f>, <0,g>, <P,f>, <i,-68>, <P,k>, <-49,g>, <-68,g>, <S,h>, <-49,d>, <M,i>, <m,a>, <L,l>, <-68,a>, <U,-9>, <k,r>, <K,k>, <C,c>, <V,v>, <Y,j>, <h,-68>, <G,k>, <j,ĝ>, <k,ĥ>, <U,u>, <q,k>, <a,ŝ>, <-68,i>, <-68,-65>, <o,ŭ>, <w,-68>, <o,-65>, <h,-65>, <C,l>, <S,l>, <p,a>, <n,h>, <h,p>, <-63,a>, <t,h>, <l,s>, <S,-63>, <P,l>, <e,g>, <O,l>, <-63,t>, <G,l>, <-63,k>, <v,o>, <ç,i>, <F,l>, <n,f>, <0,c>, <I,l>, <u,f>, <d,s>, <R,l>, <0,ĉ>, <E,l>, <E,g>, <l,-63>, <B,l>, <E,n>, <i,-63>, <l,t>, <-63,o>, <m,g>, <-63,m>, <D,l>, <-63,d>, <m,f>, < ,-63>, <G,0>, <W,l>, <l,g>, <q,e>, <N,l>, <v,l>, <H,l>, <g,h>, <l,b>, <U,l>, <z,a>, <-63,ĥ>, <-63,v>, <r,b>, <T,l>, <K,l>, <-63,b>, <z, >, <r,h>, <w,b>, <e,-65>, <-68,-72>, <m,-68>, <a,-65>, <k,p>, <s,-72>, <d,-65>, <d,-72>, <r,-72>, <t,-65>, <p,v>, <w,-72>, <-68,b>, <n,ĝ>, <q,r>, <s,-65>, <-68,t>, <p,d>, <-68,-45>, <0,-45>, <-68,r>, <g,s>, <n,-72>, <-68,j>, <n,-65>, <p,-68>, <p,g>, <p,u>, < ,-68>, <c,b>, <-68,s>, <a,h>, <t,-68>, <t,-69>, <k, >, <o,-69>, <k,ĉ>, <k,n>, <J, >, <l,J>, <-69, >, <0,b>, <g,u>, <l,-69>, <m,j>, <g,i>, <k,b>, <b,g>, <-49,n>, <-63, >, <M,s>, <r,->, <-22,-22>, <-60,-60>, <M,f>, <-81,-48>, <-48,0>, <A,P>, <D,.>, <-81,K>, <0,.>, <0,K>, <-68,K>, <-30,.>, <A,p>, <-63,K>, <B,a>, <C,.>, <E,K>, <-63,E>, <B,K>, <C,E>, <E,-63>, <c,-94>, <p,.>, <p,-63>, <x,0>, <.,0>, <-63,s>, <w,c>, <y,-49>, <d,m>, <W,a>, <I,n>, <W, >, <I,d>, <I,m>, <r,f>, <0,ĝ>, <-68,e>, <-16,e>, <w,t>, <-49,e>, <h,ĉ>, <-63,i>, <x,o>, <k,d>, <0,p>, <v,ĉ>, <f,ĵ>, <r,ĵ>, <r,-91>, <w,i>, <c,-91>, <d,ĉ>, <r,-63>, <e,-42>, <-42,-63>, <n,->, <-,-63>, <j,n>, <f,s>, <v,-63>, <m,u>, <r,j>, <a,ŭ>, <h,ŭ>, <y,ŭ>, <y,h>, <z,t>, <b,t>, <-49,ĝ>, <w,-63>, <-68,u>, <#,a>, <#,r>, <o,ĝ>, <t,-16>, <c,v>, <p,z>, <g,p>, <b,-68>, <w,g>, <l,-68>, <-68,k>, <o,g>, <u,p>, < ,g>, <#,ŭ>, <u,g>, <r,ŝ>, <f,-16>, <t,f>, <#,j>, <d,-16>, <#,0>, <p,k>, <#,o>, <t,g>, <g,-16>, <-16,a>, <i,-16>, <k,#>, <e,z>, <#,g>, <c,-16>, <y,-16>, <#,v>, <m,v>, <b,-16>, <h,-16>, <l,-16>, <w,-16>, <s,-16>, <b,v>, <q,0>, <a,#>, <#,m>, <s,ŭ>, <s,b>, <-,0>, <m,-63>, <i,ĉ>, <j,ĉ>, <n,z>, <c,-63>, <d,g>, <x,p>, <I,a>, <l,c>, <l,f>, <j,p>, <l,h>, <j,0>, <w,p>, < ,ŝ>, <u,z>, <o,ŝ>, <t,z>, <-16,n>, <é,o>, <p,o>, <0,ĥ>, <-68,ĥ>, <s,ĥ>, <k,-68>, <.,n>, <a,-46>, <l,-19>, <-19,-19>, <g,ŭ>, <l,ŭ>, <-94,o>, <-94,j>, <g,-94>, <-63,->, <0,f>, <m,-91>, <f,j>, <f,h>, <m,ĉ>, <m,c>, <-63,c>, <f,-63>, <-75,n>, <-75,ŭ>, <g,-75>, <s,h>, <z,g>, <g,-68>, <A,o>, <S,-9>, <A,-27>, <U,S>, <S,o>, <S,v>, <R,e>, <-9,t>, <-27,->, <0,U>, <t,S>, <U,t>, <S,->, <S,U>, <R,n>, <K,r>, <-27,t>, <K,o>, <-27,n>, <V,A>, <A,V>, <T,I>, <-93,o>, <0,-93>, <u,ĉ>, <',0>, <é,0>, <â,0>, <-9,j>, <t,P>, <P,n>, <k,j>, <t,M>, <M,t>, <',ŭ>, <p,h>, <z,i>, <a,ĝ>, <-16,t>, <-16,g>, <-16,k>, <m,-16>, <c,ĝ>, <u,c>, <y,c>, <e,-16>, <q,n>, <q,i>, <-16,p>, <-16,o>, <v,ĵ>, <j,u>, <d,v>, <-49,r>, <r,g>, <-49,t>, <-16,j>, <m,k>, <-68,p>, <o,h>, <k,l>, <l,j>, <-16,s>, <-68,z>, <g,-63>, <u,-49>, <h,z>, <C, >, <C,e>, <C,t>, <0,ĵ>, <-68,m>, <-,-68>, <x,l>, <p,j>, <p,c>, <i,ĵ>, <x,i>, <-68,ĵ>, <k,s>, <k,m>, <m,b>, <o,c>, <m,z>, <m,ŭ>, <y,ĵ>, <k,-16>, <0,z>, <-68,c>, <v,r>, <-49,u>, <x,a>, <k,u>, <-49,b>, <m,ĝ>, <-16,c>, <u,-68>, <f,-68>, <v,-16>, <g,c>, <b,z>, <l,ĵ>, <-68,v>, <-16,v>, <k,c>, <-16,u>, <-68,ĝ>, <d,ĝ>, <c,j>, <z,o>, <t,ŝ>, <d,ŝ>, <b,ĝ>, <o,z>, <-16,l>, <t,ĝ>, <l,ĝ>, <-49,m>, <b,ŝ>, <k,ĝ>, <y,m>, <-16,m>, <z,r>, <i,ŝ>, <c,ĵ>, <-16,z>, <t,ĵ>, <e,h>, <q,o>, <s,ĵ>, <-,v>, <p,-49>, <i,c>, <c,ĥ>, <s,ĉ>, <k,z>, <-68,ĉ>, <c,h>, <w,ŭ>, <q,v>, <-16,ŭ>, <b,-49>, <b,-63>, <d,c>, <-49,s>, <n,ŝ>, <m,ĵ>, <q,-68>, <v,g>, <v,m>, <-49,o>, <l,z>, <-49,z>, <v,b>, <-16,ĝ>, <-63,g>, <v,-49>, <-16,d>, <g,f>, <a,ĵ>, <y,ĝ>, <d,z>, <z,k>, <p,ĵ>, <y,z>, <j,i>, <q,g>, <x,u>, <x,n>, <x,f>, <x,d>, <x,t>, <f,g>, <-49,c>, <f,ŝ>, <f,b>, <f,ĉ>, <u,ĵ>, <v,-68>, <w,f>, <g,-49>, <y,ŝ>, <w,ĵ>, <f,-49>, <m,-49>, <l,ŝ>, <-,i>, <k,-49>, <q,a>, <b,ĵ>, <j,-27>, <-,l>, <S, >, <l,Ŝ>, <z,u>, <h,b>, <h,ĝ>, <g,ĵ>, <-63,ĝ>, <e,ĵ>, <K,u>, <u,ŝ>, <h,-9>, <i,h>, <j,l>, <j,b>, <-,g>, <j,ĵ>, <j,a>, <j,k>, <j,ŝ>, <j,v>, <j,s>, <k,ŝ>, <k,g>, <w,ĝ>, <w,z>, <y,b>, <n,ĵ>, <d,ĵ>, <q,u>, <-30,-68>, <-41,e>, <x,c>, <m,h>, <w,j>, <-49,v>, <x,m>, <z,ŝ>, <p,ŝ>, <v,ĝ>, <-94,s>, <0,ŝ>, <q,d>, <-49,p>, <f,z>, <x,-68>, <-29,-16>, <0,-11>, <-11,-16>, <l,ĉ>, <',-49>, <p,ĝ>, <p,ĉ>, <v,h>, <z,m>, <b,j>, <f,ĝ>, <-49,k>, <q,p>, <q,c>, <e,->, <q,t>, <-68,ŝ>, <-16,ĉ>, <-,c>, <-16,b>, <z,-16>, <-,a>, <j,m>, <q,-63>, <-16,ŝ>, <-68,h>, <v,f>, <v,ŝ>, <-,r>, <w,h>, <-41,o>, <y,f>, <w,-49>, <k,ŭ>, <-68,ŭ>, <',-68>, <v,c>, <-68, >, <ç,s>, <n,-30>, <â,a>, <é,t>, <è,e>, <-,f>, <-41,-41>, <s,-41>, <q,ŝ>, <j,d>, <-49,ĉ>, <ï,i>, <z,-49>, <-16,f>, <-63,j>, <z,-63>, <z,-68>, <v,z>, <g,ŝ>, <-63,z>, <x,g>, <b,h>, <h,ĵ>, <-,z>, <c,->, <q,-49>, <x,v>, <-49,h>, <-16,h>, <k,v>, <w,ŝ>, <z,v>, <q,m>, <-49,ŝ>, <z,p>, <f,ŭ>, <z,d>, <z,b>, <q,j>, <-,p>, <j,e>, <j,g>, <J,I>, <-9,b>, <j,r>, <L,S>, <0,h>, <z,0>, <f,R>, <e,-41>, <-,m>, <k,ĵ>, <q,l>, <p,ŭ>, <p,->, <-9,a>, <-63,ŝ>, <m,ŝ>, <-49,->, <z,f>, <z,s>, <ú,u>, <-72,-72>, <X,K>, <-65,-65>, <',e>, <',-63>, <x,-63>, <j,ŭ>, <m,->, <h,ĥ>, <u,h>, <j,f>, <j,h>, <Z,e>, <k,-63>, <-68,->, <q,ĉ>, <q,f>, <-49,ĵ>, <q,ŭ>, <-,ŭ>, <x,z>, <m,ĥ>, <O,0>]

    return a_new;
  }

  @Override
  public String toString() {
    return slexicinv.toString();
  }
  private static final int MAX_CHARCACHE = 200;
  private static final String[] CHARCACHE = new String[MAX_CHARCACHE];
  private static final String[] UPCHARCACHE = new String[MAX_CHARCACHE];

  static {
    char[] ca = new char[MAX_CHARCACHE];
    for (char i = 0; i < MAX_CHARCACHE; i++)
      ca[i] = i;
    String str = new String(ca);
    for (int i = 1; i < MAX_CHARCACHE; i++)
      CHARCACHE[i] = str.substring(i, i + 1);
    String upstr = str.toUpperCase();
    for (int i = 1; i < MAX_CHARCACHE; i++)
      UPCHARCACHE[i] = upstr.substring(i, i + 1);
    CHARCACHE[0] = UPCHARCACHE[0] = "";
  }

  public static boolean isUpperCase(int val) {
    return Character.isUpperCase(val);
  }

  public static boolean isSpaceChar(char val) {
    return Character.isSpaceChar(val);
  }

  public static boolean isWhitespace(char val) {
    return Character.isWhitespace(val);
  }

  public static boolean isLetter(char charAt) {
    return Character.isLetter(charAt);
  }

  public static char toLowerCase(char charAt) {
    return Character.toLowerCase(charAt);
  }

  public static int toLowerCase(int val) {
    return Character.toLowerCase(val);
  }

  public static char toUpperCase(char charAt) {
    return Character.toUpperCase(charAt);
  }

  public String getSymbol(int symbol) {
    return getSymbol(symbol, false);
  }

  /**
   * Find a symbol symbol
   *
   * @param symbol the symbol to be added
   * @param uppercase true if we want an uppercase symbol
   * @return the symbol as a string
   */
  public String getSymbol(int symbol, boolean uppercase) {

    //System.err.println("symbol = " + symbol);
    if (symbol == 0) {
      return "";
    }

    if (symbol < 0) {
      return slexicinv.get(-symbol - 1);
    }

    // re-use strings
    if (symbol < MAX_CHARCACHE) {
      return uppercase ? UPCHARCACHE[symbol] : CHARCACHE[symbol];
    }


    if (!uppercase) {
      return String.valueOf((char) symbol);
    } else {
      return String.valueOf(Character.toUpperCase((char) symbol));
    }
  }

  /**
   * Used do find and hide Control Symbols in flagmatch and compounding
   *
   * @return all the symbols, actually. It's the callers responsibility to filer out those he dont need
   */
  public Collection<String> getFlagMatchSymbols() {
    return slexicinv;
  }

  /**
   * Sets an already existing symbol to represent a new value
   * Used to avoid decomposition symbols in output in flagmatch and compounding
   */
  public void setSymbol(int symbol, String newSymbolString) {
    if (symbol >= 0) {
      throw new IllegalArgumentException("Symbol may not be a normal character:" + symbol);
    } else {
      slexicinv.set(-symbol - 1, newSymbolString);
    }
  }

  /**
   * Checks whether a symbol is a tag or not
   *
   * @param symbol the code of the symbol
   * @return true if the symbol is a tag
   */
  public static boolean isTag(int symbol) {
    return symbol < 0;
  }

  public IntegerPair decode(int code) {
    return spairinv.get(code);
  }
}
