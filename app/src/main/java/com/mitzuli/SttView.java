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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SttView extends RelativeLayout {

    public static final double MIN_RMS_DB = -2;
    public static final double MAX_RMS_DB = 10;

    private final TextView recognizedText;
    private final TextView statusText;
    private final View microphoneForeground;
    private final int microphoneHeight;

    public SttView(Context context) {
        super(context);
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View v = inflater.inflate(R.layout.stt_view, this, true);
        recognizedText = (TextView) v.findViewById(R.id.recognized_text);
        statusText = (TextView) v.findViewById(R.id.status_text);
        microphoneForeground = v.findViewById(R.id.microphone_foreground);
        microphoneHeight = microphoneForeground.getLayoutParams().height;
    }

    public void setRecognizedText(CharSequence text) {
        recognizedText.setText(text);
    }

    public void setStatusText(CharSequence text) {
        statusText.setText(text);
    }

    public void setRms(double rmsdB) {
        if (rmsdB < MIN_RMS_DB) rmsdB = MIN_RMS_DB;
        if (rmsdB > MAX_RMS_DB) rmsdB = MAX_RMS_DB;
        microphoneForeground.getLayoutParams().height = (int)(microphoneHeight*(rmsdB-MIN_RMS_DB)/(MAX_RMS_DB-MIN_RMS_DB));
        microphoneForeground.requestLayout(); // TODO Is this the right call???
    }
}
