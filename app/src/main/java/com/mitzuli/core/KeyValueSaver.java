/*
 * Copyright (C) 2015 Mikel Artetxe <artetxem@gmail.com>
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

package com.mitzuli.core;

import android.content.SharedPreferences;

import java.util.regex.Pattern;


public class KeyValueSaver {

    private static final String TYPE_BOOLEAN = "b:";
    private static final String TYPE_INT = "i:";
    private static final String TYPE_LONG = "l:";
    private static final String TYPE_FLOAT = "f:";
    private static final String TYPE_STRING = "s:";

    private final SharedPreferences prefs;
    private final String prefix;
    private final Pattern keyPattern;


    public KeyValueSaver(SharedPreferences prefs) {
        this(prefs, "");
    }

    public KeyValueSaver(SharedPreferences prefs, String prefix) {
        checkKey(prefix);
        this.prefs = prefs;
        this.prefix = prefix + ":";
        this.keyPattern = Pattern.compile(Pattern.quote(prefix) + "[^:]+:[^:]*");
    }

    public KeyValueSaver(KeyValueSaver saver, String prefix) {
        checkPrefix(prefix);
        this.prefs = saver.prefs;
        this.prefix = saver.prefix + prefix;
        this.keyPattern = Pattern.compile(Pattern.quote(prefix) + "[^:]+:[^:]*");
    }

    public boolean containsBoolean(String key) {
        return getBoolean(key) != null;
    }

    public Boolean getBoolean(String key) {
        return getBoolean(key, null);
    }

    public Boolean getBoolean(String key, Boolean defValue) {
        checkKey(key);
        return prefs.contains(prefix + TYPE_BOOLEAN + key) ? prefs.getBoolean(prefix + TYPE_BOOLEAN + key, false) : defValue;
    }

    public void saveBoolean(String key, boolean value) {
        checkKey(key);
        prefs.edit().putBoolean(prefix + TYPE_BOOLEAN + key, value).commit();
    }

    public void removeBoolean(String key) {
        checkKey(key);
        prefs.edit().remove(prefix + TYPE_BOOLEAN + key).commit();
    }

    public boolean containsInt(String key) {
        return getInt(key) != null;
    }

    public Integer getInt(String key) {
        return getInt(key, null);
    }

    public Integer getInt(String key, Integer defValue) {
        checkKey(key);
        return prefs.contains(prefix + TYPE_INT + key) ? prefs.getInt(prefix + TYPE_INT + key, 0) : defValue;
    }

    public void saveInt(String key, int value) {
        checkKey(key);
        prefs.edit().putInt(prefix + TYPE_INT + key, value).commit();
    }

    public void removeInt(String key) {
        checkKey(key);
        prefs.edit().remove(prefix + TYPE_INT + key).commit();
    }

    public boolean containsLong(String key) {
        return getLong(key) != null;
    }

    public Long getLong(String key) {
        return getLong(key, null);
    }

    public Long getLong(String key, Long defValue) {
        checkKey(key);
        return prefs.contains(prefix + TYPE_LONG + key) ? prefs.getLong(prefix + TYPE_LONG + key, 0) : defValue;
    }

    public void saveLong(String key, long value) {
        checkKey(key);
        prefs.edit().putLong(prefix + TYPE_LONG + key, value).commit();
    }

    public void removeLong(String key) {
        checkKey(key);
        prefs.edit().remove(prefix + TYPE_LONG + key).commit();
    }

    public boolean containsFloat(String key) {
        return getFloat(key) != null;
    }

    public Float getFloat(String key) {
        return getFloat(key, null);
    }

    public Float getFloat(String key, Float defValue) {
        checkKey(key);
        return prefs.contains(prefix + TYPE_FLOAT + key) ? prefs.getFloat(prefix + TYPE_FLOAT + key, 0) : defValue;
    }

    public void saveFloat(String key, float value) {
        checkKey(key);
        prefs.edit().putFloat(prefix + TYPE_FLOAT + key, value).commit();
    }

    public void removeFloat(String key) {
        checkKey(key);
        prefs.edit().remove(prefix + TYPE_FLOAT + key).commit();
    }

    public boolean containsString(String key) {
        return getString(key) != null;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defValue) {
        checkKey(key);
        return prefs.getString(prefix + TYPE_STRING + key, defValue);
    }

    public void saveString(String key, String value) {
        checkKey(key);
        prefs.edit().putString(prefix + TYPE_STRING + key, value).commit();
    }

    public void removeString(String key) {
        checkKey(key);
        prefs.edit().remove(prefix + TYPE_STRING + key).commit();
    }

    public void removeAll() {
        final SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) if (keyPattern.matcher(key).matches()) editor.remove(key);
        editor.commit();
    }


    private void checkPrefix(String prefix) {
        if (prefix.contains(":")) throw new IllegalArgumentException("Illegal prefix: " + prefix);
    }

    private void checkKey(String key) {
        if (key.contains(":")) throw new IllegalArgumentException("Illegal key: " + key);
    }

}
