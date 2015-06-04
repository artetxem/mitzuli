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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.mitzuli.core.Package;
import com.mitzuli.core.PackageManager;
import com.mitzuli.core.mt.MtPackage;
import com.mitzuli.core.ocr.OcrPackage;
import com.mitzuli.core.ocr.TesseractTextRecognizer;
import com.mitzuli.core.speech.Stt;
import com.mitzuli.core.speech.Tts;

import org.acra.ACRA;
import org.opencv.android.OpenCVLoader;

import com.f2prateek.progressbutton.ProgressButton;

import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements OnClickListener, AdapterView.OnItemSelectedListener, Toolbar.OnMenuItemClickListener {

    private static final boolean OPENCV_AVAILABLE = OpenCVLoader.initDebug();

    private static final String STATE_SRC_CARD_VISIBILITY = "SRC_CARD_VISIBILITY";
    private static final String STATE_TRG_CARD_VISIBILITY = "TRG_CARD_VISIBILITY";
    private static final String STATE_SRC_TEXT = "SRC_TEXT";
    private static final String STATE_TRG_TEXT = "TRG_TEXT";
    private static final String PREFS_LAST_PAIR = "LAST_PAIR";
    private static final int SETTINGS_ACTIVITY_ID = 1;
    private static final int ENABLED_ICON_ALPHA = 60*(255-51)/100;
    private static final int DISABLED_ICON_ALPHA = 30*(255-51)/100;
    private static final int EXPANDER_ICON_ALPHA = 114;


    private PackageManager packageManager;
    private List<LanguagePair> languagePairs;
    private LanguagePair activePair;
    private Tts tts;
    private Stt stt;

    private boolean ttsLoaded = false;
    private boolean sttLoaded = false;
    private boolean cameraLoaded = false;

    private boolean ttsAvailable = false;
    private boolean sttAvailable = false;
    private boolean cameraAvailable = false;

    private boolean editing = false;
    private boolean ttsRequest = false;

    private boolean updatedManifest = false;

    private SharedPreferences preferences;

    private LinearLayout mainPanel;

    private Spinner actionBarSpinner;

    private CardView srcCard;
    private Toolbar srcToolbar;
    private LinearLayout srcContent;

    private EditText srcText;
    private SttView srcMic;
    private CameraCropperView srcCamera;
    private ProgressBar srcProgressBar;

    private Button translateButton;

    private CardView trgCard;
    private Toolbar trgToolbar;
    private LinearLayout trgContent;

    private ScrollView trgTextScroll;
    private TextView trgText;
    private ProgressBar trgProgressBar;

    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateSrcToolbar();
            updateTrgToolbar();
            updateManifest();
        }
    };

    private CameraCropperView.OpenCameraCallback openCameraCallback = new CameraCropperView.OpenCameraCallback() {
        @Override public void onCameraOpened(boolean success) {
            if (success) srcCamera.updateDisplayOrientation(getApplicationContext());
            cameraLoaded = true;
            cameraAvailable = success;
            updateSrcToolbar();
        }
    };

    private CameraCropperView.CroppedPictureCallback croppedPictureCallback = new CameraCropperView.CroppedPictureCallback() {
        @Override public void onPictureCropped(Image croppedPicture) {
            setSrcContent(srcProgressBar);
            TesseractTextRecognizer.DEBUG = preferences.getBoolean("pref_key_ocr_debugging", false); // TODO Temporary workaround to allow to manually enable debugging
            activePair.ocrPackage.recognizeText(croppedPicture, ocrCallback, exceptionCallback);
        }
    };

    private OcrPackage.OcrCallback ocrCallback = new OcrPackage.OcrCallback() {
        @Override public void onTextRecognized(String text) {
            srcText.setText(text);
            setSrcContent(srcText);
            activePair.mtPackage.translate(
                    srcText.getText().toString(),
                    translationCallback,
                    exceptionCallback,
                    preferences.getBoolean("pref_key_mark_unknown", true),
                    true);
        }
    };

    private MtPackage.TranslationCallback translationCallback = new MtPackage.TranslationCallback() {
        @Override public void onTranslationDone(String translation) {
            trgText.setText(Html.fromHtml(translation));
            setTrgContent(trgTextScroll);
            if (ttsRequest && tts.isLanguageAvailable(activePair.mtPackage.getTargetLanguage().toLocale())) {
                tts.speak(trgText.getText().toString(), activePair.mtPackage.getTargetLanguage().toLocale());
            }
            ttsRequest = false;
        }
    };

    private Package.ExceptionCallback exceptionCallback = new Package.ExceptionCallback() {
        @Override public void onException(Exception exception) {
            ACRA.getErrorReporter().handleSilentException(exception);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.error_dialog_title)
                    .setMessage(exception.getLocalizedMessage())
                    .setPositiveButton(R.string.ok_button, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
            setSrcContent(srcText);
            setTrgContent(trgTextScroll);
        }
    };

    private Stt.RecognitionCallback sttRecognitionCallback = new Stt.RecognitionCallback() {
        @Override public void onRecognitionDone(String recognizedText) {
            if (recognizedText.length() > 0) srcText.setText(recognizedText);
            setSrcContent(srcText);
            if (recognizedText.length() > 0) {
                setTrgContent(trgProgressBar);
                ttsRequest = preferences.getBoolean("pref_key_auto_tts", true);
                activePair.mtPackage.translate(
                        recognizedText,
                        translationCallback,
                        exceptionCallback,
                        preferences.getBoolean("pref_key_mark_unknown", true),
                        true);
            } else if (trgContent.getChildAt(0) == trgProgressBar) { // The user pressed the "TRANSLATE" button but there was no speech input
                srcText.setText("");
                trgText.setText("");
                setTrgContent(trgTextScroll);
            }
        }
    };

    private Stt.ExceptionCallback sttExceptionCallback = new Stt.ExceptionCallback() {
        @Override public void onException(Exception exception) {
            exceptionCallback.onException(exception); // TODO Review this
        }
    };

    private Stt.ProgressCallback sttProgressCallback = new Stt.ProgressCallback() {
        @Override public void onReadyForSpeech() {
            srcMic.setStatusText(getResources().getText(R.string.speak_now));
            srcMic.setRecognizedText("");
            srcMic.setRms(SttView.MIN_RMS_DB);
            setSrcContent(srcMic);
        }
        @Override public void onRmsChanged(float rmsdB) {
            srcMic.setRms(rmsdB);
        }
        @Override public void onPartialResults(String recognizedText) {
            srcMic.setRecognizedText(recognizedText);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        mainPanel = (LinearLayout)findViewById(R.id.activity_main);

        srcCard = (CardView)findViewById(R.id.src_card);
        srcToolbar = (Toolbar)findViewById(R.id.src_toolbar);
        srcToolbar.inflateMenu(R.menu.src_card);
        srcToolbar.setOnMenuItemClickListener(this);
        srcToolbar.setOnClickListener(this);
        srcContent = (LinearLayout)findViewById(R.id.src_content);

        translateButton = (Button)findViewById(R.id.translate_button);
        translateButton.setOnClickListener(this);

        trgCard = (CardView)findViewById(R.id.trg_card);
        trgToolbar = (Toolbar)findViewById(R.id.trg_toolbar);
        trgToolbar.inflateMenu(R.menu.trg_card);
        trgToolbar.setOnMenuItemClickListener(this);
        trgToolbar.setOnClickListener(this);
        trgContent = (LinearLayout)findViewById(R.id.trg_content);

        srcText = (EditText)findViewById(R.id.src_text);
        srcMic = new SttView(getApplicationContext());
        srcCamera = new CameraCropperView(getApplicationContext());
        srcCamera.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        srcProgressBar = new ProgressBar(getApplicationContext());
        srcProgressBar.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        trgTextScroll = (ScrollView)findViewById(R.id.trg_text_scroll);
        trgText = (TextView)findViewById(R.id.trg_text);
        trgProgressBar = new ProgressBar(getApplicationContext());
        trgProgressBar.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        mainPanel.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            private int trgCardPreEditingVisibility;
            @Override public void onGlobalLayout() {
                final int heightDiffPx = mainPanel.getRootView().getHeight() - mainPanel.getHeight();
                final int heightDiffDp = (int) (heightDiffPx / (getResources().getDisplayMetrics().densityDpi / 160f));
                if (heightDiffDp > 150 && !editing) { // if more than 150 dps, its probably a keyboard...
                    editing = true;
                    trgCardPreEditingVisibility = trgCard.getVisibility();
                    trgCard.setVisibility(View.GONE);
                    updateSrcToolbar();
                } else if (heightDiffDp < 150 && editing) {
                    editing = false;
                    trgCard.setVisibility(trgCardPreEditingVisibility);
                    srcText.clearFocus();
                    updateSrcToolbar();
                }
            }
        });

        final Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(toolbar);
        final Context context = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? this : getSupportActionBar().getThemedContext();
        final LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        actionBarSpinner = (Spinner)inflater.inflate(R.layout.actionbar_spinner, null);
        actionBarSpinner.setOnItemSelectedListener(this);
        toolbar.addView(actionBarSpinner);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        try {
            packageManager = PackageManager.fromContext(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tts = new Tts(getApplicationContext(), new Tts.OnInitListener() {
            @Override public void onInit(boolean success) {
                ttsLoaded = true;
                ttsAvailable = success;
                if (activePair != null) {
                    updateSrcToolbar();
                    updateTrgToolbar();
                }
            }
        });

        stt = new Stt(getApplicationContext(), new Stt.OnInitListener() {
            @Override public void onInit(boolean success) {
                sttLoaded = true;
                sttAvailable = success;
                if (activePair != null) {
                    updateSrcToolbar();
                }
            }
        });

        refreshLanguagePairs();
        updateManifest();

        final Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            srcText.setText(intent.getStringExtra(Intent.EXTRA_TEXT));
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(STATE_SRC_CARD_VISIBILITY, srcCard.getVisibility());
        savedInstanceState.putInt(STATE_TRG_CARD_VISIBILITY, trgCard.getVisibility());
        savedInstanceState.putString(STATE_SRC_TEXT, Html.toHtml(srcText.getText()));
        savedInstanceState.putString(STATE_TRG_TEXT, Html.toHtml(trgText.getEditableText()));
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        final int srcCardVisibility = savedInstanceState.getInt(STATE_SRC_CARD_VISIBILITY);
        final int trgCardVisibility = savedInstanceState.getInt(STATE_TRG_CARD_VISIBILITY);
        if (srcCardVisibility == View.VISIBLE && trgCardVisibility == View.GONE) {
            trgCard.setVisibility(View.GONE);
        } else if (srcCardVisibility == View.GONE && trgCardVisibility == View.VISIBLE) {
            srcCard.setVisibility(View.GONE);
        }
        srcText.setText(Html.fromHtml(savedInstanceState.getString(STATE_SRC_TEXT)));
        trgText.setText(Html.fromHtml(savedInstanceState.getString(STATE_TRG_TEXT)));
        updateSrcToolbar();
        updateTrgToolbar();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SETTINGS_ACTIVITY_ID) {
            try {
                packageManager = PackageManager.fromContext(this);
                refreshLanguagePairs();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (OPENCV_AVAILABLE) {
            srcCamera.openCamera(getApplicationContext(), openCameraCallback);
        } else {
            ACRA.getErrorReporter().handleSilentException(new RuntimeException("Unexpected error while loading OpenCV"));
            cameraLoaded = true;
            cameraAvailable = false;
        }
        registerReceiver(connectivityChangeReceiver, new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        updateSrcToolbar();
        updateTrgToolbar();
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(connectivityChangeReceiver);
        srcCamera.releaseCamera();
        cameraLoaded = false;
        cameraAvailable = false;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (cameraAvailable && srcCamera.isOpened()) srcCamera.updateDisplayOrientation(getApplicationContext());
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action buttons
        switch(item.getItemId()) {
            case R.id.action_settings:
                final Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivityForResult(intent, SETTINGS_ACTIVITY_ID);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setActivePair(languagePairs.get(position));
    }


    @Override
    public void onNothingSelected(AdapterView<?> parent) {}


    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.shutdown();
        stt.shutdown();
    }


    @Override
    public void onClick(View view) {
        if (view == translateButton && (activePair.mtPackage.isInstalled() || checkInternetAccess(getResources().getString(R.string.offline_on_translate_error_title), getResources().getString(R.string.offline_on_translate_error_message)))) {
            if (srcContent.getChildAt(0) == srcMic) { // Voice input mode
                setTrgContent(trgProgressBar);
                stt.stopRecognition();
            } else if (srcContent.getChildAt(0) == srcCamera) { // Camera input mode
                setTrgContent(trgProgressBar);
                srcCamera.takeCroppedPicture(croppedPictureCallback); // Do the OCR stuff
            } else if (srcContent.getChildAt(0) == srcText) { // Text input mode
                if (editing) hideSoftKeyboard();
                setTrgContent(trgProgressBar);
                activePair.mtPackage.translate(
                        srcText.getText().toString(),
                        translationCallback,
                        exceptionCallback,
                        preferences.getBoolean("pref_key_mark_unknown", true),
                        true);
            }
        } else if (view == srcToolbar && !editing) {
            trgCard.setVisibility(trgCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateSrcToolbar();
        } else if (view == trgToolbar) {
            srcCard.setVisibility(srcCard.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            updateTrgToolbar();
        }
    }


    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_src_audio:
                if (!tts.isLanguageAvailable(activePair.mtPackage.getSourceLanguage().toLocale())) {
                    Toast.makeText(getApplicationContext(), isOnline() ? R.string.toast_unavailable_tts : R.string.toast_unavailable_tts_offline, Toast.LENGTH_SHORT).show();
                } else {
                    tts.speak(srcText.getText().toString(), activePair.mtPackage.getSourceLanguage().toLocale());
                }
                return true;
            case R.id.action_mic:
                if (!isOnline()) {
                    Toast.makeText(getApplicationContext(), R.string.toast_unavailable_stt_offline, Toast.LENGTH_SHORT).show();
                } else if (!stt.isLanguageAvailable(activePair.mtPackage.getSourceLanguage().toLocale())) {
                    Toast.makeText(getApplicationContext(), R.string.toast_unavailable_stt, Toast.LENGTH_SHORT).show();
                } else {
                    if (editing) hideSoftKeyboard();
                    setSrcContent(srcProgressBar);
                    stt.recognize(activePair.mtPackage.getSourceLanguage().toLocale(), sttRecognitionCallback, sttExceptionCallback, sttProgressCallback);
                }
                return true;
            case R.id.action_camera:
                if (activePair.ocrPackage == null) {
                    Toast.makeText(getApplicationContext(), R.string.toast_unavailable_ocr, Toast.LENGTH_SHORT).show();
                } else {
                    if (editing) hideSoftKeyboard();
                    setSrcContent(srcCamera);
                }
                return true;
            case R.id.action_keyboard:
                if (srcContent.getChildAt(0) == srcMic) stt.cancelRecognition();
                setSrcContent(srcText);
                return true;
            case R.id.action_clear:
                srcText.setText("");
                return true;
            case R.id.action_trg_audio:
                if (!tts.isLanguageAvailable(activePair.mtPackage.getTargetLanguage().toLocale())) {
                    Toast.makeText(getApplicationContext(), isOnline() ? R.string.toast_unavailable_tts : R.string.toast_unavailable_tts_offline, Toast.LENGTH_SHORT).show();
                } else {
                    tts.speak(trgText.getText().toString(), activePair.mtPackage.getTargetLanguage().toLocale());
                }
                return true;
            case R.id.action_copy: // TODO Should we use conditional code depending on the Android version?
                android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(trgText.getText());
                Toast.makeText(getApplicationContext(), R.string.translation_copied, Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_share:
                final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, trgText.getText().toString());
                startActivity(Intent.createChooser(intent, getResources().getText(R.string.share_with)));
                return true;
            default:
                return false;
        }
    }


    private void hideSoftKeyboard() {
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(srcText.getApplicationWindowToken(), 0);
    }


    private void updateMenuItem(MenuItem item, boolean isVisible, boolean isAvailable, boolean isEnabled) {
        item.setVisible(isVisible);
        item.getIcon().setAlpha(isAvailable && isEnabled ? ENABLED_ICON_ALPHA : DISABLED_ICON_ALPHA);
        item.setEnabled(isEnabled);
    }


    private void updateSrcToolbar() {
        final View view = srcContent.getChildAt(0);
        final Language language = activePair.mtPackage.getSourceLanguage();
        final boolean isLoaded = ttsLoaded && sttLoaded && cameraLoaded;
        final boolean isAudioVisible = isLoaded && ttsAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isAudioAvailable = language != null && tts.isLanguageAvailable(language.toLocale());
        final boolean isAudioEnabled = view == srcText;
        final boolean isMicVisible = isLoaded && sttAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isMicAvailable = language != null && stt.isLanguageAvailable(language.toLocale()) && isOnline();
        final boolean isMicEnabled = view == srcText;
        final boolean isCameraVisible = isLoaded && cameraAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isCameraAvailable = activePair.ocrPackage != null;
        final boolean isCameraEnabled = view == srcText;
        final boolean isKeyboardVisible = view == srcCamera || view == srcMic;
        final boolean isKeyboardAvailable = true;
        final boolean isKeyboardEnabled = true;
        final boolean isClearVisible = view == srcText && editing;
        final boolean isClearAvailable = true;
        final boolean isClearEnabled = true;
        final boolean isLoadingVisible = !isLoaded && !isKeyboardVisible && !isClearVisible;
        srcToolbar.setTitle(activePair.mtPackage.getSourceLanguage().getDisplayName(this));
        srcToolbar.setNavigationIcon(trgCard.getVisibility() == View.VISIBLE ? R.drawable.ic_expander_down : editing ? R.drawable.ic_expander_right : R.drawable.ic_expander_up);
        srcToolbar.getNavigationIcon().setAlpha(EXPANDER_ICON_ALPHA);
        updateMenuItem(srcToolbar.getMenu().findItem(R.id.action_src_audio), isAudioVisible, isAudioAvailable, isAudioEnabled);
        updateMenuItem(srcToolbar.getMenu().findItem(R.id.action_mic), isMicVisible, isMicAvailable, isMicEnabled);
        updateMenuItem(srcToolbar.getMenu().findItem(R.id.action_camera), isCameraVisible, isCameraAvailable, isCameraEnabled);
        updateMenuItem(srcToolbar.getMenu().findItem(R.id.action_keyboard), isKeyboardVisible, isKeyboardAvailable, isKeyboardEnabled);
        updateMenuItem(srcToolbar.getMenu().findItem(R.id.action_clear), isClearVisible, isClearAvailable, isClearEnabled);
        srcToolbar.findViewById(R.id.loading_indicator).setVisibility(isLoadingVisible ? View.VISIBLE : View.GONE);
        srcToolbar.findViewById(R.id.menu_separator).setVisibility(isAudioVisible || isMicVisible || isCameraVisible || isKeyboardVisible || isClearVisible || isLoadingVisible ? View.VISIBLE : View.GONE);
    }


    private void updateTrgToolbar() {
        final View view = trgContent.getChildAt(0);
        final Language language = activePair.mtPackage.getTargetLanguage();
        final boolean isLoaded = ttsLoaded;
        final boolean isAudioVisible = isLoaded && ttsAvailable && (view == trgTextScroll || view == trgProgressBar);
        final boolean isAudioAvailable = language != null && tts.isLanguageAvailable(language.toLocale());
        final boolean isAudioEnabled = view == trgTextScroll;
        final boolean isShareVisible = isLoaded && (view == trgTextScroll || view == trgProgressBar);
        final boolean isShareAvailable = true;
        final boolean isShareEnabled = view == trgTextScroll;
        final boolean isCopyVisible = isLoaded && (view == trgTextScroll || view == trgProgressBar);
        final boolean isCopyAvailable = true;
        final boolean isCopyEnabled = view == trgTextScroll;
        final boolean isLoadingVisible = !isLoaded;
        trgToolbar.setTitle(activePair.mtPackage.getTargetLanguage().getDisplayName(this));
        trgToolbar.setNavigationIcon(srcCard.getVisibility() == View.VISIBLE ? R.drawable.ic_expander_up : R.drawable.ic_expander_down);
        trgToolbar.getNavigationIcon().setAlpha(EXPANDER_ICON_ALPHA);
        updateMenuItem(trgToolbar.getMenu().findItem(R.id.action_trg_audio), isAudioVisible, isAudioAvailable, isAudioEnabled);
        updateMenuItem(trgToolbar.getMenu().findItem(R.id.action_copy), isCopyVisible, isCopyAvailable, isCopyEnabled);
        updateMenuItem(trgToolbar.getMenu().findItem(R.id.action_share), isShareVisible, isShareAvailable, isShareEnabled);
        trgToolbar.findViewById(R.id.loading_indicator).setVisibility(isLoadingVisible ? View.VISIBLE : View.GONE);
        trgToolbar.findViewById(R.id.menu_separator).setVisibility(isAudioVisible || isShareVisible || isCopyVisible || isLoadingVisible ? View.VISIBLE : View.GONE);
    }


    private void setSrcContent(View view) {
        if (view != srcContent.getChildAt(0)) {
            srcContent.removeAllViews();
            srcContent.setGravity(view == srcText ? Gravity.TOP : Gravity.CENTER);
            srcContent.addView(view);
        }
        translateButton.setEnabled(view != srcProgressBar && trgContent.getChildAt(0) != trgProgressBar);
        updateSrcToolbar();
    }


    private void setTrgContent(View view) {
        if (view != trgContent.getChildAt(0)) {
            trgContent.removeAllViews();
            trgContent.setGravity(view == trgTextScroll ? Gravity.TOP : Gravity.CENTER);
            trgContent.addView(view);
        }
        translateButton.setEnabled(srcContent.getChildAt(0) != srcProgressBar && view != trgProgressBar);
        updateTrgToolbar();
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


    private void setActivePair(LanguagePair pair) {
        activePair = pair;
        if (pair != null) {
            setTitle(pair.name);
            preferences.edit().putString(PREFS_LAST_PAIR, pair.mtPackage.getSourceLanguage().toTag() + "." + pair.mtPackage.getTargetLanguage().toTag()).commit();
            for (int i = 0; i < languagePairs.size(); i++) {
                if (pair.mtPackage.equals(languagePairs.get(i).mtPackage)) {
                    actionBarSpinner.setSelection(i);
                    break;
                }
            }
            setSrcContent(srcText);
            setTrgContent(trgTextScroll);
        }
    }


    private void refreshLanguagePairs() {
        languagePairs = new ArrayList<LanguagePair>();
        for (MtPackage translatorPackage : packageManager.getMtPackages()) {
            languagePairs.add(new LanguagePair(
                    (translatorPackage.isBeta() ? "[BETA] " : "") +
                    translatorPackage.getSourceLanguage().getDisplayName(this) + " → " + translatorPackage.getTargetLanguage().getDisplayName(this),
                    translatorPackage,
                    packageManager.ocrPackageForLanguage(translatorPackage.getSourceLanguage())
            ));
        }
        Collections.sort(languagePairs);
        actionBarSpinner.setAdapter(new LanguagePairAdapter(getSupportActionBar().getThemedContext()));

        // Not the most efficient way of doing it, but it's clear and fast enough
        final String lastPairId = preferences.getString(PREFS_LAST_PAIR, null);
        LanguagePair lastPair = null;
        for (LanguagePair pair : languagePairs) if ((pair.mtPackage.getSourceLanguage().toTag() + "." + pair.mtPackage.getTargetLanguage().toTag()).equals(lastPairId)) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null && pair.mtPackage.isInstalled() && pair.mtPackage.getSourceLanguage().getLanguage().equals(Language.forLocale(Locale.getDefault()).getLanguage())) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null && pair.mtPackage.isInstalled()) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null && pair.mtPackage.getSourceLanguage().getLanguage().equals(Language.forLocale(Locale.getDefault()).getLanguage())) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null) lastPair = pair;
        setActivePair(lastPair);
    }


    private void updateManifest() {
        if (!updatedManifest && isOnline()) {
            if (preferences.getBoolean("pref_key_autocheck_updates", true)) {
                packageManager.showUpdateDialog(this, Keys.REPO_URL, false, false, new PackageManager.ManifestsUpdateCallback() {
                    @Override public void onManifestsUpdate() {
                        refreshLanguagePairs();
                    }
                });
            } else {
                packageManager.updateManifest(Keys.REPO_URL, new PackageManager.ManifestsUpdateCallback(){
                    @Override public void onManifestsUpdate() {
                        refreshLanguagePairs();
                    }
                });
            }
            updatedManifest = true;
        }
    }


    private static class LanguagePair implements Comparable<LanguagePair> {
        public final String name;
        public final MtPackage mtPackage;
        public final OcrPackage ocrPackage;

        public LanguagePair(String name, MtPackage mtPackage, OcrPackage ocrPackage) {
            this.name = name;
            this.mtPackage = mtPackage;
            this.ocrPackage = ocrPackage;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int compareTo(LanguagePair another) {
            return this.name.compareTo(another.name);
        }
    }


    private class LanguagePairAdapter extends ArrayAdapter<LanguagePair> {

        public LanguagePairAdapter(Context context) {
            super(context, R.layout.actionbar_spinner_main_view, languagePairs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.actionbar_spinner_main_view, parent, false);
            ((TextView)convertView).setText(languagePairs.get(position).name);
            return convertView;
        }

        @Override
        public View getDropDownView(final int position, View convertView, ViewGroup parent) {
            final LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.actionbar_spinner_dropdown_view, parent, false);

            final TextView pairTxt = (TextView) convertView.findViewById(R.id.pairTxt);
            final ProgressButton progress = (ProgressButton) convertView.findViewById(R.id.progressBtn);

            if (!languagePairs.get(position).mtPackage.isInstallable()) progress.setVisibility(View.INVISIBLE);

            progress.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    final LanguagePair pair = languagePairs.get(position);
                    if (!progress.isPinned()) { // The button is not pinned now, so it was pinned before it was clicked and the package was either installed or installing
                        pair.mtPackage.uninstall();

                        boolean uninstallOcr = pair.ocrPackage != null;
                        for (LanguagePair lp : languagePairs)
                            if (uninstallOcr && lp.mtPackage.isInstalled() && pair.ocrPackage.equals(lp.ocrPackage))
                                uninstallOcr = false;
                        if (uninstallOcr) pair.ocrPackage.uninstall();

                        notifyDataSetChanged();

                    } else if (checkInternetAccess(getResources().getString(R.string.offline_on_install_error_title), getResources().getString(R.string.offline_on_install_error_message))) {
                        PackageManager.installPackages(
                                pair.ocrPackage != null && !pair.ocrPackage.isInstalled() ?
                                        Arrays.asList(pair.mtPackage, pair.ocrPackage) :
                                        Collections.singletonList((Package)pair.mtPackage),
                                new Package.ProgressCallback() {
                                    @Override public void onProgress(int arg) {
                                        progress.setProgress(arg);
                                    }
                                },
                                new Package.InstallCallback() {
                                    @Override public void onInstall() {
                                        //progress.setPinned(true);
                                        //progress.setProgress(100);
                                        notifyDataSetChanged();
                                    }
                                },
                                new Package.ExceptionCallback() {
                                    @Override public void onException(Exception exception) {
                                        //progress.setPinned(false);
                                        //progress.setProgress(0);
                                        notifyDataSetChanged();
                                        exceptionCallback.onException(exception);
                                    }
                                });
                    }
                }
            });

            pairTxt.setText(languagePairs.get(position).name);
            progress.setPinned(languagePairs.get(position).mtPackage.isInstalled());
            progress.setProgress(languagePairs.get(position).mtPackage.isInstalled() ? 100 : 0);
            //if (languagePairs.get(position).mtPackage.isInstalled()) progress.setProgress(100);

            return convertView;
        }

    }

}
