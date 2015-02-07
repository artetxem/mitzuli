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

package com.mitzuli.core.mt;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;


public class AbumatranTranslator implements Translator {

    private final String code, url;

    public AbumatranTranslator(String code, String url) {
        this.code = code;
        this.url = url;
    }

    @Override
    public String translate(String text) throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        final OutputStream wr = new BufferedOutputStream(connection.getOutputStream());
        final JSONObject params = new JSONObject();
        params.put("text", new JSONArray(Arrays.asList(text.split("\n"))));
        params.put("lang", code);
        final JSONObject preprocessParams = new JSONObject();
        preprocessParams.put("splitter", new JSONArray(Arrays.asList(new Integer[]{1,1})));
        preprocessParams.put("tokenizer", new JSONArray(Arrays.asList(new Integer[]{1,2})));
        preprocessParams.put("truecaser", new JSONArray(Arrays.asList(new Integer[]{1,3})));
        params.put("preprocess", preprocessParams);
        final JSONObject postprocessParams = new JSONObject();
        postprocessParams.put("detokenizer", new JSONArray(Arrays.asList(new Integer[]{1,2})));
        postprocessParams.put("detruecaser", new JSONArray(Arrays.asList(new Integer[]{1,1})));
        params.put("postprocess", postprocessParams);
        wr.write(("[" + params + "]").getBytes("UTF-8"));
        wr.flush();
        wr.close();
        final JSONObject response = new JSONObject(new Scanner(new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))).useDelimiter("\\A").next());
        if (response.getInt("success") != 1)  throw new Exception("Abu-MaTran API failed");
        final StringBuilder sb = new StringBuilder();
        final JSONArray translatedLines = response.getJSONArray("text");
        for (int i = 0; i < translatedLines.length(); i++) {
            final JSONArray translatedLine = translatedLines.getJSONArray(i);
            if (i != 0) sb.append("\n");
            for (int j = 0; j < translatedLine.length(); j++) {
                if (j != 0) sb.append(" ");
                sb.append(translatedLine.getString(j));
            }
        }
        return sb.toString();
    }

}
