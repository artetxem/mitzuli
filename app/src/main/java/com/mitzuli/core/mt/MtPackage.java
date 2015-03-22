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

import android.os.AsyncTask;
import android.text.TextUtils;

import dalvik.system.DexClassLoader;

import com.mitzuli.Keys;
import com.mitzuli.core.KeyValueSaver;
import com.mitzuli.Language;
import com.mitzuli.core.Package;
import com.mitzuli.core.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MtPackage extends Package { // TODO Installing, updating or uninstalling this package while its translation task is running yields to undefined behavior

    private static final String ONLINE_SCALE_MT = "scale-mt";
    private static final String ONLINE_APERTIUM_APY = "apy";
    private static final String ONLINE_MATXIN = "matxin";
    private static final String ONLINE_ABUMATRAN = "abumatran";
    private static final String OFFLINE_APERTIUM = "apertium";

    private static final Pattern UNKNOWN_PATTERN = Pattern.compile("\\B\\*((\\p{L}||\\p{N})+)\\b");

    private final Language src;
    private final Language trg;

    public static interface TranslationCallback {
        public void onTranslationDone(String translation);
    }

    public static class Builder extends Package.Builder {

        private final Language src;
        private final Language trg;

        public Builder(PackageManager manager, KeyValueSaver saver, File packageDir, File cacheDir, File safeCacheDir, File tmpDir, Language src, Language trg) {
            super(manager, saver, packageDir, cacheDir, safeCacheDir, tmpDir);
            this.src = src;
            this.trg = trg;
        }

        @Override
        public MtPackage build() {
            return new MtPackage(this);
        }

    }

    private MtPackage(Builder builder) {
        super(builder);
        this.src = builder.src;
        this.trg = builder.trg;
    }

    @Override
    public boolean isSupported() {
        final List<String> supportedTypes = new ArrayList<String>();
        if (Keys.SCALE_MT_API_KEY != null) supportedTypes.add(ONLINE_SCALE_MT);
        supportedTypes.add(ONLINE_APERTIUM_APY);
        if (Keys.MATXIN_API_KEY != null) supportedTypes.add(ONLINE_MATXIN);
        supportedTypes.add(ONLINE_ABUMATRAN);
        supportedTypes.add(OFFLINE_APERTIUM);
        return isSupported(supportedTypes);
    }

    public Language getSourceLanguage() {
        return src;
    }

    public Language getTargetLanguage() {
        return trg;
    }

    public void translate(String text, TranslationCallback translationCallback, ExceptionCallback exceptionCallback, boolean markUnknown, boolean htmlOutput) {
        markUsage();
        new TranslationTask(translationCallback, exceptionCallback, markUnknown, htmlOutput).execute(text);
    }


    private class TranslationTask extends AsyncTask<String, Void, String> {

        private final boolean markUnknown;
        private final boolean htmlOutput;
        private final TranslationCallback translationCallback;
        private final ExceptionCallback exceptionCallback;
        private Exception exception;

        public TranslationTask(TranslationCallback translationCallback, ExceptionCallback exceptionCallback, boolean markUnknown, boolean htmlOutput) {
            this.translationCallback = translationCallback;
            this.exceptionCallback = exceptionCallback;
            this.markUnknown = markUnknown;
            this.htmlOutput = htmlOutput;
        }

        private String escape(String s) {
            return htmlOutput ? TextUtils.htmlEncode(s).replaceAll("\n", "<br/>") : s;
        }

        private String format(String s) {
            final Matcher matcher = UNKNOWN_PATTERN.matcher(s);
            final StringBuilder sb = new StringBuilder();
            if (htmlOutput) sb.append("<html>");
            int prevEnd = 0;
            while (matcher.find()) {
                sb.append(escape(s.substring(prevEnd, matcher.start())));
                if (markUnknown) sb.append(htmlOutput ? "<font color='#EE0000'>" : "*");
                sb.append(escape(matcher.group(1)));
                if (markUnknown && htmlOutput) sb.append("</font>");
                prevEnd = matcher.end();
            }
            sb.append(escape(s.substring(prevEnd)));
            if (htmlOutput) sb.append("</html>");
            return sb.toString();
        }

        @Override
        protected String doInBackground(final String... text) {
            final OfflineServiceProvider offline = getOfflineServiceProvider();
            if (offline == null) {
                for (OnlineServiceProvider online : getOnlineServiceProviders()) {
                    try {
                        if (online.type.equals(ONLINE_SCALE_MT)) {
                            return format(new ScaleMtTranslator(online.code, online.url, Keys.SCALE_MT_API_KEY).translate(text[0]));
                        } else if (online.type.equals(ONLINE_APERTIUM_APY)) {
                            return format(new ApyTranslator(online.code, online.url).translate(text[0]));
                        } else if (online.type.equals(ONLINE_MATXIN)) {
                            return format(new MatxinTranslator(online.code, online.url, Keys.MATXIN_API_KEY).translate(text[0]));
                        } else if (online.type.equals(ONLINE_ABUMATRAN)) {
                            return format(new AbumatranTranslator(online.code, online.url).translate(text[0]));
                        }
                    } catch (Exception e) {
                        exception = e;
                    }
                }
                if (isInstallable()) {
                    installToCache(null, new InstallCallback() {
                        @Override public void onInstall() {
                            translate(text[0], translationCallback, exceptionCallback, markUnknown, htmlOutput);
                        }
                    }, exceptionCallback);
                    return null;
                } else {
                    exception = new Exception("Online translation failed and package not installable", exception);
                    return null;
                }
            } else {
                try {
                    if (offline.type.equals(OFFLINE_APERTIUM)) {
                        final File jar = new File(offline.dir, "classes.jar");
                        if (!verifyFileIntegrity(jar)) throw new Exception("Package integrity verification failed");
                        final ClassLoader classLoader = new DexClassLoader(jar.getAbsolutePath(), getSafeCacheDir().getAbsolutePath(), null, getClass().getClassLoader());
                        return format(new ApertiumTranslator(offline.code, offline.dir, getCacheDir(), classLoader).translate(text[0]));
                    } else {
                        throw new Exception("Unknown engine: " + offline.type);
                    }
                } catch (Exception e) {
                    exception = e;
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(String translation) {
            if (translation != null) translationCallback.onTranslationDone(translation);
            else if (exception != null) exceptionCallback.onException(exception);
        }

    }

}
