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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

import com.mitzuli.core.*;
import com.mitzuli.core.Package;

import android.content.SharedPreferences;


public class OcrPackageManager extends PackageManager<OcrPackage> {

    public OcrPackageManager(File baseDir, File cacheDir, SharedPreferences prefs, BufferedReader defaultManifest) throws IOException {
        super(baseDir, cacheDir, prefs, defaultManifest);
    }

    public OcrPackage getPackageForLanguage(Locale language) {
        for (OcrPackage pack: getAllPackages())
            if (pack.getLanguage().getISO3Language().equals(language.getISO3Language()))
                return pack;
        return null;
    }

    @Override
    protected OcrPackage constructPackage(String id, URL repoUrl, long repoVersion, Package.LongSaver installedVersionSaver, Package.LongSaver lastUsageSaver, File packageDir, File cacheDir, File tmpDir) {
        return new OcrPackage(id, repoUrl, repoVersion, installedVersionSaver, lastUsageSaver, packageDir, cacheDir, tmpDir, this);
    }

}
