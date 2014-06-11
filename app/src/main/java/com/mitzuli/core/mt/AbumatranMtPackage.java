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

import android.os.AsyncTask;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;

public class AbumatranMtPackage extends MtPackage {

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
                final URL url = new URL("http://www.abumatran.eu:55555/translator/");
                final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                final DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
                final JSONObject params = new JSONObject();
                params.put("text", new JSONArray(Arrays.asList(text[0].split("\n"))));
                params.put("lang", getId());
                final JSONObject preprocessParams = new JSONObject();
                preprocessParams.put("splitter", new JSONArray(Arrays.asList(new Integer[]{1,1})));
                preprocessParams.put("tokenizer", new JSONArray(Arrays.asList(new Integer[]{1,2})));
                preprocessParams.put("truecaser", new JSONArray(Arrays.asList(new Integer[]{1,3})));
                params.put("preprocess", preprocessParams);
                final JSONObject postprocessParams = new JSONObject();
                postprocessParams.put("detokenizer", new JSONArray(Arrays.asList(new Integer[]{1,2})));
                postprocessParams.put("detruecaser", new JSONArray(Arrays.asList(new Integer[]{1,1})));
                params.put("postprocess", postprocessParams);
                wr.writeBytes("[" + params + "]");
                wr.flush();
                wr.close();
                final JSONObject response = new JSONObject(new Scanner(new BufferedReader(new InputStreamReader(connection.getInputStream()))).useDelimiter("\\A").next());
                if (response.getInt("success") != 1)  throw new Exception("Abu-MaTran API failed");
                final StringBuilder sb = new StringBuilder();
                final JSONArray translatedLines = response.getJSONArray("text");
                for (int i = 0; i < translatedLines.length(); i++) {
                    final JSONArray translatedLine = translatedLines.getJSONArray(i);
                    if (i != 0) sb.append("<br/>");
                    for (int j = 0; j < translatedLine.length(); j++) {
                        if (j != 0) sb.append(" ");
                        sb.append(TextUtils.htmlEncode(translatedLine.getString(j)));
                    }
                }
                return sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
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
                exceptionCallback.onException(new Exception("Abu-MaTran API failed", exception));
            }
        }

    }

    public AbumatranMtPackage(String id) {
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
