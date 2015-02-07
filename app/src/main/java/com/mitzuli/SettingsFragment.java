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

import java.util.SortedMap;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import com.mitzuli.core.PackageManager;


public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String DEFAULT_LANGUAGE= "pref_value_default_language";

    private ListPreference displayLanguagePreference;
    private String displayLanguage;

    private CheckBoxPreference externalStoragePreference;
    private boolean externalStorage;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        final SortedMap<String, String> languageToCode = new TreeMap<String, String>();
        for (String code: getResources().getStringArray(R.array.supported_display_languages)) {
            languageToCode.put(new Language(code).getDisplayLanguage(getActivity()), code);
        }
        final String displayLanguageEntryValues[] = new String[languageToCode.size()+1];
        final String displayLanguageEntries[] = new String[languageToCode.size()+1];
        displayLanguageEntryValues[0] = DEFAULT_LANGUAGE;
        displayLanguageEntries[0] = getResources().getString(R.string.pref_entry_value_default_display_language);
        int i = 1;
        for (SortedMap.Entry<String, String> entry : languageToCode.entrySet()) {
            displayLanguageEntryValues[i] = entry.getValue();
            displayLanguageEntries[i] = entry.getKey();
            i++;
        }
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);
        displayLanguage = preferences.getString("pref_key_display_language", DEFAULT_LANGUAGE);
        displayLanguagePreference = (ListPreference)findPreference("pref_key_display_language");
        displayLanguagePreference.setEntryValues(displayLanguageEntryValues);
        displayLanguagePreference.setEntries(displayLanguageEntries);
        displayLanguagePreference.setValue(displayLanguage);
        displayLanguagePreference.setSummary(displayLanguagePreference.getEntry());
        externalStorage = preferences.getBoolean("pref_key_external_storage", true);
        externalStoragePreference = (CheckBoxPreference)findPreference("pref_key_external_storage");

        findPreference("pref_key_check_updates").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
                if (checkInternetAccess(getResources().getString(R.string.offline_on_update_error_title), getResources().getString(R.string.offline_on_update_error_message))) {
                    try {
                        PackageManager.fromContext(getActivity()).showUpdateDialog(getActivity(), Keys.REPO_URL, true, true);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                return true;
            }
        });
        findPreference("pref_key_licenses").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
                getLicenseDialog().show();
                return true;
            }
        });
    }


    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (key.equals("pref_key_display_language") && !displayLanguage.equals(sharedPreferences.getString(key, DEFAULT_LANGUAGE))) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_dialog_title_confirm_display_language)
                    .setMessage(R.string.pref_dialog_message_confirm_display_language)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override public void onCancel(DialogInterface dialog) {
                            displayLanguagePreference.setValue(displayLanguage);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int id) {
                            displayLanguagePreference.setValue(displayLanguage);
                            dialog.dismiss();
                        }
                    })
                    .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int id) {
                            System.exit(0);
                        }
                    })
                    .create().show();
        }
        if (key.equals("pref_key_external_storage") && externalStorage != sharedPreferences.getBoolean(key, true)) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_dialog_title_confirm_external_storage)
                    .setMessage(R.string.pref_dialog_message_confirm_external_storage)
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            externalStoragePreference.setChecked(externalStorage);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            externalStoragePreference.setChecked(externalStorage);
                            dialog.dismiss();
                        }
                    })
                    .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        }
    }


    private Dialog getLicenseDialog() {
        final WebView webview = new WebView(getActivity());
        webview.loadUrl("file:///android_asset/licenses.html");
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.pref_title_licenses)
                .setView(webview)
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();
    }


    private boolean isOnline() {
        final NetworkInfo networkInfo = ((ConnectivityManager)getActivity().getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }


    private boolean checkInternetAccess(String errorTitle, String errorMessage) {
        if (isOnline()) {
            return true;
        } else {
            new AlertDialog.Builder(getActivity())
                    .setTitle(errorTitle)
                    .setMessage(errorMessage)
                    .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
            return false;
        }
    }

}
