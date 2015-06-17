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

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class MatxinTranslator implements Translator {

    private final String code, url, key;

    public MatxinTranslator(String code, String url, String key) {
        this.code = code;
        this.url = url;
        this.key = key;
    }

    private String translateLine(String text) throws IOException {
        if (text.trim().length() == 0) return "";
        final URL query = new URL(url + "?lang=" + code + "&cod_client=" + key + "&text=" + URLEncoder.encode(text, "UTF-8"));
        return new Scanner(query.openStream(), "UTF-8").useDelimiter("\\A").next().trim();
    }

    @Override
    public String translate(String text) throws Exception {
        // The Matxin API does not keep line breaks in its translations, so we will translate line by line
        final String[] lines = text.split("\n");
        if (lines.length > 25) {  // If the input text has more than 25 lines translate it as a single line (we don't want to make so many calls to the server)
            return translateLine(text);
        }
        final ExecutorService executor = Executors.newCachedThreadPool();
        final List<Callable<String>> tasks = new ArrayList<Callable<String>>();
        for (final String line : lines) {
            tasks.add(new Callable<String>() {
                @Override public String call() throws Exception {
                    return translateLine(line);
                }
            });
        }
        final StringBuilder sb = new StringBuilder();
        for (Future<String> translation : executor.invokeAll(tasks)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(translation.get());
        }
        return sb.toString();
    }

}
