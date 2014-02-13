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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class SettingsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        findPreference("pref_key_check_updates").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
                if (checkInternetAccess(getResources().getString(R.string.offline_on_update_error_title), getResources().getString(R.string.offline_on_update_error_message))) {
                    PackageManagers.updatePackages(SettingsActivity.this, true, true);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                finish();
                //NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private Dialog getLicenseDialog() {
        final WebView webview = new WebView(this);
        webview.loadUrl("file:///android_asset/licenses.html");
        webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.pref_title_licenses)
                .setView(webview)
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

}
