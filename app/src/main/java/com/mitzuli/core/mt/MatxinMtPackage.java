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

package com.mitzuli.core.mt;

import com.mitzuli.Keys;

import android.os.AsyncTask;

import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;


public class MatxinMtPackage extends MtPackage {

    private OnlineTranslationTask onlineTranslationTask;

    private class OnlineTranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public OnlineTranslationTask(boolean markUnknown, TranslationCallback translationCallback, ExceptionCallback exceptionCallback) {
            this.markUnknown = markUnknown;
            this.translationCallback = translationCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected String doInBackground(String... text) {
            try {
                final URL url = new URL(Keys.MATXIN_API_URL + "?lang=" + getId() + "&cod_client=" + Keys.MATXIN_API_KEY + "&text=" + URLEncoder.encode(text[0], "UTF-8"));
                return new Scanner(url.openStream(), "UTF-8").useDelimiter("\\A").next();
            } catch (Exception e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String translation) {
            onlineTranslationTask = null;
            if (translation != null) {
                translationCallback.onTranslationDone(translation);
            } else if (exception != null) {
                exceptionCallback.onException(new Exception("Matxin API failed", exception));
            }
        }

    }

    public MatxinMtPackage(String id) {
        super(id, null, -1, null, null, null, null, null, null);
    }

    @Override
    public void translate(final String text, final boolean markUnknown, final TranslationCallback translationCallback, final ExceptionCallback exceptionCallback) {
        onlineTranslationTask = new OnlineTranslationTask(markUnknown, translationCallback, exceptionCallback);
        onlineTranslationTask.execute(text);
    }

    @Override
    public boolean isInstalled() {
        return false;
    }

    @Override
    public boolean isInstallable() {
        return false;
    }

    @Override
    public boolean isUpdateable() {
        return false;
    }

}
