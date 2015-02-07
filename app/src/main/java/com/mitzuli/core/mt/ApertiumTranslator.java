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

import java.io.File;


public class ApertiumTranslator implements Translator {

    private final String code;
    private final File packageDir, cacheDir;
    private final ClassLoader classLoader;

    public ApertiumTranslator(String code, File packageDir, File cacheDir, ClassLoader classLoader) {
        this.code = code;
        this.packageDir = packageDir;
        this.cacheDir = cacheDir;
        this.classLoader = classLoader;
    }

    @Override
    public String translate(String text) throws Exception {
        synchronized (org.apertium.Translator.class) {
            org.apertium.Translator.setDisplayMarks(true);
            org.apertium.Translator.setBase(packageDir.getAbsolutePath(), classLoader);
            org.apertium.Translator.setMode(code);
            org.apertium.utils.IOUtils.cacheDir = cacheDir;
            return org.apertium.Translator.translate(text);
        }
    }

}
