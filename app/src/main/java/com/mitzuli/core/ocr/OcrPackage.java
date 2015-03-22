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

import android.os.AsyncTask;

import com.mitzuli.Image;
import com.mitzuli.core.KeyValueSaver;
import com.mitzuli.Language;
import com.mitzuli.core.Package;
import com.mitzuli.core.PackageManager;

import java.io.File;
import java.util.Collections;


public class OcrPackage extends Package { // TODO Installing, updating or uninstalling this package while its text recognition task is running yields to undefined behavior

    private static final String OFFLINE_TESSERACT = "tesseract";

    private final Language language;

    public static interface OcrCallback {
        public void onTextRecognized(String text);
    }

    public static class Builder extends Package.Builder {

        private final Language language;

        public Builder(PackageManager manager, KeyValueSaver saver, File packageDir, File cacheDir, File safeCacheDir, File tmpDir, Language language) {
            super(manager, saver, packageDir, cacheDir, safeCacheDir, tmpDir);
            this.language = language;
        }

        @Override
        public OcrPackage build() {
            return new OcrPackage(this);
        }

    }

    private OcrPackage(Builder builder) {
        super(builder);
        this.language = builder.language;
    }

    @Override
    public boolean isSupported() {
        return isSupported(Collections.singleton(OFFLINE_TESSERACT));
    }

    public Language getLanguage() {
        return language;
    }

    public void recognizeText(Image image, OcrCallback ocrCallback, ExceptionCallback exceptionCallback) {
        markUsage();
        new OcrTask(ocrCallback, exceptionCallback).execute(image);
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
        protected String doInBackground(final Image... image) {
            final OfflineServiceProvider offline = getOfflineServiceProvider();
            if (offline == null) {
                if (isInstallable()) {
                    installToCache(null, new InstallCallback() {
                        @Override public void onInstall() {
                            recognizeText(image[0], ocrCallback, exceptionCallback);
                        }
                    }, exceptionCallback);
                    return null;
                } else {
                    exception = new Exception("Online translation failed and package not installable", exception);
                    return null;
                }
            } else {
                try {
                    if (offline.type.equals(OFFLINE_TESSERACT)) {
                        return new TesseractTextRecognizer(offline.code, offline.dir).recognizeText(image[0]);
                    } else {
                        throw new Exception("Unknown engine: " + offline.type);
                    }
                } catch (Exception e) {
                    exception = e;
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(String text) {
            if (text != null) ocrCallback.onTextRecognized(text);
            else if (exception != null) exceptionCallback.onException(exception);
        }

    }

}
