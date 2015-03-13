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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;


public class Tts { // TODO Review concurrency

    private List<TextToSpeech> ttsList = new ArrayList<TextToSpeech>();
    private volatile int totalEngines = -1, loadedEngines = 0;

    public interface OnInitListener {
        public void onInit(boolean success);
    }

    public Tts(final Context context, final OnInitListener listener) {
        class TtsOnInitListener implements TextToSpeech.OnInitListener {
            private TextToSpeech tts; // TODO The current code assumes that this will only be null if TTS initialization fails
            @Override public void onInit(int status) {
                synchronized (Tts.this) {
                    if (status == TextToSpeech.SUCCESS && tts != null) ttsList.add(tts);
                    if (loadedEngines == 0) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH || tts == null) {
                            totalEngines = 1;
                        } else {
                            totalEngines = tts.getEngines().size();
                            for (TextToSpeech.EngineInfo engine : tts.getEngines()) {
                                if (!engine.name.equals(tts.getDefaultEngine())) {
                                    final TtsOnInitListener ttsOnInitListener = new TtsOnInitListener();
                                    ttsOnInitListener.tts = new TextToSpeech(context, ttsOnInitListener, engine.name);
                                }
                            }
                        }
                    }
                    loadedEngines++;
                    if (loadedEngines == totalEngines) listener.onInit(!ttsList.isEmpty());
                }
            }
        };
        final TtsOnInitListener ttsOnInitListener = new TtsOnInitListener();
        ttsOnInitListener.tts = new TextToSpeech(context, ttsOnInitListener);
    }

    public boolean isLanguageAvailable(Locale language) {
        if (loadedEngines != totalEngines) return false;
        for (TextToSpeech tts : ttsList) {
            final int available = tts.isLanguageAvailable(language);
            if (available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED) return true;
        }
        return false;
    }

    public boolean speak(String text, Locale language) {
        if (loadedEngines != totalEngines) return false;
        for (TextToSpeech tts : ttsList) tts.stop();
        for (TextToSpeech tts : ttsList) {
            final int available = tts.isLanguageAvailable(language);
            if (available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(language);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                return true;
            }
        }
        return false;
    }

    public void shutdown() {
        for (TextToSpeech tts : ttsList) tts.shutdown();
    }

}
