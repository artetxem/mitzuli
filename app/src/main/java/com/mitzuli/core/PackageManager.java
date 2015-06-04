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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.util.Base64;
import android.util.Xml;

import com.mitzuli.BuildConfig;
import com.mitzuli.Language;
import com.mitzuli.R;
import com.mitzuli.core.mt.MtPackage;
import com.mitzuli.core.ocr.OcrPackage;
import com.mitzuli.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.acra.ACRA;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;


public class PackageManager { // TODO Using packages contained by this manager while -or after- updating its manifest yields to undefined behavior

    private static final long DAY_MILLIS = 24*60*60*1000;// # of milliseconds in a day
    private static final String KEY_MANIFEST_VERSION = "manifest-version";

    private static final String NAMESPACE = "";
    private static final String TAG_ROOT = "manifest";
    private static final String TAG_MT = "mt";
    private static final String TAG_OCR = "ocr";
    private static final String TAG_ONLINE = "online";
    private static final String TAG_OFFLINE = "offline";
    private static final String ATTR_SRC = "src";
    private static final String ATTR_TRG = "trg";
    private static final String ATTR_BETA = "beta";
    private static final String ATTR_LANGUAGE = "lang";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_CODE = "code";
    private static final String ATTR_URL = "url";
    private static final String ATTR_SIGNATURE = "signature";
    private static final String ATTR_VERSION = "version";

    private final File packagesDir, cacheDir, safeCacheDir, tmpDir, savedManifest;
    private final KeyValueSaver saver;
    private final boolean useBetaPackages;
    private final byte[] publicKey;

    private final List<MtPackage> mtPackages = new ArrayList<MtPackage>();
    private final List<OcrPackage> ocrPackages = new ArrayList<OcrPackage>();

    private static class Manifest {
        public final List<MtPackage> mtPackages, unknownMtPackages;
        public final List<OcrPackage> ocrPackages, unknownOcrPackages;
        public final long version;
        public final byte[] data;
        public Manifest(List<MtPackage> mtPackages, List<MtPackage> unknownMtPackages, List<OcrPackage> ocrPackages, List<OcrPackage> unknownOcrPackages, long version, byte[] data) {
            this.mtPackages = mtPackages;
            this.unknownMtPackages = unknownMtPackages;
            this.ocrPackages = ocrPackages;
            this.unknownOcrPackages = unknownOcrPackages;
            this.version = version;
            this.data = data;
        }
    }

    // This is just a convenient method to create a package manager from a context
    // I know that this might not be the most appropriate place to parse preferences and all that,
    // but having this code in a centralized place makes everything easier and less prone to bugs
    public static PackageManager fromContext(Context context) throws IOException, XmlPullParserException {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean betaPackages          = preferences.getBoolean("pref_key_beta_packages", false);
        final boolean useExternalStorage    = preferences.getBoolean("pref_key_external_storage", true);
        final boolean signatureVerification = preferences.getBoolean("pref_key_signature_verification", true);
        final File externalBaseDir  = context.getExternalFilesDir(null) == null ? null : new File(context.getExternalFilesDir(null), "packages");
        final File externalCacheDir = context.getExternalCacheDir() == null ? null : new File(context.getExternalCacheDir(), "packages");
        final File internalBaseDir  = new File(context.getFilesDir(), "packages");
        final File internalCacheDir = new File(new File(context.getCacheDir(), "packages"), "cache");
        final File safeCacheDir     = new File(new File(context.getCacheDir(), "packages"), "safe");
        final File baseDir  = useExternalStorage && externalBaseDir  != null ? externalBaseDir  : internalBaseDir;
        final File cacheDir = useExternalStorage && externalCacheDir != null ? externalCacheDir : internalCacheDir;
        if (externalBaseDir  != null) IOUtils.deleteAll(useExternalStorage ? internalBaseDir  : externalBaseDir);
        if (externalCacheDir != null) IOUtils.deleteAll(useExternalStorage ? internalCacheDir : externalCacheDir);
        final byte[] key = signatureVerification ? IOUtils.toByteArray(context.getResources().openRawResource(R.raw.public_key)) : null;
        return new PackageManager(baseDir, cacheDir, safeCacheDir,
                context.getSharedPreferences("packages", Context.MODE_PRIVATE),
                new BufferedInputStream(context.getResources().openRawResource(R.raw.manifest)),
                betaPackages, key);
    }

    public PackageManager(File baseDir, File cacheDir, File safeCacheDir, SharedPreferences prefs, InputStream defaultManifest, boolean useBetaPackages, byte[] publicKey) throws IOException, XmlPullParserException {
        baseDir.mkdirs();
        this.packagesDir = new File(baseDir, "packages");
        this.tmpDir = new File(baseDir, "tmp");
        this.cacheDir = cacheDir;
        this.safeCacheDir = safeCacheDir;
        this.savedManifest = new File(baseDir, "manifest");
        if (this.tmpDir.exists()) IOUtils.deleteAll(this.tmpDir);
        this.saver = new KeyValueSaver(prefs);
        this.useBetaPackages = useBetaPackages;
        this.publicKey = publicKey;

        Manifest manifest = null;
        if (savedManifest.exists()) {
            try {
                manifest = readManifest(new BufferedInputStream(new FileInputStream(savedManifest)));
            } catch (Exception e) {}
        }
        if (manifest == null) manifest = readManifest(new BufferedInputStream(defaultManifest));
        saver.saveLong(KEY_MANIFEST_VERSION, manifest.version);
        refreshPackages(manifest);
        defaultManifest.close();
        cleanUpCache();
    }

    public void updateManifest(String url) throws IOException, XmlPullParserException {
        final long localVersion = saver.getLong(KEY_MANIFEST_VERSION, 0L);
        final Manifest manifest = readManifest(new URL(url +
                "?app-id=" + BuildConfig.APPLICATION_ID +
                "&app-version=" + BuildConfig.VERSION_CODE +
                "&manifest-version=" + localVersion).openStream());
        if (localVersion < manifest.version && !manifest.mtPackages.isEmpty()) {
            refreshPackages(manifest);
            IOUtils.write(manifest.data, savedManifest);
            saver.saveLong(KEY_MANIFEST_VERSION, manifest.version);
        }
    }

    private void refreshPackages(Manifest manifest) {
        mtPackages.clear();
        ocrPackages.clear();
        for (MtPackage mtPackage : manifest.mtPackages) {
            if (mtPackage.isSupported()) {
                mtPackages.add(mtPackage);
            } else {
                mtPackage.uninstall();
                mtPackage.cleanUpCache();
            }
        }
        for (OcrPackage ocrPackage : manifest.ocrPackages) {
            if (ocrPackage.isSupported()) {
                ocrPackages.add(ocrPackage);
            } else {
                ocrPackage.uninstall();
                ocrPackage.cleanUpCache();
            }
        }
        for (MtPackage mtPackage : manifest.unknownMtPackages) {
            if (mtPackage.isInstalled() && mtPackage.isSupported()) {
                mtPackages.add(mtPackage);
            } else {
                mtPackage.uninstall();
                mtPackage.cleanUpCache();
            }
        }
        for (OcrPackage ocrPackage : manifest.unknownOcrPackages) {
            if (ocrPackage.isInstalled() && ocrPackage.isSupported()) {
                ocrPackages.add(ocrPackage);
            } else {
                ocrPackage.uninstall();
                ocrPackage.cleanUpCache();
            }
        }
    }

    private Manifest readManifest(InputStream in) throws XmlPullParserException, IOException {
        final List<MtPackage> mtPackages = new ArrayList<MtPackage>();
        final List<MtPackage> unknownMtPackages = new ArrayList<MtPackage>();
        final List<OcrPackage> ocrPackages = new ArrayList<OcrPackage>();
        final List<OcrPackage> unknownOcrPackages = new ArrayList<OcrPackage>();
        long manifestVersion = 0;
        final byte[] data = IOUtils.toByteArray(in);

        final File mtPackageDir = new File(packagesDir, "mt");
        final File mtCacheDir = new File(cacheDir, "mt");
        final File mtSafeCacheDir = new File(safeCacheDir, "mt");
        final File mtTmpDir = new File(tmpDir, "mt");
        final KeyValueSaver mtSaver = new KeyValueSaver(saver, "mt");
        final Set<String> processedMtPackages = new HashSet<String>();

        final File ocrPackageDir = new File(packagesDir, "ocr");
        final File ocrCacheDir = new File(cacheDir, "ocr");
        final File ocrSafeCacheDir = new File(safeCacheDir, "ocr");
        final File ocrTmpDir = new File(tmpDir, "ocr");
        final KeyValueSaver ocrSaver = new KeyValueSaver(saver, "ocr");
        final Set<String> processedOcrPackages = new HashSet<String>();

        // Parse the XML and add the packages it contains
        final XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new ByteArrayInputStream(data), null);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                final String tag = parser.getName();
                if (tag.equals(TAG_ROOT)) {
                    manifestVersion = Long.parseLong(parser.getAttributeValue(NAMESPACE, ATTR_VERSION));
                } else if (tag.equals(TAG_MT)) {
                    final Language src = Language.forTag(parser.getAttributeValue(NAMESPACE, ATTR_SRC));
                    final Language trg = Language.forTag(parser.getAttributeValue(NAMESPACE, ATTR_TRG));
                    final String betaAttr = parser.getAttributeValue(NAMESPACE, ATTR_BETA);
                    final boolean beta = betaAttr != null && betaAttr.equals("true");
                    final String id = src.toTag() + "." + trg.toTag();
                    final MtPackage.Builder builder = new MtPackage.Builder(this, new KeyValueSaver(mtSaver, id),
                            new File(mtPackageDir, id), new File(mtCacheDir, id), new File(mtSafeCacheDir, id), new File(mtTmpDir, id),
                            src, trg);
                    builder.setBeta(beta);
                    if (publicKey != null) builder.setPublicKey(publicKey);
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.getEventType() == XmlPullParser.START_TAG) {
                            if (parser.getName().equals(TAG_ONLINE)) {
                                final String type = parser.getAttributeValue(NAMESPACE, ATTR_TYPE);
                                final String code = parser.getAttributeValue(NAMESPACE, ATTR_CODE);
                                final String url = parser.getAttributeValue(NAMESPACE, ATTR_URL);
                                builder.addOnline(type, code, url);
                            } else if (parser.getName().equals(TAG_OFFLINE)) {
                                final String type = parser.getAttributeValue(NAMESPACE, ATTR_TYPE);
                                final String code = parser.getAttributeValue(NAMESPACE, ATTR_CODE);
                                final String url = parser.getAttributeValue(NAMESPACE, ATTR_URL);
                                final byte[] signature = Base64.decode(parser.getAttributeValue(NAMESPACE, ATTR_SIGNATURE), Base64.DEFAULT);
                                final long version = Long.parseLong(parser.getAttributeValue(NAMESPACE, ATTR_VERSION));
                                builder.setOffline(type, code, url, signature, version);
                            }
                            skip(parser);
                        }
                    }
                    final MtPackage mtPackage = builder.build();
                    if (!beta || useBetaPackages || mtPackage.isInstalled()) {
                        mtPackages.add(mtPackage);
                        processedMtPackages.add(id);
                    }
                } else if (tag.equals(TAG_OCR)) {
                    final Language language = Language.forTag(parser.getAttributeValue(NAMESPACE, ATTR_LANGUAGE));
                    final String id = language.toTag();
                    final String betaAttr = parser.getAttributeValue(NAMESPACE, ATTR_BETA);
                    final boolean beta = betaAttr != null && betaAttr.equals("true");
                    final OcrPackage.Builder builder = new OcrPackage.Builder(this, new KeyValueSaver(ocrSaver, id),
                            new File(ocrPackageDir, id), new File(ocrCacheDir, id), new File(ocrSafeCacheDir, id), new File(ocrTmpDir, id),
                            language);
                    builder.setBeta(beta);
                    if (publicKey != null) builder.setPublicKey(publicKey);
                    while (parser.next() != XmlPullParser.END_TAG) {
                        if (parser.getEventType() == XmlPullParser.START_TAG) {
                            if (parser.getName().equals(TAG_ONLINE)) {
                                final String type = parser.getAttributeValue(NAMESPACE, ATTR_TYPE);
                                final String code = parser.getAttributeValue(NAMESPACE, ATTR_CODE);
                                final String url = parser.getAttributeValue(NAMESPACE, ATTR_URL);
                                builder.addOnline(type, code, url);
                            } else if (parser.getName().equals(TAG_OFFLINE)) {
                                final String type = parser.getAttributeValue(NAMESPACE, ATTR_TYPE);
                                final String code = parser.getAttributeValue(NAMESPACE, ATTR_CODE);
                                final String url = parser.getAttributeValue(NAMESPACE, ATTR_URL);
                                final byte[] signature = Base64.decode(parser.getAttributeValue(NAMESPACE, ATTR_SIGNATURE), Base64.DEFAULT);
                                final long version = Long.parseLong(parser.getAttributeValue(NAMESPACE, ATTR_VERSION));
                                builder.setOffline(type, code, url, signature, version);
                            }
                            skip(parser);
                        }
                    }
                    final OcrPackage ocrPackage = builder.build();
                    if (!beta || useBetaPackages || ocrPackage.isInstalled()) {
                        ocrPackages.add(ocrPackage);
                        processedOcrPackages.add(id);
                    }
                } else {
                    skip(parser);
                }
            }
        }

        // Add local packages that were missing in the manifest
        if (mtPackageDir.exists()) {
            for (File f : mtPackageDir.listFiles()) {
                if (!f.isDirectory()) {
                    f.delete();
                } else if (!processedMtPackages.contains(f.getName())) {
                    final String id = f.getName();
                    final String tags[] = id.split("\\.");
                    if (tags.length == 2) {
                        final Language src = Language.forTag(tags[0]);
                        final Language trg = Language.forTag(tags[1]);
                        final MtPackage mtPackage = new MtPackage.Builder(this, new KeyValueSaver(mtSaver, id),
                                new File(mtPackageDir, id), new File(mtCacheDir, id), new File(mtSafeCacheDir, id), new File(mtTmpDir, id),
                                src, trg).build();
                        unknownMtPackages.add(mtPackage);
                    } else {
                        IOUtils.deleteAll(f);
                    }
                }
            }
        }
        if (ocrPackageDir.exists()) {
            for (File f : ocrPackageDir.listFiles()) {
                if (!f.isDirectory()) {
                    f.delete();
                } else if (!processedOcrPackages.contains(f.getName())) {
                    final String id = f.getName();
                    final Language language = Language.forTag(id);
                    final OcrPackage ocrPackage = new OcrPackage.Builder(this, new KeyValueSaver(mtSaver, id),
                            new File(mtPackageDir, id), new File(mtCacheDir, id), new File(mtSafeCacheDir, id), new File(mtTmpDir, id),
                            language).build();
                    unknownOcrPackages.add(ocrPackage);
                }
            }
        }
        return new Manifest(mtPackages, unknownMtPackages, ocrPackages, unknownOcrPackages, manifestVersion, data);
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) throw new IllegalStateException();
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }


    public List<Package> getPackages() {
        final ArrayList<Package> packages = new ArrayList<Package>(mtPackages.size() + ocrPackages.size());
        packages.addAll(mtPackages);
        packages.addAll(ocrPackages);
        return Collections.unmodifiableList(packages);
    }

    public List<Package> getUpdateablePackages() {
        final ArrayList<Package> packages = new ArrayList<Package>();
        for (Package p : getPackages()) if (p.isUpdateable()) packages.add(p);
        return Collections.unmodifiableList(packages);
    }

    public List<MtPackage> getMtPackages() {
        return Collections.unmodifiableList(new ArrayList<MtPackage>(mtPackages));
    }

    public List<OcrPackage> getOcrPackages() {
        return Collections.unmodifiableList(new ArrayList<OcrPackage>(ocrPackages));
    }

    public OcrPackage ocrPackageForLanguage(Language language) {
        for (OcrPackage ocrPackage : ocrPackages) if (ocrPackage.getLanguage().getLanguage().equals(language.getLanguage())) return ocrPackage;
        return null;
    }

    public void cleanUpCache() {
        final SortedSet<Package> sortedPackages = new TreeSet<Package>(new Comparator<Package>() {
            @Override public int compare(Package lhs, Package rhs) {
                final long aux = rhs.getLastUsage() - lhs.getLastUsage();
                return aux < 0 ? -1 : aux == 0 ? 0 : 1;
            }
        });
        sortedPackages.addAll(mtPackages);
        sortedPackages.addAll(ocrPackages);
        int installedCount = 0;
        int cachedCount = 0;
        for (Package pack : sortedPackages) {
            if (pack.isInstalled()) { // The package is installed for offline use
                installedCount++;
                // Clean up the cache if the package is not in the top 10 in recent usage and it hasn't been used for 2 weeks
                if (installedCount > 10 && System.currentTimeMillis() - pack.getLastUsage() < 14*DAY_MILLIS) {
                    pack.cleanUpCache();
                }
            } else if (pack.isCached()) { // The package is installed in the cache
                cachedCount++;
                // Clean up the cache if the package is not in the top 10 in recent usage
                if (cachedCount > 10) {
                    pack.cleanUpCache();
                }
            } else { // The package is not installed
                pack.cleanUpCache();
            }
        }
    }



    // TODO The following are again convenient methods for which this might not be the most appropriate place, but they make everything simpler for now

    public static interface ManifestsUpdateCallback {
        public void onManifestsUpdate();
    }

    public static void installPackages(List<Package> packages, Package.ProgressCallback progressCallback, Package.InstallCallback installCallback, Package.ExceptionCallback exceptionCallback) {
        installPackages(packages, 0, progressCallback, installCallback, exceptionCallback);
    }

    private static void installPackages(final List<Package> packages, final int index, final Package.ProgressCallback progressCallback, final Package.InstallCallback installationCallback, final Package.ExceptionCallback exceptionCallback) {
        if (index == packages.size()) {
            installationCallback.onInstall();
        } else {
            packages.get(index).install(
                    new Package.ProgressCallback() {
                        @Override public void onProgress(int progress) {
                            progressCallback.onProgress((index*100+progress)/packages.size());
                        }
                    },
                    new Package.InstallCallback() {
                        @Override public void onInstall() {
                            installPackages(packages, index+1, progressCallback, installationCallback, exceptionCallback);
                        }
                    },
                    exceptionCallback);
        }
    }

    public static void updatePackages(List<Package> packages, Package.ProgressCallback progressCallback, Package.UpdateCallback updateCallback, Package.ExceptionCallback exceptionCallback) {
        updatePackages(packages, 0, progressCallback, updateCallback, exceptionCallback);
    }

    private static void updatePackages(final List<Package> packages, final int index, final Package.ProgressCallback progressCallback, final Package.UpdateCallback updateCallback, final Package.ExceptionCallback exceptionCallback) {
        if (index == packages.size()) {
            updateCallback.onUpdate();
        } else {
            packages.get(index).update(
                    new Package.ProgressCallback() {
                        @Override public void onProgress(int progress) {
                            progressCallback.onProgress((index*100+progress)/packages.size());
                        }
                    },
                    new Package.UpdateCallback() {
                        @Override public void onUpdate() {
                            updatePackages(packages, index+1, progressCallback, updateCallback, exceptionCallback);
                        }
                    },
                    exceptionCallback);
        }
    }


    public void updateManifest(String url, ManifestsUpdateCallback updateCallback) {
        new UpdateTask(url, updateCallback).execute();
    }

    public void showUpdateDialog(Context context, String url, boolean warnOnNoUpdate, boolean warnOnError) {
        new UpdateTask(context, url, warnOnNoUpdate, warnOnError).execute();
    }

    public void showUpdateDialog(Context context, String url, boolean warnOnNoUpdate, boolean warnOnError, ManifestsUpdateCallback manifestsUpdateCallback) {
        new UpdateTask(context, url, warnOnNoUpdate, warnOnError, manifestsUpdateCallback).execute();
    }


    private class UpdateTask extends AsyncTask<Void, Void, Exception> {

        private final Context context;
        private final String url;
        private final boolean warnOnNoUpdate;
        private final boolean warnOnError;
        private final ManifestsUpdateCallback manifestsUpdateCallback;

        public UpdateTask(String url, ManifestsUpdateCallback manifestsUpdateCallback) {
            this(null, url, false, false, manifestsUpdateCallback);
        }

        public UpdateTask(Context context, String url, boolean warnOnNoUpdate, boolean warnOnError) {
            this(context, url, warnOnNoUpdate, warnOnError, null);
        }

        public UpdateTask(Context context, String url, boolean warnOnNoUpdate, boolean warnOnError, ManifestsUpdateCallback manifestsUpdateCallback) {
            this.context = context;
            this.url = url;
            this.warnOnNoUpdate = warnOnNoUpdate;
            this.warnOnError = warnOnError;
            this.manifestsUpdateCallback = manifestsUpdateCallback;
        }

        @Override
        protected Exception doInBackground(Void... args) {
            try {
                updateManifest(url);
                return null;
            } catch (Exception e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception == null) {
                if (manifestsUpdateCallback != null) manifestsUpdateCallback.onManifestsUpdate();
                final List<Package> updateablePackages = getUpdateablePackages();
                if (!updateablePackages.isEmpty()) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(context.getResources().getString(R.string.update_dialog_packages_to_update));
                    sb.append("<br/><br/>");
                    for (Package pack : updateablePackages) {
                        sb.append("&#8226; ");
                        if (pack instanceof MtPackage) {
                            sb.append(((MtPackage)pack).getSourceLanguage().getDisplayName(context));
                            sb.append(" → ");
                            sb.append(((MtPackage)pack).getTargetLanguage().getDisplayName(context));
                        } else if (pack instanceof OcrPackage) {
                            sb.append(((OcrPackage)pack).getLanguage().getDisplayName(context));
                            sb.append(" (OCR)");
                        }
                        sb.append("<br/>");
                    }
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.update_dialog_title)
                            .setMessage(Html.fromHtml(sb.toString()))
                            .setPositiveButton(R.string.update_dialog_update_button, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int id) {
                                    final ProgressDialog progressDialog = ProgressDialog.show(
                                            context,
                                            context.getResources().getString(R.string.update_progress_dialog_title),
                                            context.getResources().getString(R.string.update_progress_dialog_message),
                                            false,
                                            false);
                                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                    progressDialog.setMax(100);
                                    progressDialog.setProgress(0);
                                    updatePackages(
                                            updateablePackages,
                                            new Package.ProgressCallback() {
                                                @Override public void onProgress(int progress) {
                                                    progressDialog.setProgress(progress);
                                                }
                                            },
                                            new Package.UpdateCallback() {
                                                @Override public void onUpdate() {
                                                    progressDialog.dismiss();
                                                }
                                            },
                                            new Package.ExceptionCallback() {
                                                @Override public void onException(Exception exception) {
                                                    ACRA.getErrorReporter().handleSilentException(exception);
                                                    progressDialog.dismiss();
                                                    if (warnOnError) {
                                                        new AlertDialog.Builder(context)
                                                                .setTitle(R.string.error_dialog_title)
                                                                .setMessage(exception.getLocalizedMessage())
                                                                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                                                                    @Override public void onClick(DialogInterface dialog, int id) {
                                                                        dialog.dismiss();
                                                                    }
                                                                })
                                                                .create().show();
                                                    }
                                                }
                                            });
                                }
                            })
                            .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            })
                            .create().show();
                } else if (warnOnNoUpdate) {
                    new AlertDialog.Builder(context)
                            .setMessage(R.string.no_update_dialog_message)
                            .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }
            } else if (warnOnError) {
                ACRA.getErrorReporter().handleSilentException(exception);
                new AlertDialog.Builder(context)
                        .setTitle(R.string.error_dialog_title)
                        .setMessage(exception.getLocalizedMessage())
                        .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        }
    }

}
