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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OnlineMtPackageManager {

    private final File savedManifest;

    private List<MtPackage> packages;

    public OnlineMtPackageManager(File baseDir, BufferedReader defaultManifest) throws IOException {
        baseDir.mkdirs();
        this.savedManifest = new File(baseDir, "manifest");
        updateManifest(savedManifest.exists() ? new BufferedReader(new FileReader(savedManifest)) : defaultManifest, false);
        defaultManifest.close();
    }

    public List<MtPackage> getAllPackages() {
        return packages;
    }

    public void updateManifest(URL manifestUrl) throws IOException {
        updateManifest(new BufferedReader(new InputStreamReader(manifestUrl.openStream())));
    }

    public void updateManifest(BufferedReader manifest) throws IOException {
        updateManifest(manifest, true);
    }

    private void updateManifest(BufferedReader manifest, boolean save) throws IOException {
        final Writer writer = save ? new BufferedWriter(new FileWriter(savedManifest)) : null;
        packages = new ArrayList<MtPackage>();
        String line;
        while ((line = manifest.readLine()) != null) {
            if (line.trim().startsWith("#")) continue;
            if (save) writer.append(line).append("\n");
            final String[] columns = line.split("\t");
            if (columns.length == 2) {
                final String id = columns[0];
                final String api = columns[1];
                if (api.equals("abumatran")) {
                    packages.add(new AbumatranMtPackage(id));
                } else if (api.equals("matxin")) {
                    packages.add(new MatxinMtPackage(id));
                }
            }
        }
        manifest.close();
        if (save) writer.close();
    }

}
