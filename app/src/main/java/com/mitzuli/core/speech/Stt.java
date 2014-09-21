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

package com.mitzuli.core.speech;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * A class to easily perform speech recognition. All of its methods must be invoked from the main application thread.
 */
public class Stt {

    public interface OnInitListener {
        public void onInit(boolean success);
    }

    public static interface RecognitionCallback {
        public void onRecognitionDone(String recognizedText);
    }

    public static interface ProgressCallback {
        public void onReadyForSpeech();
        public void onRmsChanged(float rmsdB);
        public void onPartialResults(String recognizedText);
    }

    public static interface ExceptionCallback {
        public void onException(Exception exception);
    }

    private SpeechRecognizer recognizer;
    private boolean loaded;

    private List<Locale> supportedLanguages;

    private RecognitionCallback recognitionCallback;
    private ExceptionCallback exceptionCallback;
    private ProgressCallback progressCallback;

    private final RecognitionListener recognitionListener = new RecognitionListener() {

        @Override
        public void onError(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Audio recording error."));
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Other client side errors."));
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Insufficient permissions."));
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Other network related errors."));
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Network operation timed out."));
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    if (recognitionCallback != null) recognitionCallback.onRecognitionDone("");
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("RecognitionService busy."));
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    if (exceptionCallback != null) exceptionCallback.onException(new Exception("Server sends error status."));
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    if (recognitionCallback != null) recognitionCallback.onRecognitionDone("");
                    break;
            }
            recognitionCallback = null;
            exceptionCallback = null;
        }

        @Override
        public void onResults(Bundle results) {
            if (recognitionCallback != null) recognitionCallback.onRecognitionDone(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0));
            recognitionCallback = null;
            exceptionCallback = null;
        }

        @Override
        public void onReadyForSpeech(Bundle bundle) {
            if (progressCallback != null) progressCallback.onReadyForSpeech();
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            if (progressCallback != null) progressCallback.onRmsChanged(rmsdB);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (progressCallback != null) progressCallback.onPartialResults(partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0));
        }

        // Callbacks that we don't use
        @Override public void onBeginningOfSpeech() {}
        @Override public void onEndOfSpeech() {}
        @Override public void onBufferReceived(byte[] bytes) {}
        @Override public void onEvent(int i, Bundle bundle) {}

    };


    public Stt(final Context context, final OnInitListener listener) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(recognitionListener);
            context.sendOrderedBroadcast(new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS), null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    supportedLanguages = new ArrayList<Locale>();
                    final Bundle results = getResultExtras(true);
                    if (results.containsKey(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)) {
                        for (String language : results.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)) {
                            final String tags[] = language.split("-");
                            if (tags.length == 1) supportedLanguages.add(new Locale(tags[0]));
                            else if (tags.length == 2) supportedLanguages.add(new Locale(tags[0], tags[1]));
                            else if (tags.length == 3) supportedLanguages.add(new Locale(tags[0], tags[1], tags[2]));
                        }
                        loaded = true;
                        listener.onInit(true);
                    } else {
                        listener.onInit(false);
                    }
                }
            }, null, Activity.RESULT_OK, null, null);
        } else {
            listener.onInit(false);
        }
    }

    public void recognize(Locale language, RecognitionCallback recognitionCallback, ExceptionCallback exceptionCallback) {
        recognize(language, recognitionCallback, exceptionCallback, null);
    }

    public void recognize(Locale language, RecognitionCallback recognitionCallback, ExceptionCallback exceptionCallback, ProgressCallback progressCallback) {
        if (!loaded) {
            exceptionCallback.onException(new IllegalStateException("Speech recognizer not initialized yet."));
        } else if (!isLanguageAvailable(language)) {
            exceptionCallback.onException(new IllegalStateException("Speech recognizer not available for this language."));
        } else if (this.recognitionCallback != null || this.exceptionCallback != null) {
            exceptionCallback.onException(new IllegalStateException("There is an ongoing recognition."));
        } else {
            this.recognitionCallback = recognitionCallback;
            this.exceptionCallback = exceptionCallback;
            this.progressCallback = progressCallback;
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, compatibleLanguageCode(language));
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{}); // TODO This is undocumented but necessary. See https://productforums.google.com/forum/#!topic/websearch/PUjEPmdSzSE/discussion
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, progressCallback != null);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
        }
    }

    public boolean isLanguageAvailable(Locale language) {
        return loaded && compatibleLanguageCode(language) != null;
    }

    private String compatibleLanguageCode(Locale language) {
        final ArrayList<Locale> compatibleLanguages = new ArrayList<Locale>();
        for (Locale supportedLanguage : supportedLanguages) {
            try {
                if (supportedLanguage.getISO3Language().equals(language.getISO3Language())) {
                    compatibleLanguages.add(supportedLanguage);
                }
            } catch (MissingResourceException e) {} // No 3-letter language code for locale (as in cmn-Hans-CN), which can be ignored in our case
        }
        if (!compatibleLanguages.isEmpty() && language.getCountry().equals("")) return compatibleLanguages.get(0).getLanguage(); // Not a country specific locale
        for (Locale compatibleLanguage : compatibleLanguages) {
            try {
                if (compatibleLanguage.getISO3Country().equals(language.getISO3Country())) {
                    return compatibleLanguage.getLanguage() + "-" + compatibleLanguage.getCountry().toUpperCase();
                }
            } catch (MissingResourceException e) {} // No 3-letter country code for locale (as in cmn-Hans-CN), which can be ignored in our case
        }
        return compatibleLanguages.isEmpty() ? null : compatibleLanguages.get(0).getLanguage();
    }

    public void stopRecognition() {
        recognizer.stopListening();
    }

    public void cancelRecognition() {
        recognitionCallback = null;
        exceptionCallback = null;
        recognizer.stopListening();
    }

    public void shutdown() {
        if (recognizer != null) recognizer.destroy();
    }

}
