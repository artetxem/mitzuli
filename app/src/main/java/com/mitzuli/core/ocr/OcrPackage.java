/*
 * Copyright (C) 2014 Mikel Artetxe <artetxem@gmail.com>
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

import java.io.File;
import java.net.URL;
import java.util.Locale;

import com.googlecode.leptonica.android.Pix;
import com.mitzuli.Image;
import com.mitzuli.core.Package;

import android.os.AsyncTask;

import com.googlecode.tesseract.android.TessBaseAPI;


public class OcrPackage extends Package {

    private final Locale language;
    private OcrTask ocrTask;

    public static interface OcrCallback {
        public void onTextRecognized(String text);
    }

    public OcrPackage(String id, URL repoUrl, long repoVersion, LongSaver installedVersionSaver, LongSaver lastUsageSaver, File packageDir, File cacheDir, File tmpDir, OcrPackageManager manager) {
        super(id, repoUrl, repoVersion, installedVersionSaver, lastUsageSaver, packageDir, cacheDir, tmpDir, manager);
        this.language = codeToLocale(id);
    }

    public Locale getLanguage() {
        return language;
    }

    public void recognizeText(final Image picture, final OcrCallback ocrCallback, final ExceptionCallback exceptionCallback) {
        markUsage();
        if (getPackageDir() != null) {
            ocrTask = new OcrTask(ocrCallback, exceptionCallback);
            ocrTask.execute(picture);
        } else if (isInstallable()) {
            installToCache(
                    null,
                    new InstallCallback() {
                        @Override
                        public void onInstall() {
                            ocrTask = new OcrTask(ocrCallback, exceptionCallback);
                            ocrTask.execute(picture);
                        }
                    },
                    exceptionCallback);
        } else {
            exceptionCallback.onException(new Exception("Package neither installed not installable."));
        }
    }


    private class OcrTask extends AsyncTask<Image, Void, String> {

        private final OcrCallback ocrCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public OcrTask(OcrCallback ocrCallback, ExceptionCallback exceptionCallback) {
            this.ocrCallback = ocrCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected String doInBackground(Image... picture) {
            try {
                final File packageDir = getPackageDir();
                if (packageDir == null) throw new Exception("Package not installed.");
                final TessBaseAPI tesseract = new TessBaseAPI();
                if (!tesseract.init(packageDir.getAbsolutePath(), getId())) throw new Exception("Tesseract init failed.");
                final Image preprocessedImage = OcrPreprocessor.preprocess(picture[0]);
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
                return sb.toString();
                //return tesseract.getUTF8Text();
            } catch (Exception e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String text) {
            ocrTask = null;
            if (text != null) ocrCallback.onTextRecognized(text);
            else if (exception != null) exceptionCallback.onException(exception);
        }

    }

    private static Locale codeToLocale(String code) {
        final String args[] = code.split("_", 2);
        if (args.length == 1) return new Locale(args[0]);
        else return new Locale(args[0], args[1]);
    }

}
