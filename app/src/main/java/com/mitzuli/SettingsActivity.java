/*
 * Copyright (C) 2014-2015 Mikel Artetxe <artetxem@gmail.com>
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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.mitzuli.core.PackageManager;

import java.util.SortedMap;
import java.util.TreeMap;


public class SettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String DEFAULT_LANGUAGE= "pref_value_default_language";

    private ListPreference displayLanguagePreference;
    private String displayLanguage;

    private CheckBoxPreference externalStoragePreference;
    private boolean externalStorage;

    private AppCompatDelegate delegate;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        final Toolbar toolbar = (Toolbar)View.inflate(this, R.layout.toolbar_actionbar, null);
        final ViewGroup root = (ViewGroup)findViewById(android.R.id.content);
        if (!(root.getChildAt(0) instanceof LinearLayout)) { // If the child is not a linear layout we will create one for it
            final View content = root.getChildAt(0);
            root.removeAllViews();
            final LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(content);
            root.addView(container);
        }
        ((LinearLayout)root.getChildAt(0)).addView(toolbar, 0);

        getDelegate().setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.settings);
        final TypedArray ta = getDelegate().getSupportActionBar().getThemedContext().obtainStyledAttributes(new int[]{R.attr.homeAsUpIndicator});
        toolbar.setNavigationIcon(ta.getDrawable(0));
        ta.recycle();

        addPreferencesFromResource(R.xml.preferences);

        final SortedMap<String, String> languageToCode = new TreeMap<String, String>();
        for (String code: getResources().getStringArray(R.array.supported_display_languages)) {
            languageToCode.put(new Language(code).getDisplayLanguage(this), code);
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
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
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
                        PackageManager.fromContext(SettingsActivity.this).showUpdateDialog(SettingsActivity.this, Keys.REPO_URL, true, true);
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
        findPreference("pref_key_acknowledgments").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
                getAcknowledgmentsDialog().show();
                return true;
            }
        });
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    @Override
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }

    @Override
    public void setContentView(int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getDelegate().onDestroy();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (key.equals("pref_key_display_language") && !displayLanguage.equals(sharedPreferences.getString(key, DEFAULT_LANGUAGE))) {
            new AlertDialog.Builder(this)
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
            new AlertDialog.Builder(this)
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
        final WebView webview = new WebView(this);
        webview.loadUrl("file:///android_asset/licenses.html");
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.pref_title_licenses)
                .setView(webview)
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();
    }


    private Dialog getAcknowledgmentsDialog() {
        final ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.claim_ej);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.pref_title_acknowledgments)
                .setMessage(getResources().getText(R.string.pref_dialog_message_acknowledgments) + "\n")
                .setView(logo)
                .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .create();
    }


    private boolean isOnline() {
        final NetworkInfo networkInfo = ((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }


    private boolean checkInternetAccess(String errorTitle, String errorMessage) {
        if (isOnline()) {
            return true;
        } else {
            new AlertDialog.Builder(this)
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


    private AppCompatDelegate getDelegate() {
        if (delegate == null) delegate = AppCompatDelegate.create(this, null);
        return delegate;
    }

}