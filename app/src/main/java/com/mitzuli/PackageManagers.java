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

package com.mitzuli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mitzuli.core.Package;
import com.mitzuli.core.PackageManager;
import com.mitzuli.core.mt.MtPackage;
import com.mitzuli.core.mt.MtPackageManager;
import com.mitzuli.core.mt.OnlineMtPackageManager;
import com.mitzuli.core.ocr.OcrPackage;
import com.mitzuli.core.ocr.OcrPackageManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.text.Html;

public class PackageManagers {

    private static final String ONLINE_MT_PACKAGES_MANIFEST_URL = "http://sourceforge.net/projects/mitzuli/files/packages/mt/online/manifest/download";
    private static final String BETA_MT_PACKAGES_MANIFEST_URL = "http://sourceforge.net/projects/mitzuli/files/packages/mt/beta/manifest/download";
    private static final String RELEASED_MT_PACKAGES_MANIFEST_URL = "http://sourceforge.net/projects/mitzuli/files/packages/mt/released/manifest/download";
    private static final String OCR_PACKAGES_MANIFEST_URL = "http://sourceforge.net/projects/mitzuli/files/packages/ocr/manifest/download";

    public static OnlineMtPackageManager onlineMtPackageManager;
    public static MtPackageManager betaMtPackageManager, releasedMtPackageManager;
    public static OcrPackageManager ocrPackageManager;

    public static interface ManifestsUpdateCallback {
        public void onManifestsUpdate();
    }

    public static void init(Context context) throws IOException {
        onlineMtPackageManager = new OnlineMtPackageManager(
                new File(context.getFilesDir(), "online_mt_packages"),
                new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.online_mt_packages_manifest))));
        betaMtPackageManager = new MtPackageManager(
                new File(context.getFilesDir(), "beta_mt_packages"),
                new File(context.getCacheDir(), "beta_mt_packages"),
                context.getSharedPreferences("beta_mt_packages", Context.MODE_PRIVATE),
                new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.beta_mt_packages_manifest))));

        releasedMtPackageManager = new MtPackageManager(
                new File(context.getFilesDir(), "released_mt_packages"),
                new File(context.getCacheDir(), "released_mt_packages"),
                context.getSharedPreferences("released_mt_packages", Context.MODE_PRIVATE),
                new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.released_mt_packages_manifest))));

        ocrPackageManager = new OcrPackageManager(
                new File(context.getFilesDir(), "ocr_packages"),
                new File(context.getCacheDir(), "ocr_packages"),
                context.getSharedPreferences("ocr_packages", Context.MODE_PRIVATE),
                new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.ocr_packages_manifest))));
    }

    public static String getName(Locale locale) {
        char[] chars = locale.getDisplayName().toLowerCase().toCharArray();
        boolean found = false;
        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i]) || chars[i]=='.' || chars[i]=='(') {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    public static void updatePackages(Context context, boolean warnOnNoUpdate, boolean warnOnError) {
        new UpdateTask(context, warnOnNoUpdate, warnOnError).execute();
    }

    public static void updatePackages(Context context, boolean warnOnNoUpdate, boolean warnOnError, ManifestsUpdateCallback manifestsUpdateCallback) {
        new UpdateTask(context, warnOnNoUpdate, warnOnError, manifestsUpdateCallback).execute();
    }

    private static class UpdateTask extends AsyncTask<Void, Void, Exception> {

        private final Context context;
        private final boolean warnOnNoUpdate;
        private final boolean warnOnError;
        private final ManifestsUpdateCallback manifestsUpdateCallback;

        public UpdateTask(Context context, boolean warnOnNoUpdate, boolean warnOnError) {
            this(context, warnOnNoUpdate, warnOnError, null);
        }

        public UpdateTask(Context context, boolean warnOnNoUpdate, boolean warnOnError, ManifestsUpdateCallback manifestsUpdateCallback) {
            this.context = context;
            this.warnOnNoUpdate = warnOnNoUpdate;
            this.warnOnError = warnOnError;
            this.manifestsUpdateCallback = manifestsUpdateCallback;
        }

        @Override
        protected Exception doInBackground(Void... args) {
            try {
                onlineMtPackageManager.updateManifest(new URL(ONLINE_MT_PACKAGES_MANIFEST_URL));
                betaMtPackageManager.updateManifest(new URL(BETA_MT_PACKAGES_MANIFEST_URL));
                releasedMtPackageManager.updateManifest(new URL(RELEASED_MT_PACKAGES_MANIFEST_URL));
                ocrPackageManager.updateManifest(new URL(OCR_PACKAGES_MANIFEST_URL));
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception exception) {
            if (exception == null) {
                if (manifestsUpdateCallback != null) manifestsUpdateCallback.onManifestsUpdate();

                final List<Package> updateablePackages = new ArrayList<Package>();
                updateablePackages.addAll(betaMtPackageManager.getUpdateablePackages());
                updateablePackages.addAll(releasedMtPackageManager.getUpdateablePackages());
                updateablePackages.addAll(ocrPackageManager.getUpdateablePackages());

                if (!updateablePackages.isEmpty()) {

                    final StringBuilder sb = new StringBuilder();
                    sb.append(context.getResources().getString(R.string.update_dialog_packages_to_update));
                    sb.append("<br/><br/>");
                    for (MtPackage pack : releasedMtPackageManager.getUpdateablePackages()) {
                        sb.append("&#8226; ");
                        sb.append(getName(pack.getSourceLanguage()));
                        sb.append(" → ");
                        sb.append(getName(pack.getTargetLanguage()));
                        sb.append("<br/>");
                    }
                    for (MtPackage pack : betaMtPackageManager.getUpdateablePackages()) {
                        sb.append("&#8226; ");
                        sb.append("[BETA]");
                        sb.append(" ");
                        sb.append(getName(pack.getSourceLanguage()));
                        sb.append(" → ");
                        sb.append(getName(pack.getTargetLanguage()));
                        sb.append("<br/>");
                    }
                    for (OcrPackage pack : ocrPackageManager.getUpdateablePackages()) {
                        sb.append("&#8226; ");
                        sb.append(getName(pack.getLanguage()));
                        sb.append(" (OCR)");
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
                                    PackageManager.updatePackages(
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
                                                    exception.printStackTrace();
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
