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

package com.mitzuli.core.tts;

import java.util.Locale;

import android.content.Context;
import android.speech.tts.TextToSpeech;


public class Tts {

    private TextToSpeech tts;
    private boolean loaded = false;

    public interface OnInitListener {
        public void onInit();
    }

    public Tts(final Context context, final OnInitListener listener) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                loaded = true;
                if (status == TextToSpeech.SUCCESS) listener.onInit();
                else tts = null;
            }
        });
    }

    public boolean isLanguageAvailable(Locale language) {
        if (!loaded || tts == null) return false;

        final int res = tts.isLanguageAvailable(language);
        return res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    public boolean speak(String text, Locale language, boolean flush) {
        if (!isLanguageAvailable(language)) return false;

        tts.setLanguage(language);
        tts.speak(text, flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null);
        return true;
    }

    public void shutdown() {
        if (tts != null) tts.shutdown();
    }

}
