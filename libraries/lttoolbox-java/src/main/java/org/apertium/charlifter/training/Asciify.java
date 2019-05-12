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
    s = s.replaceAll("\u02d0", ":");
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
    s = s.replaceAll("[\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u0100\u0102\u0104\u212b\u01cd\u01de\u01e0\u01fa\u0200\u0202\u0226\u1e00\u1ea0\u1ea2\u1ea4\u1ea6\u1ea8\u1eaa\u1eac\u1eae\u1eb0\u1eb2\u1eb4\u1eb6]", "A");
    s = s.replaceAll("[\u00c6\u01e2\u01fc]", "AE");
    s = s.replaceAll("[\u1e02\u0181\u0182\u0184\u1e04\u1e06]", "B");
    s = s.replaceAll("[\u00c7\u0106\u0108\u010a\u010c\u0187\u1e08]", "C");
    s = s.replaceAll("[\u00d0\u010e\u0110\u1e0a\u0189\u018a\u018b\u1e0c\u1e0e\u1e10\u1e12]", "D");
    s = s.replaceAll("[\u01c4\u01f1]", "DZ");
    s = s.replaceAll("[ǅǲ]", "Dz");
    s = s.replaceAll("[\u00c8\u00c9\u00ca\u00cb\u0112\u0114\u0116\u0118\u011a\u0190\u018e\u018f\u0204\u0206\u0228\u1e14\u1e16\u1e18\u1e1a\u1e1c\u1eb8\u1eba\u1ebc\u1ebe\u1ec0\u1ec2\u1ec4\u1ec6\u018f]", "E");
    s = s.replaceAll("[ḞƑ]", "F");
    s = s.replaceAll("[\u011c\u011e\u0120\u0122\u0193\u0194\u01e4\u01e6\u01f4\u1e20]", "G");
    s = s.replaceAll("[\u0124\u0126\u021e\u1e22\u1e24\u1e26\u1e28\u1e2a]", "H");
    s = s.replaceAll("Ƕ", "Hv");
    s = s.replaceAll("[\u00cc\u00cd\u00ce\u00cf\u0128\u012a\u012c\u012e\u0130\u0196\u0197\u01cf\u0208\u020a\u1e2c\u1e2e\u1ec8\u1eca]", "I");
    s = s.replaceAll("Ĳ", "IJ");
    s = s.replaceAll("Ĵ", "J");
    s = s.replaceAll("[\u0136\u212a\u0198\u01e8\u1e30\u1e32\u1e34]", "K");
    s = s.replaceAll("[\u0139\u013b\u013d\u013f\u0141\u1e36\u1e38\u1e3a\u1e3c]", "L");
    s = s.replaceAll("Ǉ", "LJ");
    s = s.replaceAll("ǈ", "Lj");
    s = s.replaceAll("[\u1e40\u019c\u1e3e\u1e42]", "M");
    s = s.replaceAll("[\u00d1\u0143\u0145\u0147\u014a\u019d\u01f8\u1e44\u1e46\u1e48\u1e4a]", "N");
    s = s.replaceAll("Ǌ", "NJ");
    s = s.replaceAll("ǋ", "Nj");
    s = s.replaceAll("[\u00d2\u00d3\u00d4\u00d5\u00d6\u00d8\u014c\u014e\u0150\u2126\u0186\u019f\u01a0\u01d1\u01ea\u01ec\u01fe\u020c\u020e\u022a\u022c\u022e\u0230\u1e4c\u1e4e\u1e50\u1e52\u1ecc\u1ece\u1ed0\u1ed2\u1ed4\u1ed6\u1ed8\u1eda\u1edc\u1ede\u1ee0\u1ee2]", "O");
    s = s.replaceAll("Œ", "OE");
    s = s.replaceAll("Ƣ", "OI");
    s = s.replaceAll("[\u1e56\u01a4\u1e54]", "P");
    s = s.replaceAll("[\u0154\u0156\u0158\u01a6\u0210\u0212\u1e58\u1e5a\u1e5c\u1e5e]", "R");
    s = s.replaceAll("[ŚŜŞŠȘṠƧƩṢṤṦṨ]", "S");
    s = s.replaceAll("[ŢŤŦȚṪƬƮṬṮṰ]", "T");
    s = s.replaceAll("Þ", "Th");
    s = s.replaceAll("[\u00d9\u00da\u00db\u00dc\u0168\u016a\u016c\u016e\u0170\u0172\u01af\u01b1\u01d3\u01d5\u01d7\u01d9\u01db\u0214\u0216\u1e72\u1e74\u1e76\u1e78\u1e7a\u1ee4\u1ee6\u1ee8\u1eea\u1eec\u1eee\u1ef0]", "U");
    s = s.replaceAll("[\u1e7c\u1e7e\u01b2]", "V");
    s = s.replaceAll("[\u0174\u1e80\u1e82\u1e84\u1e86\u1e88]", "W");
    s = s.replaceAll("[\u1e8a\u1e8c]", "X");
    s = s.replaceAll("[\u00dd\u0176\u0178\u1ef2\u01b3\u0232\u1e8e\u1ef4\u1ef6\u1ef8]", "Y");
    s = s.replaceAll("[\u0179\u017b\u017d\u01b5\u01b7\u01b8\u01ee\u0224\u1e90\u1e92\u1e94]", "Z");
    s = s.replaceAll("ˆ", "^");
    s = s.replaceAll("[ʻʽ]", "`");
    s = s.replaceAll("[\u00aa\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u0101\u0103\u0105\u01ce\u01df\u01e1\u01fb\u0201\u0203\u0227\u1e01\u1e9a\u1ea1\u1ea3\u1ea5\u1ea7\u1ea9\u1eab\u1ead\u1eaf\u1eb1\u1eb3\u1eb5\u1eb7]", "a");
    s = s.replaceAll("[æǣǽ]", "ae");
    s = s.replaceAll("[\u1e03\u0180\u0183\u0185\u0253\u0304\u1e05\u1e07]", "b");
    s = s.replaceAll("[\u00e7\u0107\u0109\u010b\u010d\u0188\u1e09]", "c");
    s = s.replaceAll("[\u00f0\u010f\u0111\u1e0b\u018c\u018d\u0257\u1e0d\u1e0f\u1e11\u1e13\u0256]", "d");
    s = s.replaceAll("[ǆǳ]", "dz");
    s = s.replaceAll("[\u00e8\u00e9\u00ea\u00eb\u0113\u0115\u0117\u0119\u011b\u025b\u01dd\u0205\u0207\u0229\u1e15\u1e17\u1e19\u1e1b\u1e1d\u1eb9\u1ebb\u1ebd\u1ebf\u1ec1\u1ec3\u1ec5\u1ec7\u0259]", "e");
    s = s.replaceAll("[\u0192\u1e1f\u1e9b]", "f");
    s = s.replaceAll("ﬀ", "ff");
    s = s.replaceAll("ﬃ", "ffi");
    s = s.replaceAll("ﬄ", "ffl");
    s = s.replaceAll("\ufb01", "fi");
    s = s.replaceAll("ﬂ", "fl");
    s = s.replaceAll("[\u011d\u011f\u0121\u0123\u01e5\u01e7\u01f5\u1e21\u0263]", "g");
    s = s.replaceAll("[\u0125\u0127\u021f\u1e23\u1e25\u1e27\u1e29\u1e2b\u1e96]", "h");
    s = s.replaceAll("ƕ", "hv");
    s = s.replaceAll("[\u00ec\u00ed\u00ee\u00ef\u0129\u012b\u012d\u012f\u0131\u01d0\u0209\u020b\u1e2d\u1e2f\u1ec9\u1ecb\u0269]", "i");
    s = s.replaceAll("ĳ", "ij");
    s = s.replaceAll("[ĵǰ]", "j");
    s = s.replaceAll("[\u0137\u0138\u0199\u01e9\u1e31\u1e33\u1e35]", "k");
    s = s.replaceAll("[\u013a\u013c\u013e\u0140\u0142\u2113\u019a\u019b\u1e37\u1e39\u1e3b\u1e3d]", "l");
    s = s.replaceAll("ǉ", "lj");
    s = s.replaceAll("[\u1e41\u1e3f\u1e43]", "m");
    s = s.replaceAll("[\u00f1\u0144\u0146\u0148\u0149\u014b\u207f\u019e\u01f9\u1e45\u1e47\u1e49\u1e4b\u0272]", "n");
    s = s.replaceAll("ǌ", "nj");
    s = s.replaceAll("[\u00ba\u00f2\u00f3\u00f4\u00f5\u00f6\u00f8\u014d\u014f\u0151\u0254\u01a1\u01d2\u01eb\u01ed\u01ff\u020d\u020f\u022b\u022d\u022f\u0231\u1e4d\u1e4f\u1e51\u1e53\u1ecd\u1ecf\u1ed1\u1ed3\u1ed5\u1ed7\u1ed9\u1edb\u1edd\u1edf\u1ee1\u1ee3]", "o");
    s = s.replaceAll("œ", "oe");
    s = s.replaceAll("ƣ", "oi");
    s = s.replaceAll("\u1e57\u01a5\u1e55]", "p");
    s = s.replaceAll("\u0155\u0157\u0159\u0211\u0213\u1e59\u1e5b\u1e5d\u1e5f]", "r");
    s = s.replaceAll("[\u015b\u015d\u015f\u0161\u017f\u0219\u1e61\u01a8\u1e63\u1e65\u1e67\u1e69]", "s");
    s = s.replaceAll("ß", "ss");
    s = s.replaceAll("[ﬅﬆ]", "st");
    s = s.replaceAll("[\u0163\u0165\u0167\u021b\u1e6b\u01ab\u01ad\u1e6d\u1e6f\u1e71\u1e97]", "t");
    s = s.replaceAll("þ", "th");
    s = s.replaceAll("[\u00b5\u00f9\u00fa\u00fb\u00fc\u0169\u016b\u016d\u016f\u0171\u0173\u01b0\u01d4\u01d6\u01d8\u01da\u01dc\u0215\u0217\u1e73\u1e75\u1e77\u1e79\u1e7b\u1ee5\u1ee7\u1ee9\u1eeb\u1eed\u1eef\u1ef1]", "u");
    s = s.replaceAll("[\u1e7d\u1e7f\u028b]", "v");
    s = s.replaceAll("[\u0175\u1e81\u1e83\u1e85\u1e87\u1e89\u1e98]", "w");
    s = s.replaceAll("[\u1e8b\u1e8d]", "x");
    s = s.replaceAll("[\u00fd\u00ff\u0177\u1ef3\u01b4\u0233\u1e8f\u1e99\u1ef5\u1ef7\u1ef9]", "y");
    s = s.replaceAll("[źżžƶƹƺǯȥẑẓẕ]", "z");

    return s;
  }
}
