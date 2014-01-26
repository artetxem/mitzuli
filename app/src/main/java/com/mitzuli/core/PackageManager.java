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

package com.mitzuli.core;

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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.mitzuli.core.Package.ExceptionCallback;
import com.mitzuli.core.Package.InstallCallback;
import com.mitzuli.core.Package.ProgressCallback;
import com.mitzuli.core.Package.UpdateCallback;


import android.content.SharedPreferences;

public abstract class PackageManager<T extends Package> {

    private static final long DAY_MILLIS = 24*60*60*1000;// # of milliseconds in a day
    private static final String INSTALLED_VERSION_PREFIX = "INSTALLED_VERSION_";
    private static final String LAST_USAGE_PREFIX = "LAST_USAGE_";

    private final File packagesDir, cacheDir, tmpDir, savedManifest;

    private List<T> packages;
    private SharedPreferences prefs;

    public PackageManager(File baseDir, File cacheDir, SharedPreferences prefs, BufferedReader defaultManifest) throws IOException {
        baseDir.mkdirs();
        this.packagesDir = new File(baseDir, "packages");
        this.tmpDir = new File(baseDir, "tmp");
        this.cacheDir = cacheDir;
        this.savedManifest = new File(baseDir, "manifest");
        if (this.tmpDir.exists()) deleteAll(this.tmpDir);
        this.prefs = prefs;
        updateManifest(savedManifest.exists() ? new BufferedReader(new FileReader(savedManifest)) : defaultManifest, false);
        defaultManifest.close();
        cleanUpCache();
    }

    private static void deleteAll(File f) {
        if (f.isDirectory()) for (File child : f.listFiles()) deleteAll(child);
        f.delete();
    }

    public List<T> getAllPackages() {
        return packages;
    }

    public List<T> getInstalledPackages() {
        final ArrayList<T> installedPackages = new ArrayList<T>();
        for (T pack : packages)
            if (pack.isInstalled())
                installedPackages.add(pack);
        return installedPackages;
    }

    public List<T> getUpdateablePackages() {
        final ArrayList<T> updateablePackages = new ArrayList<T>();
        for (T pack : packages)
            if (pack.isUpdateable())
                updateablePackages.add(pack);
        return updateablePackages;
    }

    public void cleanUpCache() {
        final SortedSet<T> sortedPackages = new TreeSet<T>(new Comparator<T>() {
            @Override public int compare(T lhs, T rhs) {
                final long aux = rhs.getLastUsage() - lhs.getLastUsage();
                return aux < 0 ? -1 : aux == 0 ? 0 : 1;
            }
        });
        sortedPackages.addAll(packages);
        int installedCount = 0;
        int cachedCount = 0;
        for (T pack : sortedPackages) {
            if (pack.isInstalled()) { // The package is installed for offline usage
                installedCount++;
                // Clean up the cache if the package is not in the top 5 in recent usage and it hasn't been used for 2 weeks
                if (installedCount > 5 && System.currentTimeMillis() - pack.getLastUsage() < 14*DAY_MILLIS) {
                    pack.cleanUpCache();
                }
            } else if (pack.getPackageDir() != null) { // The package is installed in the cache
                cachedCount++;
                // Clean up the cache if the package is not in the top 5 in recent usage
                if (cachedCount > 5) {
                    pack.cleanUpCache();
                }
            } else { // The package is not installed
                pack.cleanUpCache();
            }
        }
    }

    public void updateManifest(URL manifestUrl) throws IOException {
        updateManifest(new BufferedReader(new InputStreamReader(manifestUrl.openStream())));
    }

    public void updateManifest(BufferedReader manifest) throws IOException {
        updateManifest(manifest, true);
    }

    private void updateManifest(BufferedReader manifest, boolean save) throws IOException {
        final Writer writer = save ? new BufferedWriter(new FileWriter(savedManifest)) : null;
        packages = new ArrayList<T>();
        String line;
        while ((line = manifest.readLine()) != null) {
            if (line.trim().startsWith("#")) continue;
            if (save) writer.append(line).append("\n");
            final String[] columns = line.split("\t");
            if (columns.length == 3) {
                final String id = columns[0];
                final URL repoUrl = new URL(columns[2]);
                final long repoVersion = Long.parseLong(columns[1]);
                final Package.LongSaver installedVersionSaver = new Package.LongSaver() {
                    @Override public void put(long version) {prefs.edit().putLong(INSTALLED_VERSION_PREFIX + id, version).commit();}
                    @Override public long get() {return prefs.getLong(INSTALLED_VERSION_PREFIX + id, Package.NOT_AVAILABLE);}
                };
                final Package.LongSaver lastUsageSaver = new Package.LongSaver() {
                    @Override public void put(long version) {prefs.edit().putLong(LAST_USAGE_PREFIX + id, version).commit();}
                    @Override public long get() {return prefs.getLong(LAST_USAGE_PREFIX + id, Package.NOT_AVAILABLE);}
                };
                packages.add(constructPackage(id, repoUrl, repoVersion, installedVersionSaver, lastUsageSaver, new File(packagesDir, id), new File(cacheDir, id), new File(tmpDir, id)));
            }
        }

        final Set<String> savedValues = new TreeSet<String>(prefs.getAll().keySet());
        for (int i = 0; i < packages.size(); i++) savedValues.remove(INSTALLED_VERSION_PREFIX + packages.get(i).getId());
        for (final String value : savedValues) {
            if (value.startsWith(INSTALLED_VERSION_PREFIX)) {
                final String id = value.substring(INSTALLED_VERSION_PREFIX.length());
                if (new File(packagesDir, id).exists()) {
                    final Package.LongSaver installedVersionSaver = new Package.LongSaver() {
                        @Override public void put(long version) {prefs.edit().putLong(INSTALLED_VERSION_PREFIX + id, version).commit();}
                        @Override public long get() {return prefs.getLong(INSTALLED_VERSION_PREFIX + id, Package.NOT_AVAILABLE);}
                    };
                    final Package.LongSaver lastUsageSaver = new Package.LongSaver() {
                        @Override public void put(long version) {prefs.edit().putLong(LAST_USAGE_PREFIX + id, version).commit();}
                        @Override public long get() {return prefs.getLong(LAST_USAGE_PREFIX + id, Package.NOT_AVAILABLE);}
                    };
                    packages.add(constructPackage(id, null, Package.NOT_AVAILABLE, installedVersionSaver, lastUsageSaver, new File(packagesDir, id), new File(cacheDir, id), new File(tmpDir, id)));
                } else { // Package not installed
                    deleteAll(new File(cacheDir, id));
                    deleteAll(new File(tmpDir, id));
                }
            }
        }
        manifest.close();
        if (save) writer.close();
    }

    protected abstract T constructPackage(String id, URL repoUrl, long repoVersion, Package.LongSaver installedVersionSaver, Package.LongSaver lastUsageSaver, File packageDir, File cacheDir, File tmpDir);


    public static void installPackages(List<Package> packages, ProgressCallback progressCallback, InstallCallback installCallback, ExceptionCallback exceptionCallback) {
        installPackages(packages, 0, progressCallback, installCallback, exceptionCallback);
    }

    private static void installPackages(final List<Package> packages, final int index, final ProgressCallback progressCallback, final InstallCallback installationCallback, final ExceptionCallback exceptionCallback) {
        if (index == packages.size()) {
            installationCallback.onInstall();
        } else {
            packages.get(index).install(
                    new ProgressCallback() {
                        @Override public void onProgress(int progress) {
                            progressCallback.onProgress((index*100+progress)/packages.size());
                        }
                    },
                    new InstallCallback() {
                        @Override public void onInstall() {
                            installPackages(packages, index+1, progressCallback, installationCallback, exceptionCallback);
                        }
                    },
                    exceptionCallback);
        }
    }

    public static void updatePackages(List<Package> packages, ProgressCallback progressCallback, UpdateCallback updateCallback, ExceptionCallback exceptionCallback) {
        updatePackages(packages, 0, progressCallback, updateCallback, exceptionCallback);
    }

    private static void updatePackages(final List<Package> packages, final int index, final ProgressCallback progressCallback, final UpdateCallback updateCallback, final ExceptionCallback exceptionCallback) {
        if (index == packages.size()) {
            updateCallback.onUpdate();
        } else {
            packages.get(index).update(
                    new ProgressCallback() {
                        @Override public void onProgress(int progress) {
                            progressCallback.onProgress((index*100+progress)/packages.size());
                        }
                    },
                    new UpdateCallback() {
                        @Override public void onUpdate() {
                            updatePackages(packages, index+1, progressCallback, updateCallback, exceptionCallback);
                        }
                    },
                    exceptionCallback);
        }
    }

    public static void uninstallPackages(List<Package> packages) {
        for (Package pack : packages) pack.uninstall();
    }


}
