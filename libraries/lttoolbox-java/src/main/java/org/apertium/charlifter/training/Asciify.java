/*
 * Copyright 2010 Jimmy O'Regan
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
package org.apertium.charlifter.training;

/**
 * This class should be replaced, using the same mechanism as ACX
 * Starting with it, for simplicity's sake
 *
 * @author jimregan
 */
//@Deprecated  // remove deprecation warning until someone cares to look at it. Jacob
public class Asciify {
  /**
   * # data taken from the table in transtab package:
   * # http://www.cl.cam.ac.uk/~mgk25/download/transtab.tar.gz
   *
   * @param s UTF-8 string
   * @return ASCII-fied version
   */
  public static String toascii(String s) {
    // first batch are characters to delete - these need to get
    // added to charlifter script too...
    s = s.replaceAll("[\\x{A8}\\x{B4}\\x{B8}]", "");  // wipe out latin-1 diacriticals
    s = s.replaceAll("[\\p{M}]", "");  // important to wipe out ALL marks (start at 0x0300)
    s = s.replaceAll("[\\x{02B9}-\\x{02DD}]", "");  // "modifier letters"

    s = s.replaceAll("[\\x{02BB}]", "'");  // correct "okina" in Hawaiian, Samoan, etc.
    s = s.replaceAll("[ʹʼ]", "'");
    s = s.replaceAll("ˌ", ",");
    s = s.replaceAll("ː", ":");
    // some latin-1 range stuff:
    s = s.replaceAll("©", "(C)");
    s = s.replaceAll("®", "(R)");
    s = s.replaceAll("«", "<<");
    s = s.replaceAll("»", ">>");
    s = s.replaceAll("¼", "1/4");
    s = s.replaceAll("½", "1/2");
    s = s.replaceAll("¾", "3/4");
    s = s.replaceAll("±", "+/-");
    s = s.replaceAll("[\\x{A0}]", " ");  // nbsp
    s = s.replaceAll("[\\x{A1}]", "!");  // inverted !
    s = s.replaceAll("[\\x{A7}]", "\\S");  // section sign
    s = s.replaceAll("°", "^o");
    s = s.replaceAll("[\\x{B2}]", "^2");
    s = s.replaceAll("[\\x{B3}]", "^3");
    s = s.replaceAll("[\\x{B9}]", "^1");
    s = s.replaceAll("[\\x{B7}]", "*");         // MIDDLE DOT
    // Unicode general punc. block
    s = s.replaceAll("[\\x{2000}-\\x{200b}]", " ");   // all spaces even if thin or zero width!
    s = s.replaceAll("[\\x{200c}-\\x{200f}]", "");   // L to R, R to L, markers etc.
    s = s.replaceAll("[\\x{2010}-\\x{2015}]", "-");     // hyphens
    s = s.replaceAll("\\x{2016}", "||");     // Double Vertical Line
    s = s.replaceAll("\\x{2017}", "=");     // Double Low Line
    s = s.replaceAll("\\x{2018}", "'");     // LEFT SINGLE QUOTATION MARK
    s = s.replaceAll("\\x{2019}", "'");     // RIGHT SINGLE QUOTATION MARK
    s = s.replaceAll("\\x{201a}", ",");     // SINGLE LOW 9 QUOTATION MARK
    s = s.replaceAll("\\x{201b}", "'");     // SINGLE HIGH REVERSED 9 QUOTATION MARK
    s = s.replaceAll("\\x{201c}", "\"");     // LEFT DOUBLE QUOTATION MARK
    s = s.replaceAll("\\x{201d}", "\"");     // RIGHT DOUBLE QUOTATION MARK
    s = s.replaceAll("\\x{201e}", ",,");    // DOUBLE LOW 9 QUOTATION MARK
    s = s.replaceAll("\\x{2022}", "*");     // BULLET
    s = s.replaceAll("\\x{2025}", "..");
    s = s.replaceAll("\\x{2026}", "...");   // HORIZONTAL ELLIPSIS
    s = s.replaceAll("\\x{2027}", ".");
    s = s.replaceAll("\\x{2030}", "%");     // MILLE SIGN (close enough)
    s = s.replaceAll("[\\x{20AC}]", "EUR");
    s = s.replaceAll("[ÀÁÂÃÄÅĀĂĄÅǍǞǠǺȀȂȦḀẠẢẤẦẨẪẬẮẰẲẴẶ]", "A");
    s = s.replaceAll("[ÆǢǼ]", "AE");
    s = s.replaceAll("[ḂƁƂƄḄḆ]", "B");
    s = s.replaceAll("[ÇĆĈĊČƇḈ]", "C");
    s = s.replaceAll("[ÐĎĐḊƉƊƋḌḎḐḒ]", "D");
    s = s.replaceAll("[ǄǱ]", "DZ");
    s = s.replaceAll("[ǅǲ]", "Dz");
    s = s.replaceAll("[ÈÉÊËĒĔĖĘĚƐƎƏȄȆȨḔḖḘḚḜẸẺẼẾỀỂỄỆƏ]", "E");
    s = s.replaceAll("[ḞƑ]", "F");
    s = s.replaceAll("[ĜĞĠĢƓƔǤǦǴḠ]", "G");
    s = s.replaceAll("[ĤĦȞḢḤḦḨḪ]", "H");
    s = s.replaceAll("Ƕ", "Hv");
    s = s.replaceAll("[ÌÍÎÏĨĪĬĮİƖƗǏȈȊḬḮỈỊ]", "I");
    s = s.replaceAll("Ĳ", "IJ");
    s = s.replaceAll("Ĵ", "J");
    s = s.replaceAll("[ĶKƘǨḰḲḴ]", "K");
    s = s.replaceAll("[ĹĻĽĿŁḶḸḺḼ]", "L");
    s = s.replaceAll("Ǉ", "LJ");
    s = s.replaceAll("ǈ", "Lj");
    s = s.replaceAll("[ṀƜḾṂ]", "M");
    s = s.replaceAll("[ÑŃŅŇŊƝǸṄṆṈṊ]", "N");
    s = s.replaceAll("Ǌ", "NJ");
    s = s.replaceAll("ǋ", "Nj");
    s = s.replaceAll("[ÒÓÔÕÖØŌŎŐΩƆƟƠǑǪǬǾȌȎȪȬȮȰṌṎṐṒỌỎỐỒỔỖỘỚỜỞỠỢ]", "O");
    s = s.replaceAll("Œ", "OE");
    s = s.replaceAll("Ƣ", "OI");
    s = s.replaceAll("[ṖƤṔ]", "P");
    s = s.replaceAll("[ŔŖŘƦȐȒṘṚṜṞ]", "R");
    s = s.replaceAll("[ŚŜŞŠȘṠƧƩṢṤṦṨ]", "S");
    s = s.replaceAll("[ŢŤŦȚṪƬƮṬṮṰ]", "T");
    s = s.replaceAll("Þ", "Th");
    s = s.replaceAll("[ÙÚÛÜŨŪŬŮŰŲƯƱǓǕǗǙǛȔȖṲṴṶṸṺỤỦỨỪỬỮỰ]", "U");
    s = s.replaceAll("[ṼṾƲ]", "V");
    s = s.replaceAll("[ŴẀẂẄẆẈ]", "W");
    s = s.replaceAll("[ẊẌ]", "X");
    s = s.replaceAll("[ÝŶŸỲƳȲẎỴỶỸ]", "Y");
    s = s.replaceAll("[ŹŻŽƵƷƸǮȤẐẒẔ]", "Z");
    s = s.replaceAll("ˆ", "^");
    s = s.replaceAll("[ʻʽ]", "`");
    s = s.replaceAll("[ªàáâãäåāăąǎǟǡǻȁȃȧḁẚạảấầẩẫậắằẳẵặ]", "a");
    s = s.replaceAll("[æǣǽ]", "ae");
    s = s.replaceAll("[ḃƀƃƅɓ̄ḅḇ]", "b");
    s = s.replaceAll("[çćĉċčƈḉ]", "c");
    s = s.replaceAll("[ðďđḋƌƍɗḍḏḑḓɖ]", "d");
    s = s.replaceAll("[ǆǳ]", "dz");
    s = s.replaceAll("[èéêëēĕėęěɛǝȅȇȩḕḗḙḛḝẹẻẽếềểễệə]", "e");
    s = s.replaceAll("[ƒḟẛ]", "f");
    s = s.replaceAll("ﬀ", "ff");
    s = s.replaceAll("ﬃ", "ffi");
    s = s.replaceAll("ﬄ", "ffl");
    s = s.replaceAll("ﬁ", "fi");
    s = s.replaceAll("ﬂ", "fl");
    s = s.replaceAll("[ĝğġģǥǧǵḡɣ]", "g");
    s = s.replaceAll("[ĥħȟḣḥḧḩḫẖ]", "h");
    s = s.replaceAll("ƕ", "hv");
    s = s.replaceAll("[ìíîïĩīĭįıǐȉȋḭḯỉịɩ]", "i");
    s = s.replaceAll("ĳ", "ij");
    s = s.replaceAll("[ĵǰ]", "j");
    s = s.replaceAll("[ķĸƙǩḱḳḵ]", "k");
    s = s.replaceAll("[ĺļľŀłℓƚƛḷḹḻḽ]", "l");
    s = s.replaceAll("ǉ", "lj");
    s = s.replaceAll("[ṁḿṃ]", "m");
    s = s.replaceAll("[ñńņňŉŋⁿƞǹṅṇṉṋɲ]", "n");
    s = s.replaceAll("ǌ", "nj");
    s = s.replaceAll("[ºòóôõöøōŏőɔơǒǫǭǿȍȏȫȭȯȱṍṏṑṓọỏốồổỗộớờởỡợ]", "o");
    s = s.replaceAll("œ", "oe");
    s = s.replaceAll("ƣ", "oi");
    s = s.replaceAll("ṗƥṕ]", "p");
    s = s.replaceAll("ŕŗřȑȓṙṛṝṟ]", "r");
    s = s.replaceAll("[śŝşšſșṡƨṣṥṧṩ]", "s");
    s = s.replaceAll("ß", "ss");
    s = s.replaceAll("[ﬅﬆ]", "st");
    s = s.replaceAll("[ţťŧțṫƫƭṭṯṱẗ]", "t");
    s = s.replaceAll("þ", "th");
    s = s.replaceAll("[µùúûüũūŭůűųưǔǖǘǚǜȕȗṳṵṷṹṻụủứừửữự]", "u");
    s = s.replaceAll("[ṽṿʋ]", "v");
    s = s.replaceAll("[ŵẁẃẅẇẉẘ]", "w");
    s = s.replaceAll("[ẋẍ]", "x");
    s = s.replaceAll("[ýÿŷỳƴȳẏẙỵỷỹ]", "y");
    s = s.replaceAll("[źżžƶƹƺǯȥẑẓẕ]", "z");

    return s;
  }
}
