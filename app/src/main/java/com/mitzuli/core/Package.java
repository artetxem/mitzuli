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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.os.AsyncTask;


public abstract class Package {

    public static final long NOT_AVAILABLE = -1;

    private final String id;
    private final URL repoUrl;
    private final long repoVersion;
    private final LongSaver installedVersionSaver, lastUsageSaver;
    private final PackageManager manager;

    private File packageDir, cacheDir, cachedPackageDir, tmpDir;

    private InstallTask installTask;

    public static interface LongSaver {
        public void put(long value);
        public long get();
    }

    public static interface ProgressCallback {
        public void onProgress(int progress);
    }

    public static interface InstallCallback {
        public void onInstall();
    }

    public static interface UpdateCallback {
        public void onUpdate();
    }

    public static interface ExceptionCallback {
        public void onException(Exception exception);
    }

    public Package(String id, URL repoUrl, long repoVersion, LongSaver installedVersionSaver, LongSaver lastUsageSaver, File packageDir, File cacheDir, File tmpDir, PackageManager manager) {
        this.id = id;
        this.repoUrl = repoUrl;
        this.repoVersion = repoVersion;
        this.lastUsageSaver = lastUsageSaver;
        this.installedVersionSaver = installedVersionSaver;
        this.packageDir = packageDir;
        this.cacheDir = new File(cacheDir, "cache");
        this.cachedPackageDir = new File(cacheDir, "package");
        this.tmpDir = tmpDir;
        this.manager = manager;
    }

    public String getId() {
        return id;
    }

    protected File getPackageDir() {
        if (packageDir.exists()) return packageDir;
        else if (cachedPackageDir.exists()) return cachedPackageDir;
        else return null;
    }

    protected File getCacheDir() {
        cacheDir.mkdirs();
        return cacheDir;
    }

    public long getInstalledVersion() {
        return installedVersionSaver.get();
    }

    public long getRepoVersion() {
        return repoVersion;
    }

    private class InstallTask extends AsyncTask<Void, Integer, Exception> {

        private final File installDir;
        private final ProgressCallback progressCallback;
        private final InstallCallback installCallback;
        private final ExceptionCallback exceptionCallback;

        public InstallTask(File installDir, ProgressCallback progressCallback, InstallCallback installCallback, ExceptionCallback exceptionCallback) {
            this.installDir = installDir;
            this.progressCallback = progressCallback;
            this.installCallback = installCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected Exception doInBackground(Void... args) {
            ZipInputStream zis = null;
            try {
                final URLConnection connection = repoUrl.openConnection();
                final int totalBytes = connection.getContentLength();
                final ProcessedByteCountingInputStream is = new ProcessedByteCountingInputStream(new BufferedInputStream(connection.getInputStream()));
                zis = new ZipInputStream(is);
                int progress = 10;
                publishProgress(progress);
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (isCancelled()) throw new InterruptedException();
                    if (10+80*(int)is.getProcessedByteCount()/totalBytes > progress) publishProgress(progress = 10+80*(int)is.getProcessedByteCount()/totalBytes);
                    if (entry.isDirectory()) continue;

                    final File destFile = new File(tmpDir, entry.getName());
                    destFile.getParentFile().mkdirs();
                    final byte[] buffer = new byte[2048];
                    final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile), buffer.length);
                    try {
                        int count;
                        while ((count = zis.read(buffer, 0, buffer.length)) != -1) {
                            bos.write(buffer, 0, count);
                            if (isCancelled()) throw new InterruptedException();
                            if (10+80*(int)is.getProcessedByteCount()/totalBytes > progress) publishProgress(progress = 10+80*(int)is.getProcessedByteCount()/totalBytes);
                        }
                    } finally {
                        bos.close();
                    }
                }
                publishProgress(90);
                if (installDir.exists()) deleteAll(installDir);
                installDir.getParentFile().mkdirs();
                if (!tmpDir.renameTo(installDir)) throw new Exception("Rename failed");
                publishProgress(95);
                installedVersionSaver.put(repoVersion);
                publishProgress(100);
                return null;
            } catch (Exception e) {
                return e;
            } finally {
                if (zis != null) try {zis.close();} catch (IOException e){}
                deleteAll(tmpDir);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            if (progressCallback != null) progressCallback.onProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(Exception exception) {
            installTask = null;
            if (exception == null) installCallback.onInstall();
            else exceptionCallback.onException(exception);
        }
    }


    public void install(final ProgressCallback progressCallback, final InstallCallback installCallback, final ExceptionCallback exceptionCallback) {
        install(packageDir, progressCallback, installCallback, exceptionCallback);
    }

    protected void installToCache(final ProgressCallback progressCallback, final InstallCallback installCallback, final ExceptionCallback exceptionCallback) {
        install(cachedPackageDir, progressCallback, installCallback, exceptionCallback);
    }

    private void install(final File installDir, final ProgressCallback progressCallback, final InstallCallback installCallback, final ExceptionCallback exceptionCallback) {
        markUsage();
        installTask = new InstallTask(installDir, progressCallback, installCallback, exceptionCallback);
        installTask.execute((Void)null);
    }

    public void update(final ProgressCallback progressCallback, final UpdateCallback updateCallback, final ExceptionCallback exceptionCallback) {
        final File installDir = getPackageDir();
        if (packageDir == null) {
            exceptionCallback.onException(new Exception("The package is not installed"));
        } else {
            installTask = new InstallTask(
                    installDir,
                    progressCallback,
                    new InstallCallback(){
                        @Override public void onInstall() {
                            updateCallback.onUpdate();
                        }
                    },
                    exceptionCallback);
            installTask.execute((Void)null);
        }
    }

    public void uninstall() {
        if (installTask != null) {
            installTask.cancel(true); //TODO hau berrikusi
        }
        deleteAll(packageDir);
    }

    public boolean isInstallable() {
        return repoUrl != null;
    }

    public boolean isInstalled() {
        return packageDir.exists();
    }

    public boolean isUpdateable() {
        return
                isInstalled() &&
                        installedVersionSaver.get() != NOT_AVAILABLE &&
                        repoVersion != NOT_AVAILABLE &&
                        installedVersionSaver.get() < repoVersion;
    }

    protected long getLastUsage() {
        return lastUsageSaver.get();
    }

    protected void markUsage() {
        lastUsageSaver.put(System.currentTimeMillis());
        manager.cleanUpCache();
    }

    protected void cleanUpCache() {
        deleteAll(cacheDir);
        deleteAll(cachedPackageDir);
    }

    private static void deleteAll(File f) {
        if (f.isDirectory()) for (File child : f.listFiles()) deleteAll(child);
        f.delete();
    }

}
