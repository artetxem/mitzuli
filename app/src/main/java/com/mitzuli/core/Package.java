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

import android.os.AsyncTask;
import android.util.Base64;

import com.mitzuli.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public abstract class Package {

    private static final String ALGORITHM_KEY = "RSA";
    private static final String ALGORITHM_SIGNATURE = "SHA256withRSA";
    private static final String ALGORITHM_MESSAGE_DIGEST = "SHA-256";
    private static final String KEY_LAST_USAGE = "last-usage";
    private static final String KEY_INSTALLED_TYPE = "installed-type";
    private static final String KEY_INSTALLED_CODE = "installed-code";
    private static final String KEY_INSTALLED_VERSION = "installed-version";
    private static final String PREFIX_DIGEST_SAVER = "digest";
    private static final String DIR_CACHE = "cache";
    private static final String DIR_CACHED_PACKAGE = "package";

    private final PackageManager manager;
    private final File packageDir, cacheDir, cachedPackageDir, safeCacheDir, tmpDir;
    private final KeyValueSaver saver;
    private final KeyValueSaver digestSaver;
    private final boolean beta;
    private final PublicKey publicKey;
    private final List<OnlineServiceProvider> onlineServiceProviders;
    private final RemotePackage remotePackage;
    private OfflineServiceProvider installedServiceProvider;
    private OfflineServiceProvider cachedServiceProvider;
    private InstallTask installTask;

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

    protected static class OnlineServiceProvider {
        public final String type;
        public final String code;
        public final String url;
        public OnlineServiceProvider(String type, String code, String url) {
            this.type = type;
            this.code = code;
            this.url = url;
        }
    }

    protected static class OfflineServiceProvider {
        public final String type;
        public final String code;
        public final File dir;
        public final long version;
        public OfflineServiceProvider(String type, String code, File dir, long version) {
            this.type = type;
            this.code = code;
            this.dir = dir;
            this.version = version;
        }
    }

    private static class RemotePackage {
        public final String type;
        public final String code;
        public final String url;
        public final byte[] signature;
        public final long version;
        public RemotePackage(String type, String code, String url, byte[] signature, long version) {
            this.type = type;
            this.code = code;
            this.url = url;
            this.signature = new byte[signature.length];
            System.arraycopy(signature, 0, this.signature, 0, signature.length);
            this.version = version;
        }
    }

    protected abstract static class Builder {

        private final PackageManager manager;
        private final KeyValueSaver saver;
        private final File packageDir, cacheDir, safeCacheDir, tmpDir;
        private final List<OnlineServiceProvider> online;
        private RemotePackage offline;
        private boolean beta;
        private PublicKey publicKey;

        public Builder(PackageManager manager, KeyValueSaver saver, File packageDir, File cacheDir, File safeCacheDir, File tmpDir) {
            this.manager = manager;
            this.saver = saver;
            this.packageDir = packageDir;
            this.cacheDir = cacheDir;
            this.safeCacheDir = safeCacheDir;
            this.tmpDir = tmpDir;
            this.online = new ArrayList<OnlineServiceProvider>();
            this.beta = false;
        }

        public void addOnline(String type, String code, String url) {
            online.add(new OnlineServiceProvider(type, code, url));
        }

        public void setOffline(String type, String code, String url, byte[] signature, long version) {
            offline = new RemotePackage(type, code, url, signature, version);
        }

        public void setPublicKey(byte[] publicKey) {
            try {
                this.publicKey = KeyFactory.getInstance(ALGORITHM_KEY).generatePublic(new X509EncodedKeySpec(publicKey));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void setBeta(boolean beta) {
            this.beta = beta;
        }

        public abstract Package build();

    }

    protected Package(Builder builder) {
        manager = builder.manager;
        packageDir = builder.packageDir;
        cacheDir = new File(builder.cacheDir, DIR_CACHE);
        cachedPackageDir = new File(builder.cacheDir, DIR_CACHED_PACKAGE);
        safeCacheDir = builder.safeCacheDir;
        tmpDir = builder.tmpDir;
        saver = builder.saver;
        digestSaver = new KeyValueSaver(builder.saver, PREFIX_DIGEST_SAVER);
        beta = builder.beta;
        publicKey = builder.publicKey;
        onlineServiceProviders = Collections.unmodifiableList(new ArrayList<OnlineServiceProvider>(builder.online));
        remotePackage = builder.offline;
        if (saver.containsString(KEY_INSTALLED_TYPE) && saver.containsString(KEY_INSTALLED_CODE) && saver.containsLong(KEY_INSTALLED_VERSION)) {
            final String type = saver.getString(KEY_INSTALLED_TYPE);
            final String code = saver.getString(KEY_INSTALLED_CODE);
            final long version = saver.getLong(KEY_INSTALLED_VERSION);
            if (packageDir.exists()) {
                installedServiceProvider = new OfflineServiceProvider(type, code, packageDir, version);
                if (cachedPackageDir.exists()) IOUtils.deleteAll(cachedPackageDir);
            } else if (cachedPackageDir.exists()) {
                cachedServiceProvider = new OfflineServiceProvider(type, code, cachedPackageDir, version);
            }
        }
        if (installedServiceProvider == null && cachedServiceProvider == null) {
            saver.removeString(KEY_INSTALLED_TYPE);
            saver.removeString(KEY_INSTALLED_CODE);
            saver.removeLong(KEY_INSTALLED_VERSION);
            digestSaver.removeAll();
            if (packageDir.exists()) IOUtils.deleteAll(packageDir);
            if (cachedPackageDir.exists()) IOUtils.deleteAll(cachedPackageDir);
        }
    }

    public abstract boolean isSupported();

    protected boolean isSupported(Collection<String> supportedTypes) {
        final Set<String> availableTypes = new HashSet<String>();
        for (OnlineServiceProvider online : onlineServiceProviders) availableTypes.add(online.type);
        if (getOfflineServiceProvider() != null) availableTypes.add(getOfflineServiceProvider().type);
        if (remotePackage != null) availableTypes.add(remotePackage.type);
        for (String supportedType : supportedTypes) if (availableTypes.contains(supportedType)) return true;
        return false;
    }

    public synchronized boolean isInstallable() {
        return remotePackage != null;
    }

    public synchronized boolean isInstalled() {
        return installedServiceProvider != null;
    }

    public synchronized boolean isCached() {
        return cachedServiceProvider != null;
    }

    public synchronized boolean isUpdateable() {
        return isInstalled() && isInstallable() && installedServiceProvider.version < remotePackage.version;
    }

    public boolean isBeta() {
        return beta;
    }

    public synchronized void install(final ProgressCallback progressCallback, final InstallCallback installCallback, final ExceptionCallback exceptionCallback) {
        if (installTask != null && !installTask.installToCache) { // If the package is already being installed use the same task
            if (progressCallback  != null) installTask.progressCallbacks.add(progressCallback);
            if (installCallback   != null) installTask.installCallbacks.add(installCallback);
            if (exceptionCallback != null) installTask.exceptionCallbacks.add(exceptionCallback);
        } else { // Otherwise run a new installation task (if the package was already installed, this will reinstall it)
            if (installTask != null) cleanUpCache(); // If the package is being installed to cache cancel the task and clean up the cache
            installTask = new InstallTask();
            if (progressCallback  != null) installTask.progressCallbacks.add(progressCallback);
            if (installCallback   != null) installTask.installCallbacks.add(installCallback);
            if (exceptionCallback != null) installTask.exceptionCallbacks.add(exceptionCallback);
            installTask.execute((Void)null);
        }
    }

    protected synchronized void installToCache(final ProgressCallback progressCallback, final InstallCallback installCallback, final ExceptionCallback exceptionCallback) {
        if (installTask != null) { // If the package is already being installed use the same task
            if (progressCallback  != null) installTask.progressCallbacks.add(progressCallback);
            if (installCallback   != null) installTask.installCallbacks.add(installCallback);
            if (exceptionCallback != null) installTask.exceptionCallbacks.add(exceptionCallback);
        } else { // Otherwise run a new installation task (if the package was already installed, this will reinstall it)
            installTask = new InstallTask(true);
            if (progressCallback  != null) installTask.progressCallbacks.add(progressCallback);
            if (installCallback   != null) installTask.installCallbacks.add(installCallback);
            if (exceptionCallback != null) installTask.exceptionCallbacks.add(exceptionCallback);
            installTask.execute((Void)null);
        }
    }

    public synchronized void update(final ProgressCallback progressCallback, final UpdateCallback updateCallback, final ExceptionCallback exceptionCallback) {
        // Updating a package is simply reinstalling it (but note that we do not check if it was already installed!)
        final InstallCallback installCallback = updateCallback == null ? null : new InstallCallback() {
            @Override public void onInstall() {
                updateCallback.onUpdate();
            }
        };
        install(progressCallback, installCallback, exceptionCallback);
    }

    public synchronized void uninstall() {
        try {
            if (installTask != null && !installTask.installToCache) { // If the package is being installed cancel the installation task
                installTask.cancel(true);
                while (installTask != null) wait();
            }
            if (installedServiceProvider != null) { // If the package is installed remove it
                installedServiceProvider = null;
                if (packageDir.exists()) IOUtils.deleteAll(packageDir);
                saver.removeString(KEY_INSTALLED_TYPE);
                saver.removeString(KEY_INSTALLED_CODE);
                saver.removeLong(KEY_INSTALLED_VERSION);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected File getCacheDir() {
        cacheDir.mkdirs();
        return cacheDir;
    }

    protected File getSafeCacheDir() {
        safeCacheDir.mkdirs();
        return safeCacheDir;
    }

    protected synchronized void cleanUpCache() {
        try {
            if (installTask != null && installTask.installToCache) { // If the package is being installed to cache cancel the installation task
                installTask.cancel(true);
                while (installTask != null) wait();
            }
            if (cacheDir.exists()) IOUtils.deleteAll(cacheDir);
            if (safeCacheDir.exists()) IOUtils.deleteAll(safeCacheDir);
            if (cachedServiceProvider != null) { // If the package is installed in the cache remove it
                cachedServiceProvider = null;
                if (cachedPackageDir.exists()) IOUtils.deleteAll(packageDir);
                saver.removeString(KEY_INSTALLED_TYPE);
                saver.removeString(KEY_INSTALLED_CODE);
                saver.removeLong(KEY_INSTALLED_VERSION);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected List<OnlineServiceProvider> getOnlineServiceProviders() {
        return onlineServiceProviders;
    }

    protected OfflineServiceProvider getOfflineServiceProvider() {
        return installedServiceProvider != null ? installedServiceProvider : cachedServiceProvider;
    }

    protected boolean verifyFileIntegrity(File f) throws IOException, NoSuchAlgorithmException {
        if (getOfflineServiceProvider() == null) return false;
        final MessageDigest digest = MessageDigest.getInstance(ALGORITHM_MESSAGE_DIGEST);
        final byte[] buffer = new byte[2048];
        final DigestInputStream is = new DigestInputStream(new BufferedInputStream(new FileInputStream(f)), digest);
        while (is.read(buffer) != -1) {}
        final String computedDigest = Base64.encodeToString(digest.digest(), Base64.DEFAULT).trim();
        final String savedDigest = digestSaver.getString(getOfflineServiceProvider().dir.toURI().relativize(f.toURI()).getPath());
        return computedDigest.equals(savedDigest);
    }

    protected long getLastUsage() {
        return saver.getLong(KEY_LAST_USAGE, 0L);
    }

    protected void markUsage() {
        saver.saveLong(KEY_LAST_USAGE, System.currentTimeMillis());
    }


    private class InstallTask extends AsyncTask<Void, Integer, Exception> {

        private final boolean installToCache;
        private final List<ProgressCallback> progressCallbacks;
        private final List<InstallCallback> installCallbacks;
        private final List<ExceptionCallback> exceptionCallbacks;

        public InstallTask() {
            this(false);
        }

        public InstallTask(boolean installToCache) {
            this.installToCache = installToCache;
            this.progressCallbacks = new ArrayList<ProgressCallback>();
            this.installCallbacks = new ArrayList<InstallCallback>();
            this.exceptionCallbacks = new ArrayList<ExceptionCallback>();
        }

        @Override
        protected Exception doInBackground(Void... args) {
            ZipInputStream zis = null;
            try {
                publishProgress(5);
                if (remotePackage == null)
                    throw new IllegalStateException("There is no package available");
                final URLConnection connection = new URL(remotePackage.url).openConnection();
                final int totalBytes = connection.getContentLength();
                final byte[] buffer = new byte[2048];
                final BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
                final SignatureVerifierInputStream verifier = publicKey == null ? null : new SignatureVerifierInputStream(bis, ALGORITHM_SIGNATURE, publicKey, remotePackage.signature);
                final ProcessedByteCountingInputStream is = new ProcessedByteCountingInputStream(verifier == null ? bis : verifier);
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

                    final MessageDigest digester = MessageDigest.getInstance(ALGORITHM_MESSAGE_DIGEST);
                    final OutputStream os = new DigestOutputStream(new BufferedOutputStream(new FileOutputStream(destFile), buffer.length), digester);
                    try {
                        int count;
                        while ((count = zis.read(buffer, 0, buffer.length)) != -1) {
                            os.write(buffer, 0, count);
                            if (isCancelled()) throw new InterruptedException();
                            if (10+80*(int)is.getProcessedByteCount()/totalBytes > progress) publishProgress(progress = 10+80*(int)is.getProcessedByteCount()/totalBytes);
                        }
                        digestSaver.saveString(entry.getName(), Base64.encodeToString(digester.digest(), Base64.DEFAULT).trim());
                    } finally {
                        os.close();
                    }
                }
                publishProgress(90);
                if (verifier != null) {
                    while (verifier.read(buffer) != -1) {} // Completely consume the input to properly verify the signature
                    if (!verifier.verifies()) throw new Exception("Package signature verification failed");
                }
                synchronized (Package.this) {
                    // Remove previous installations
                    installedServiceProvider = null;
                    cachedServiceProvider = null;
                    if (packageDir.exists()) IOUtils.deleteAll(packageDir);
                    if (cachedPackageDir.exists()) IOUtils.deleteAll(cachedPackageDir);
                    saver.removeString(KEY_INSTALLED_TYPE);
                    saver.removeString(KEY_INSTALLED_CODE);
                    saver.removeLong(KEY_INSTALLED_VERSION);
                    publishProgress(95);

                    // Install the downloaded package
                    final File installDir = installToCache ? cachedPackageDir : packageDir;
                    installDir.getParentFile().mkdirs();
                    if (!tmpDir.renameTo(installDir)) throw new Exception("Rename failed");
                    saver.saveString(KEY_INSTALLED_TYPE, remotePackage.type);
                    saver.saveString(KEY_INSTALLED_CODE, remotePackage.code);
                    saver.saveLong(KEY_INSTALLED_VERSION, remotePackage.version);
                    final OfflineServiceProvider provider = new OfflineServiceProvider(remotePackage.type, remotePackage.code, installDir, remotePackage.version);
                    if (installToCache) cachedServiceProvider = provider;
                    else installedServiceProvider = provider;

                    markUsage();
                    manager.cleanUpCache();
                }
                publishProgress(100);
                return null;
            } catch (Exception e) {
                return e;
            } finally {
                if (zis != null) try {zis.close();} catch (IOException e){}
                IOUtils.deleteAll(tmpDir);
                synchronized (Package.this) {
                    installTask = null;
                    Package.this.notifyAll();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            synchronized (Package.this) {
                for (ProgressCallback callback : progressCallbacks) callback.onProgress(progress[0]);
            }
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception == null) for (InstallCallback callback : installCallbacks) callback.onInstall();
            else for (ExceptionCallback callback : exceptionCallbacks) callback.onException(exception);
        }

    }

}
