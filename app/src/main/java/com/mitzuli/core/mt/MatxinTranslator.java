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

import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;


public class MatxinTranslator implements Translator {

    private final String code, url, key;

    public MatxinTranslator(String code, String url, String key) {
        this.code = code;
        this.url = url;
        this.key = key;
    }

    @Override
    public String translate(String text) throws Exception {
        final URL query = new URL(url + "?lang=" + code + "&cod_client=" + key + "&text=" + URLEncoder.encode(text, "UTF-8"));
        return new Scanner(query.openStream(), "UTF-8").useDelimiter("\\A").next();
    }

}
