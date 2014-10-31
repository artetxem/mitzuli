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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.mitzuli.core.Package;
import com.mitzuli.core.PackageManager;
import com.mitzuli.core.mt.MtPackage;
import com.mitzuli.core.ocr.OcrPackage;
import com.mitzuli.core.speech.Stt;
import com.mitzuli.core.speech.Tts;

import org.acra.ACRA;
import org.opencv.android.OpenCVLoader;

import com.f2prateek.progressbutton.ProgressButton;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity implements OnClickListener, ActionBar.OnNavigationListener {

    private static final boolean OPENCV_AVAILABLE = OpenCVLoader.initDebug();

    private static final String STATE_SRC_CARD_VISIBILITY = "SRC_CARD_VISIBILITY";
    private static final String STATE_TRG_CARD_VISIBILITY = "TRG_CARD_VISIBILITY";
    private static final String STATE_SRC_TEXT = "SRC_TEXT";
    private static final String STATE_TRG_TEXT = "TRG_TEXT";
    private static final String PREFS_LAST_PAIR = "LAST_PAIR";
    private static final int SETTINGS_ACTIVITY_ID = 1;


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

    private SharedPreferences preferences;

    private LinearLayout mainPanel;

    private LinearLayout srcCard;
    private LinearLayout srcHeader;
    private ImageView srcExpandState;
    private TextView srcTitle;
    private View srcButtonSeparator;
    private ProgressBar srcLoadingButtonsIndicator;
    private LinearLayout srcContent;

    private EditText srcText;
    private SttView srcMic;
    private CameraCropperView srcCamera;
    private ProgressBar srcProgressBar;

    private Button translateButton;

    private LinearLayout trgCard;
    private LinearLayout trgHeader;
    private ImageView trgExpandState;
    private TextView trgTitle;
    private View trgButtonSeparator;
    private ProgressBar trgLoadingButtonsIndicator;
    private LinearLayout trgContent;

    private ScrollView trgTextScroll;
    private TextView trgText;
    private ProgressBar trgProgressBar;

    private ImageButton srcAudioButton, micButton, cameraButton, keyboardButton, removeButton, trgAudioButton, shareButton, copyButton;
    private Drawable srcAudioButtonEnabledDrawable, micButtonEnabledDrawable, cameraButtonEnabledDrawable, keyboardButtonEnabledDrawable, removeButtonEnabledDrawable, trgAudioButtonEnabledDrawable, shareButtonEnabledDrawable, copyButtonEnabledDrawable;
    private Drawable srcAudioButtonDisabledDrawable, micButtonDisabledDrawable, cameraButtonDisabledDrawable, keyboardButtonDisabledDrawable, removeButtonDisabledDrawable, trgAudioButtonDisabledDrawable, shareButtonDisabledDrawable, copyButtonDisabledDrawable;

    private CameraCropperView.OpenCameraCallback openCameraCallback = new CameraCropperView.OpenCameraCallback() {
        @Override public void onCameraOpened(boolean success) {
            if (success) srcCamera.updateDisplayOrientation(getApplicationContext());
            cameraLoaded = true;
            cameraAvailable = success;
            updateSrcButtons();
        }
    };

    private CameraCropperView.CroppedPictureCallback croppedPictureCallback = new CameraCropperView.CroppedPictureCallback() {
        @Override public void onPictureCropped(Image croppedPicture) {
            setSrcContent(srcProgressBar);
            OcrPackage.DEBUG = preferences.getBoolean("pref_key_ocr_debugging", false); // TODO Temporary workaround to allow to manually enable debugging
            activePair.ocrPackage.recognizeText(croppedPicture, ocrCallback, exceptionCallback);
        }
    };

    private OcrPackage.OcrCallback ocrCallback = new OcrPackage.OcrCallback() {
        @Override public void onTextRecognized(String text) {
            srcText.setText(text);
            setSrcContent(srcText);
            activePair.mtPackage.translate(
                    srcText.getText().toString(),
                    preferences.getBoolean("pref_key_mark_unknown", true),
                    translationCallback,
                    exceptionCallback);
        }
    };

    private MtPackage.TranslationCallback translationCallback = new MtPackage.TranslationCallback() {
        @Override public void onTranslationDone(String translation) {
            trgText.setText(Html.fromHtml(translation));
            setTrgContent(trgTextScroll);
            if (ttsRequest && tts.isLanguageAvailable(activePair.mtPackage.getTargetLanguage())) {
                tts.speak(trgText.getText().toString(), activePair.mtPackage.getTargetLanguage(), true);
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
                        preferences.getBoolean("pref_key_mark_unknown", true),
                        translationCallback,
                        exceptionCallback);
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
            srcMic.setRms(srcMic.MIN_RMS_DB);
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

        srcCard = (LinearLayout)findViewById(R.id.src_card);
        srcHeader = (LinearLayout)findViewById(R.id.src_header);
        srcHeader.setOnClickListener(this);
        srcExpandState = (ImageView)findViewById(R.id.src_expand_state);
        srcTitle = (TextView)findViewById(R.id.src_title);
        srcButtonSeparator = findViewById(R.id.src_button_separator);
        srcLoadingButtonsIndicator = (ProgressBar)findViewById(R.id.src_loading_buttons_indicator);
        srcContent = (LinearLayout)findViewById(R.id.src_content);

        translateButton = (Button)findViewById(R.id.translate_button);
        translateButton.setOnClickListener(this);

        trgCard = (LinearLayout)findViewById(R.id.trg_card);
        trgHeader = (LinearLayout)findViewById(R.id.trg_header);
        trgHeader.setOnClickListener(this);
        trgExpandState = (ImageView)findViewById(R.id.trg_expand_state);
        trgTitle = (TextView)findViewById(R.id.trg_title);
        trgButtonSeparator = findViewById(R.id.trg_button_separator);
        trgLoadingButtonsIndicator = (ProgressBar)findViewById(R.id.trg_loading_buttons_indicator);
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

        srcAudioButton = (ImageButton)findViewById(R.id.src_audio_button);
        srcAudioButton.setOnClickListener(this);
        srcAudioButtonEnabledDrawable = srcAudioButton.getDrawable();
        srcAudioButtonDisabledDrawable = convertDrawableToGrayscale(srcAudioButtonEnabledDrawable);
        micButton = (ImageButton)findViewById(R.id.mic_button);
        micButton.setOnClickListener(this);
        micButtonEnabledDrawable = micButton.getDrawable();
        micButtonDisabledDrawable = convertDrawableToGrayscale(micButtonEnabledDrawable);
        cameraButton = (ImageButton)findViewById(R.id.camera_button);
        cameraButton.setOnClickListener(this);
        cameraButtonEnabledDrawable = cameraButton.getDrawable();
        cameraButtonDisabledDrawable = convertDrawableToGrayscale(cameraButtonEnabledDrawable);
        keyboardButton = (ImageButton)findViewById(R.id.keyboard_button);
        keyboardButton.setOnClickListener(this);
        keyboardButtonEnabledDrawable = keyboardButton.getDrawable();
        keyboardButtonDisabledDrawable = convertDrawableToGrayscale(keyboardButtonEnabledDrawable);
        removeButton = (ImageButton)findViewById(R.id.remove_button);
        removeButton.setOnClickListener(this);
        removeButtonEnabledDrawable = removeButton.getDrawable();
        removeButtonDisabledDrawable = convertDrawableToGrayscale(removeButtonEnabledDrawable);
        trgAudioButton = (ImageButton)findViewById(R.id.trg_audio_button);
        trgAudioButton.setOnClickListener(this);
        trgAudioButtonEnabledDrawable = trgAudioButton.getDrawable();
        trgAudioButtonDisabledDrawable = convertDrawableToGrayscale(trgAudioButtonEnabledDrawable);
        copyButton = (ImageButton)findViewById(R.id.copy_button);
        copyButton.setOnClickListener(this);
        copyButtonEnabledDrawable = copyButton.getDrawable();
        copyButtonDisabledDrawable = convertDrawableToGrayscale(copyButtonEnabledDrawable);
        shareButton = (ImageButton)findViewById(R.id.share_button);
        shareButton.setOnClickListener(this);
        shareButtonEnabledDrawable = shareButton.getDrawable();
        shareButtonDisabledDrawable = convertDrawableToGrayscale(shareButtonEnabledDrawable);

        mainPanel.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            private int trgCardPreEditingVisibility;
            @Override public void onGlobalLayout() {
                final int heightDiff = mainPanel.getRootView().getHeight() - mainPanel.getHeight();
                if (heightDiff > 200 && !editing) { // if more than 200 pixels, its probably a keyboard...
                    editing = true;
                    trgCardPreEditingVisibility = trgCard.getVisibility();
                    trgCard.setVisibility(View.GONE);
                    srcExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_right));
                    updateSrcButtons();
                } else if (heightDiff < 200 && editing) {
                    editing = false;
                    trgCard.setVisibility(trgCardPreEditingVisibility);
                    srcExpandState.setImageDrawable(getResources().getDrawable(trgCardPreEditingVisibility == View.VISIBLE ? R.drawable.expander_down : R.drawable.expander_up));
                    srcText.clearFocus();
                    updateSrcButtons();
                }
            }
        });

        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        try {
            PackageManagers.init(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        tts = new Tts(getApplicationContext(), new Tts.OnInitListener() {
            @Override public void onInit(boolean success) {
                ttsLoaded = true;
                ttsAvailable = success;
                if (activePair != null) {
                    updateSrcButtons();
                    updateTrgButtons();
                }
            }
        });

        stt = new Stt(getApplicationContext(), new Stt.OnInitListener() {
            @Override public void onInit(boolean success) {
                sttLoaded = true;
                sttAvailable = success;
                if (activePair != null) {
                    updateSrcButtons();
                }
            }
        });

        refreshLanguagePairs();

        if (preferences.getBoolean("pref_key_autocheck_updates", true)) {
            PackageManagers.updatePackages(this, false, false, new PackageManagers.ManifestsUpdateCallback() {
                @Override public void onManifestsUpdate() {
                    refreshLanguagePairs();
                }
            });
        }

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
            srcExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_up));
            trgCard.setVisibility(View.GONE);
        } else if (srcCardVisibility == View.GONE && trgCardVisibility == View.VISIBLE) {
            srcCard.setVisibility(View.GONE);
            trgExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_down));
        }
        srcText.setText(Html.fromHtml(savedInstanceState.getString(STATE_SRC_TEXT)));
        trgText.setText(Html.fromHtml(savedInstanceState.getString(STATE_TRG_TEXT)));
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SETTINGS_ACTIVITY_ID) {
            refreshLanguagePairs();
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
            updateSrcButtons();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
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
    public boolean onNavigationItemSelected(int position, long id) {
        setActivePair(languagePairs.get(position));
        return true;
    }

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
                        preferences.getBoolean("pref_key_mark_unknown", true),
                        translationCallback,
                        exceptionCallback);
            }
        } else if (view == srcHeader && !editing) {
            if (trgCard.getVisibility() == View.GONE) {
                trgCard.setVisibility(View.VISIBLE);
                srcExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_down));
                trgExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_up));
            } else {
                trgCard.setVisibility(View.GONE);
                srcExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_up));
            }
        } else if (view == trgHeader) {
            if (srcCard.getVisibility() == View.GONE) {
                srcCard.setVisibility(View.VISIBLE);
                srcExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_down));
                trgExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_up));
            } else {
                srcCard.setVisibility(View.GONE);
                trgExpandState.setImageDrawable(getResources().getDrawable(R.drawable.expander_down));
            }
        } else if (view == srcAudioButton) {
            if (!tts.isLanguageAvailable(activePair.mtPackage.getSourceLanguage())) {
                Toast.makeText(getApplicationContext(), isOnline() ? R.string.toast_unavailable_tts : R.string.toast_unavailable_tts_offline, Toast.LENGTH_SHORT).show();
            } else {
                tts.speak(srcText.getText().toString(), activePair.mtPackage.getSourceLanguage(), true);
            }
        } else if (view == micButton) {
            if (!isOnline()) {
                Toast.makeText(getApplicationContext(), R.string.toast_unavailable_stt_offline, Toast.LENGTH_SHORT).show();
            } else if (!stt.isLanguageAvailable(activePair.mtPackage.getSourceLanguage())) {
                Toast.makeText(getApplicationContext(), R.string.toast_unavailable_stt, Toast.LENGTH_SHORT).show();
            } else {
                if (editing) hideSoftKeyboard();
                setSrcContent(srcProgressBar);
                stt.recognize(activePair.mtPackage.getSourceLanguage(), sttRecognitionCallback, sttExceptionCallback, sttProgressCallback);
            }
        } else if (view == cameraButton) {
            if (activePair.ocrPackage == null) {
                Toast.makeText(getApplicationContext(), R.string.toast_unavailable_ocr, Toast.LENGTH_SHORT).show();
            } else {
                if (editing) hideSoftKeyboard();
                setSrcContent(srcCamera);
            }
        } else if (view == keyboardButton) {
            if (srcContent.getChildAt(0) == srcMic) stt.cancelRecognition();
            setSrcContent(srcText);
        } else if (view == removeButton) {
            srcText.setText("");
        } else if (view == trgAudioButton) {
            if (!tts.isLanguageAvailable(activePair.mtPackage.getTargetLanguage())) {
                Toast.makeText(getApplicationContext(), isOnline() ? R.string.toast_unavailable_tts : R.string.toast_unavailable_tts_offline, Toast.LENGTH_SHORT).show();
            } else {
                tts.speak(trgText.getText().toString(), activePair.mtPackage.getTargetLanguage(), true);
            }
        } else if (view == copyButton) { //TODO Should we use conditional code depending on the Android version?
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(trgText.getText());
            Toast.makeText(getApplicationContext(), R.string.translation_copied, Toast.LENGTH_SHORT).show();
        } else if (view == shareButton) {
            final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, trgText.getText().toString());
            startActivity(Intent.createChooser(intent, getResources().getText(R.string.share_with)));
        }
    }


    private void hideSoftKeyboard() {
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(srcText.getApplicationWindowToken(), 0);
    }


    private void updateSrcButtons() {
        final View view = srcContent.getChildAt(0);
        final Locale language = activePair.mtPackage.getSourceLanguage();
        final boolean isLoaded = ttsLoaded && sttLoaded && cameraLoaded;
        final boolean isAudioVisible = isLoaded && ttsAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isAudioAvailable = language != null && tts.isLanguageAvailable(language);
        final boolean isAudioEnabled = view == srcText;
        final boolean isMicVisible = isLoaded && sttAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isMicAvailable = language != null && stt.isLanguageAvailable(language) && isOnline();
        final boolean isMicEnabled = view == srcText;
        final boolean isCameraVisible = isLoaded && cameraAvailable && (view == srcText || view == srcProgressBar) && !editing;
        final boolean isCameraAvailable = activePair.ocrPackage != null;
        final boolean isCameraEnabled = view == srcText;
        final boolean isKeyboardVisible = view == srcCamera || view == srcMic;
        final boolean isKeyboardAvailable = true;
        final boolean isKeyboardEnabled = true;
        final boolean isRemoveVisible = view == srcText && editing;
        final boolean isRemoveAvailable = true;
        final boolean isRemoveEnabled = true;
        final boolean isLoadingVisible = !isLoaded && !isKeyboardVisible && !isRemoveVisible;
        srcAudioButton.setVisibility(isAudioVisible ? View.VISIBLE : View.GONE);
        srcAudioButton.setImageDrawable(isAudioAvailable && isAudioEnabled ? srcAudioButtonEnabledDrawable : srcAudioButtonDisabledDrawable);
        srcAudioButton.setEnabled(isAudioEnabled);
        micButton.setVisibility(isMicVisible ? View.VISIBLE : View.GONE);
        micButton.setImageDrawable(isMicAvailable && isMicEnabled ? micButtonEnabledDrawable : micButtonDisabledDrawable);
        micButton.setEnabled(isMicEnabled);
        cameraButton.setVisibility(isCameraVisible ? View.VISIBLE : View.GONE);
        cameraButton.setImageDrawable(isCameraAvailable && isCameraEnabled ? cameraButtonEnabledDrawable : cameraButtonDisabledDrawable);
        cameraButton.setEnabled(isCameraEnabled);
        keyboardButton.setVisibility(isKeyboardVisible ? View.VISIBLE : View.GONE);
        keyboardButton.setImageDrawable(isKeyboardAvailable && isKeyboardEnabled ? keyboardButtonEnabledDrawable : keyboardButtonDisabledDrawable);
        keyboardButton.setEnabled(isKeyboardEnabled);
        removeButton.setVisibility(isRemoveVisible ? View.VISIBLE : View.GONE);
        removeButton.setImageDrawable(isRemoveAvailable && isRemoveEnabled ? removeButtonEnabledDrawable : removeButtonDisabledDrawable);
        removeButton.setEnabled(isRemoveEnabled);
        srcLoadingButtonsIndicator.setVisibility(isLoadingVisible ? View.VISIBLE : View.GONE);
        srcButtonSeparator.setVisibility(isAudioVisible || isMicVisible || isCameraVisible || isKeyboardVisible || isRemoveVisible || isLoadingVisible ? View.VISIBLE : View.GONE);
    }


    private void updateTrgButtons() {
        final View view = trgContent.getChildAt(0);
        final Locale language = activePair.mtPackage.getTargetLanguage();
        final boolean isLoaded = ttsLoaded;
        final boolean isAudioVisible = isLoaded && ttsAvailable && (view == trgTextScroll || view == trgProgressBar);
        final boolean isAudioAvailable = language != null && tts.isLanguageAvailable(language);
        final boolean isAudioEnabled = view == trgTextScroll;
        final boolean isShareVisible = isLoaded && (view == trgTextScroll || view == trgProgressBar);
        final boolean isShareAvailable = true;
        final boolean isShareEnabled = view == trgTextScroll;
        final boolean isCopyVisible = isLoaded && (view == trgTextScroll || view == trgProgressBar);
        final boolean isCopyAvailable = true;
        final boolean isCopyEnabled = view == trgTextScroll;
        final boolean isLoadingVisible = !isLoaded;
        trgAudioButton.setVisibility(isAudioVisible ? View.VISIBLE : View.GONE);
        trgAudioButton.setImageDrawable(isAudioAvailable && isAudioEnabled ? trgAudioButtonEnabledDrawable : trgAudioButtonDisabledDrawable);
        trgAudioButton.setEnabled(isAudioEnabled);
        shareButton.setVisibility(isShareVisible ? View.VISIBLE : View.GONE);
        shareButton.setImageDrawable(isShareAvailable && isShareEnabled ? shareButtonEnabledDrawable : shareButtonDisabledDrawable);
        shareButton.setEnabled(isShareEnabled);
        copyButton.setVisibility(isCopyVisible ? View.VISIBLE : View.GONE);
        copyButton.setImageDrawable(isCopyAvailable && isCopyEnabled ? copyButtonEnabledDrawable : copyButtonDisabledDrawable);
        copyButton.setEnabled(isCopyEnabled);
        trgLoadingButtonsIndicator.setVisibility(isLoadingVisible ? View.VISIBLE : View.GONE);
        trgButtonSeparator.setVisibility(isAudioVisible || isShareVisible || isCopyVisible || isLoadingVisible ? View.VISIBLE : View.GONE);
    }


    private void setSrcContent(View view) {
        if (view != srcContent.getChildAt(0)) {
            srcContent.removeAllViews();
            srcContent.setGravity(view == srcText ? Gravity.TOP : Gravity.CENTER);
            srcContent.addView(view);
        }
        translateButton.setEnabled(view != srcProgressBar && trgContent.getChildAt(0) != trgProgressBar);
        updateSrcButtons();
    }


    private void setTrgContent(View view) {
        if (view != trgContent.getChildAt(0)) {
            trgContent.removeAllViews();
            trgContent.setGravity(view == trgTextScroll ? Gravity.TOP : Gravity.CENTER);
            trgContent.addView(view);
        }
        translateButton.setEnabled(srcContent.getChildAt(0) != srcProgressBar && view != trgProgressBar);
        updateTrgButtons();
    }


    private Drawable convertDrawableToGrayscale(Drawable drawable) {
        final Drawable result = drawable.getConstantState().newDrawable().mutate();
        result.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
        return result;
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
            srcTitle.setText(PackageManagers.getName(pair.mtPackage.getSourceLanguage()));
            trgTitle.setText(PackageManagers.getName(pair.mtPackage.getTargetLanguage()));
            preferences.edit().putString(PREFS_LAST_PAIR, pair.mtPackage.getId()).commit();
            for (int i = 0; i < languagePairs.size(); i++) {
                if (pair.mtPackage.getId().equals(languagePairs.get(i).mtPackage.getId())) {
                    getSupportActionBar().setSelectedNavigationItem(i);
                    break;
                }
            }
            setSrcContent(srcText);
            setTrgContent(trgTextScroll);
        }
    }


    private void refreshLanguagePairs() {
        languagePairs = new ArrayList<LanguagePair>();
        for (MtPackage translatorPackage : PackageManagers.onlineMtPackageManager.getAllPackages()) {
            languagePairs.add(new LanguagePair(
                    PackageManagers.getName(translatorPackage.getSourceLanguage()) + " → " + PackageManagers.getName(translatorPackage.getTargetLanguage()),
                    translatorPackage,
                    PackageManagers.ocrPackageManager.getPackageForLanguage(translatorPackage.getSourceLanguage())
            ));
        }
        for (MtPackage translatorPackage : PackageManagers.releasedMtPackageManager.getAllPackages()) {
            languagePairs.add(new LanguagePair(
                    PackageManagers.getName(translatorPackage.getSourceLanguage()) + " → " + PackageManagers.getName(translatorPackage.getTargetLanguage()),
                    translatorPackage,
                    PackageManagers.ocrPackageManager.getPackageForLanguage(translatorPackage.getSourceLanguage())));
        }
        if (preferences.getBoolean("pref_key_beta_packages", false)) {
            for (MtPackage translatorPackage : PackageManagers.betaMtPackageManager.getAllPackages()) {
                languagePairs.add(new LanguagePair(
                        "[BETA] " + PackageManagers.getName(translatorPackage.getSourceLanguage()) + " → " + PackageManagers.getName(translatorPackage.getTargetLanguage()),
                        translatorPackage,
                        PackageManagers.ocrPackageManager.getPackageForLanguage(translatorPackage.getSourceLanguage())));
            }
        }
        Collections.sort(languagePairs);
        getSupportActionBar().setListNavigationCallbacks(new LanguagePairAdapter(MainActivity.this), this);

        final String lastPairId = preferences.getString(PREFS_LAST_PAIR, null);
        LanguagePair lastPair = null;
        for (LanguagePair pair : languagePairs) if (pair.mtPackage.getId().equals(lastPairId)) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null && pair.mtPackage.isInstalled()) lastPair = pair;
        for (LanguagePair pair : languagePairs) if (lastPair == null) lastPair = pair;
        setActivePair(lastPair);
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
            super(context, R.layout.ab_spinner_main_view, languagePairs);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.ab_spinner_main_view, parent, false);
            ((TextView)convertView).setText(languagePairs.get(position).name);
            return convertView;
        }

        @Override
        public View getDropDownView(final int position, View convertView, ViewGroup parent) {
            final LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = vi.inflate(R.layout.ab_spinner_dropdown_view, parent, false);

            final TextView pairTxt = (TextView) convertView.findViewById(R.id.pairTxt);
            final ProgressButton progress = (ProgressButton) convertView.findViewById(R.id.progressBtn);

            if (!languagePairs.get(position).mtPackage.isInstallable()) progress.setVisibility(View.INVISIBLE);

            progress.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    final LanguagePair pair = languagePairs.get(position);
                    if (pair.mtPackage.isInstalled()) {
                        pair.mtPackage.uninstall();

                        boolean uninstallOcr = pair.ocrPackage != null;
                        for (LanguagePair lp : languagePairs)
                            if (uninstallOcr && lp.mtPackage.isInstalled() && pair.ocrPackage.getId().equals(lp.ocrPackage.getId()))
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
