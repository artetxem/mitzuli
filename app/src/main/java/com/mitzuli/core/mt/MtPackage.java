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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mitzuli.Keys;
import com.mitzuli.core.Package;

import org.apertium.Translator;
import org.apertium.utils.IOUtils;

import de.timroes.axmlrpc.XMLRPCCallback;
import de.timroes.axmlrpc.XMLRPCClient;

import dalvik.system.DexClassLoader;
import de.timroes.axmlrpc.XMLRPCException;
import de.timroes.axmlrpc.XMLRPCServerException;

import android.os.AsyncTask;


public class MtPackage extends Package {

    private static final Pattern unknownPattern = Pattern.compile("\\B\\*((\\p{L}||\\p{N})+)\\b");
    private static final String unknownReplacement = "<font color='#EE0000'>$1</font>";

    private final Locale srcLanguage, trgLanguage;

    private TranslationTask translationTask;

    public static interface TranslationCallback {
        public void onTranslationDone(String translation);
    }

    public MtPackage(String id, URL repoUrl, long repoVersion, LongSaver installedVersionSaver, LongSaver lastUsageSaver, File packageDir, File cacheDir, File tmpDir, MtPackageManager manager) {
        super(id, repoUrl, repoVersion, installedVersionSaver, lastUsageSaver, packageDir, cacheDir, tmpDir, manager);

        final String codes[] = id.split("-", 2);
        this.srcLanguage = codeToLocale(codes[0]);
        this.trgLanguage = codeToLocale(codes[1]);
    }

    public Locale getSourceLanguage() {
        return srcLanguage;
    }

    public Locale getTargetLanguage() {
        return trgLanguage;
    }

    public void translate(final String text, final boolean markUnknown, final TranslationCallback translationCallback, final ExceptionCallback exceptionCallback) {
        markUsage();
        if (getPackageDir() != null) {
            translationTask = new TranslationTask(markUnknown, translationCallback, exceptionCallback);
            translationTask.execute(text);
        } else {
            final XMLRPCCallback callback = new XMLRPCCallback() {
                @Override public void onResponse(long id, Object result) {
                    translationCallback.onTranslationDone((String)result);
                }
                @Override public void onError(long id, XMLRPCException e) {
                    translateFromCache(e);
                }
                @Override public void onServerError(long id, XMLRPCServerException e) {
                    translateFromCache(e);
                }
                private void translateFromCache(Exception e) {
                    if (isInstallable()) {
                        installToCache(
                                null,
                                new InstallCallback() {
                                    @Override
                                    public void onInstall() {
                                        translationTask = new TranslationTask(markUnknown, translationCallback, exceptionCallback);
                                        translationTask.execute(text);
                                    }
                                },
                                exceptionCallback);
                    } else {
                        exceptionCallback.onException(new Exception("Apertium web service failed and package not installable", e));
                    }
                }
            };
            try {
                new XMLRPCClient(new URL(Keys.APERTIUM_API_URL)).callAsync(callback, "service.translate", text, "txt", getId().split("-")[0], getId().split("-")[1], markUnknown, Keys.APERTIUM_API_KEY);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e); // We should never reach this
            }
        }
    }

    private class TranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public TranslationTask(boolean markUnknown, TranslationCallback translationCallback, ExceptionCallback exceptionCallback) {
            this.markUnknown = markUnknown;
            this.translationCallback = translationCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected String doInBackground(String... text) {
            try {
                final File packageDir = getPackageDir();
                if (packageDir == null) throw new Exception("Package not installed.");
                Translator.setDisplayMarks(markUnknown);
                Translator.setBase(packageDir.getAbsolutePath(), getClassLoader(packageDir));
                Translator.setMode(getId());
                IOUtils.cacheDir = getCacheDir();
                return "<html>" + (markUnknown ? unknownPattern.matcher(Translator.translate(text[0])).replaceAll(unknownReplacement) : Translator.translate(text[0])).replaceAll("\n", "<br/>") + "<html>";
            } catch (Exception e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String translation) {
            translationTask = null;
            if (translation != null) translationCallback.onTranslationDone(translation);
            else if (exception != null) exceptionCallback.onException(exception);
        }

    }

    private ClassLoader getClassLoader(File packageDir) throws IOException {
        final File jar = new File(packageDir, "classes.jar");
        if (!jar.exists()) {
            final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jar)));
            final byte buffer[] = new byte[1024];
            final ZipEntry ze = new ZipEntry("classes.dex");
            zos.putNextEntry(ze);
            final InputStream in = new FileInputStream(new File(packageDir, "classes.dex"));
            int len;
            while ((len = in.read(buffer)) > 0) zos.write(buffer, 0, len);
            in.close();
            zos.closeEntry();
            zos.close();
        }
        return new DexClassLoader(jar.getAbsolutePath(), getCacheDir().getAbsolutePath(), null, getClass().getClassLoader());
        //return new DexClassLoader(new File(packageDir, "classes.dex").getAbsolutePath(), cacheDir.getAbsolutePath(), null, getClass().getClassLoader());
    }

    private static Locale codeToLocale(String code) {
        final String args[] = code.split("_", 2);
        if (args.length == 1) return new Locale(args[0]);
        else return new Locale(args[0], args[1]);
    }

}
