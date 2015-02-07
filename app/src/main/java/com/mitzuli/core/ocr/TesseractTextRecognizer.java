/*
 * Copyright (C) 2015 Mikel Artetxe <artetxem@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.mitzuli.core.ocr;

import android.os.Build;
import android.os.Environment;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.tesseract.android.TessBaseAPI;

import com.mitzuli.BuildConfig;
import com.mitzuli.Image;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;


public class TesseractTextRecognizer implements TextRecognizer {

    // Debugging stuff
    public static boolean DEBUG = false; // TODO Temporary workaround to allow to manually enable debugging
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    private final String code;
    private final File packageDir;


    public TesseractTextRecognizer(String code, File packageDir) {
        this.code = code;
        this.packageDir = packageDir;
    }

    @Override
    public String recognizeText(Image image) throws Exception {
        // Debugging stuff
        final File debugDir = DEBUG ? new File(new File(Environment.getExternalStorageDirectory(), "mitzuli_ocr"), TIMESTAMP.format(new Date())) : null;
        if (DEBUG) debugDir.mkdirs();

        final TessBaseAPI tesseract = new TessBaseAPI();
        if (!tesseract.init(packageDir.getAbsolutePath(), code)) throw new Exception("Tesseract init failed.");
        final Image preprocessedImage = OcrPreprocessor.preprocess(image, debugDir);
        final Pix pix = preprocessedImage.toGrayscalePix();
        preprocessedImage.recycle();
        tesseract.setImage(pix);
        pix.recycle();

        final String text = tesseract.getUTF8Text();
        final StringBuilder sb = new StringBuilder();
        boolean lastEmpty = false;
        for (String s : text.split("\n")) {
            if (s.trim().length() == 0) {
                lastEmpty = true;
            } else {
                if (sb.length() > 0) sb.append(lastEmpty ? "\n\n" : " ");
                sb.append(s.trim());
                lastEmpty = false;
            }
        }

        // Debugging stuff
        if (DEBUG) {
            final PrintWriter output = new PrintWriter(new File(debugDir, "9_output.txt"));
            output.println(sb);
            output.close();
            final PrintWriter build = new PrintWriter(new File(debugDir, "build.txt"));
            build.println("APP VERSION:   " + BuildConfig.VERSION_CODE + " (" + BuildConfig.VERSION_NAME + ")");
            build.println();
            build.println("SDK:           " + Build.VERSION.SDK_INT);
            build.println("RELEASE:       " + Build.VERSION.RELEASE);
            build.println("CODENAME:      " + Build.VERSION.CODENAME);
            build.println("INCREMENTAL:   " + Build.VERSION.INCREMENTAL);
            build.println();
            build.println("HOST:          " + Build.HOST);
            build.println("USER:          " + Build.USER);
            build.println("ID:            " + Build.ID);
            build.println("TAGS:          " + Build.TAGS);
            build.println("TYPE:          " + Build.TYPE);
            build.println();
            build.println("MODEL:         " + Build.MODEL);
            build.println("DEVICE:        " + Build.DEVICE);
            build.println("PRODUCT:       " + Build.PRODUCT);
            build.println();
            build.println("BRAND:         " + Build.BRAND);
            build.println("MANUFACTURER:  " + Build.MANUFACTURER);
            build.println();
            build.println("HARDWARE:      " + Build.HARDWARE);
            build.println("BOARD:         " + Build.BOARD);
            build.println("CPU_ABI:       " + Build.CPU_ABI);
            build.println("CPU_ABI2:      " + Build.CPU_ABI2);
            build.println();
            build.println("SERIAL:        " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD ? Build.SERIAL : Build.UNKNOWN));
            build.println();
            build.println("BOOTLOADER:    " + Build.BOOTLOADER);
            build.println("RADIO:         " + Build.RADIO);
            build.println();
            build.println("DISPLAY:       " + Build.DISPLAY);
            build.println("FINGERPRINT:   " + Build.FINGERPRINT);
            build.close();
        }

        return sb.toString();
    }

}
