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
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mitzuli.Keys;
import com.mitzuli.core.Package;

import org.apertium.Translator;
import org.apertium.utils.IOUtils;
import org.json.JSONObject;

import de.timroes.axmlrpc.XMLRPCClient;

import dalvik.system.DexClassLoader;

import android.os.AsyncTask;
import android.text.Html;


public class MtPackage extends Package {

    private static final Pattern unknownPattern = Pattern.compile("\\B\\*((\\p{L}||\\p{N})+)\\b");
    private static final String unknownReplacement = "<font color='#EE0000'>$1</font>"; // TODO Shouldn't we escape the text as HTML???

    private final Locale srcLanguage, trgLanguage;

    private OfflineTranslationTask offlineTranslationTask;
    private ApyOnlineTranslationTask apyOnlineTranslationTask;
    private XmlrpcOnlineTranslationTask xmlrpcOnlineTranslationTask;

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
            offlineTranslationTask = new OfflineTranslationTask(markUnknown, translationCallback, exceptionCallback);
            offlineTranslationTask.execute(text);
        } else {
            // The package is not installed. We will try to:
            //    1) Translate online using apy
            //    2) If apy fails translate online using XML-RPC
            //    3) If XML-RPC fails install the language pair to the cache and translate from there
            final ExceptionCallback xmlrpcExceptionCallback = new ExceptionCallback() {
                @Override
                public void onException(Exception exception) {
                    if (isInstallable()) {
                        installToCache(
                                null,
                                new InstallCallback() {
                                    @Override
                                    public void onInstall() {
                                        offlineTranslationTask = new OfflineTranslationTask(markUnknown, translationCallback, exceptionCallback);
                                        offlineTranslationTask.execute(text);
                                    }
                                },
                                exceptionCallback);
                    } else {
                        exceptionCallback.onException(new Exception("Online translation failed and package not installable", exception));
                    }
                }
            };
            final ExceptionCallback apyExceptionCallback = new ExceptionCallback() {
                @Override
                public void onException(Exception exception) {
                    xmlrpcOnlineTranslationTask = new XmlrpcOnlineTranslationTask(markUnknown, translationCallback, xmlrpcExceptionCallback);
                    xmlrpcOnlineTranslationTask.execute(text);
                }
            };
            apyOnlineTranslationTask = new ApyOnlineTranslationTask(markUnknown, translationCallback, apyExceptionCallback);
            apyOnlineTranslationTask.execute(text);
        }
    }

    private class OfflineTranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public OfflineTranslationTask(boolean markUnknown, TranslationCallback translationCallback, ExceptionCallback exceptionCallback) {
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
            offlineTranslationTask = null;
            if (translation != null) translationCallback.onTranslationDone(translation);
            else if (exception != null) exceptionCallback.onException(exception);
        }

    }


    private class ApyOnlineTranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public ApyOnlineTranslationTask(boolean markUnknown, TranslationCallback translationCallback, ExceptionCallback exceptionCallback) {
            this.markUnknown = markUnknown;
            this.translationCallback = translationCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected String doInBackground(String... text) {
            try {
                String langpair = srcLanguage.getISO3Language();
                if (srcLanguage.getCountry().length() > 0) langpair += "_" + (srcLanguage.getCountry().equals("ARAN") ? "aran" : srcLanguage.getCountry());
                langpair += "|" + trgLanguage.getISO3Language();
                if (trgLanguage.getCountry().length() > 0) langpair += "_" + (trgLanguage.getCountry().equals("ARAN") ? "aran" : trgLanguage.getCountry());
                final String response = new Scanner(new URL(Keys.APERTIUM_APY_URL + "/translate?langpair=" + langpair + "&q=" + URLEncoder.encode(text[0], "UTF-8")).openStream()).useDelimiter("\\A").next();
                final String translation = Html.fromHtml(new JSONObject(response).getJSONObject("responseData").getString("translatedText")).toString();
                return "<html>" + unknownPattern.matcher(translation).replaceAll(markUnknown ? unknownReplacement : "$1").replaceAll("\n", "<br/>") + "<html>";
            } catch (Exception e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String translation) {
            apyOnlineTranslationTask = null;
            if (translation != null) {
                translationCallback.onTranslationDone(translation);
            } else if (exception != null) {
                exceptionCallback.onException(new Exception("apy translation failed", exception));
            }
        }

    }


    private class XmlrpcOnlineTranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public XmlrpcOnlineTranslationTask(boolean markUnknown, TranslationCallback translationCallback, ExceptionCallback exceptionCallback) {
            this.markUnknown = markUnknown;
            this.translationCallback = translationCallback;
            this.exceptionCallback = exceptionCallback;
        }

        @Override
        protected String doInBackground(String... text) {
            try {
                final XMLRPCClient client = new XMLRPCClient(new URL(Keys.APERTIUM_XMLRPC_URL), XMLRPCClient.FLAGS_DEFAULT_TYPE_STRING);
                client.setTimeout(5);
                final String translation = (String)client.call("service.translate", text[0], "txt", getId().split("-")[0], getId().split("-")[1], markUnknown, Keys.APERTIUM_API_KEY);
                return "<html>" + (markUnknown ? unknownPattern.matcher(translation).replaceAll(unknownReplacement) : translation).replaceAll("\n", "<br/>") + "<html>";
            } catch (Exception e) {
                exception = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(String translation) {
            xmlrpcOnlineTranslationTask = null;
            if (translation != null) {
                translationCallback.onTranslationDone(translation);
            } else if (exception != null) {
                exceptionCallback.onException(new Exception("XML-RPC translation failed", exception));
            }
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
