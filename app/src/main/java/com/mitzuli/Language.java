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

package com.mitzuli;

import android.content.Context;

import java.util.Locale;
import java.util.MissingResourceException;


public class Language {

    private final Locale locale;

    public Language(String language) {
        this(new Locale(language));
    }

    public Language(String language, String country) {
        this(new Locale(language, country));
    }

    public Language(String language, String country, String variant) {
        this(new Locale(language, country, variant));
    }

    private Language(Locale locale) {
        if (locale.getLanguage().equals("")) throw new IllegalArgumentException("Missing language code");
        this.locale = locale;
    }

    /**
     * Returns the code for this language. The three-letter ISO 639-2/T code is used if it exist,
     * and the code that was specified when creating this language is otherwise returned.
     *
     * @return the code for this language.
     */
    public String getLanguage() {
        String language = getISO3Language();
        if (language == null) language = locale.getLanguage().toLowerCase();
        if (language.equals("")) language = null;
        return language;
    }

    /**
     * Returns the three-letter ISO 639-2/T code for this language.
     *
     * @return the three-letter ISO 639-2/T code for this language, or <code>null</code> if it does not exist.
     */
    public String getISO3Language() {
        try {
            final String s = locale.getISO3Language().toLowerCase();
            return s.equals("") ? null : s;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Returns the code for the country associated with this language. The three-letter ISO 3166
     * code is used if it exist, and the code that was specified when creating this language is
     * otherwise returned.
     *
     * @return the code for the country associated with this language, or <code>null</code> if the language has no associated country.
     */
    public String getCountry() {
        String country = getISO3Country();
        if (country == null) country = locale.getCountry().toLowerCase();
        if (country.equals("")) country = null;
        return country;
    }

    /**
     * Returns the three-letter ISO 3166 code for the country associated with this language.
     *
     * @return the three-letter ISO 3166 code for the country associated with this language, or <code>null</code> if it does not exist.
     */
    public String getISO3Country() {
        try {
            final String s = locale.getISO3Country().toLowerCase();
            return s.equals("") ? null : s;
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Returns the code for the variant associated with this language.
     *
     * @return the code for the variant associated with this language, or <code>null</code> if the language has no associated variant.
     */
    public String getVariant() {
        String variant = locale.getVariant().toLowerCase();
        if (variant.equals("")) variant = null;
        return variant;
    }

    public String getDisplayName(Context context) {
        final String language = getDisplayLanguage(context);
        final String country = getDisplayCountry(context);
        final String variant = getDisplayVariant(context);
        final StringBuilder name = new StringBuilder();
        name.append(language);
        if (country != null) {
            name.append(" (").append(country);
            if (variant != null) name.append(", ").append(variant);
            name.append(")");
        }
        return name.toString();
    }

    public String getDisplayLanguage(Context context) {
        if (getLanguage() == null) return null;
        final int id = context.getResources().getIdentifier("language_name_" + getLanguage(), "string", BuildConfig.APPLICATION_ID);
        final String s = id == 0 ? toTitleCase(locale.getDisplayLanguage()) : context.getResources().getString(id);
        return s.equals("") ? toTitleCase(getLanguage()) : s;
    }

    public String getDisplayCountry(Context context) {
        if (getCountry() == null) return null;
        final int id = context.getResources().getIdentifier("country_name_" + getCountry(), "string", BuildConfig.APPLICATION_ID);
        final String s = id == 0 ? toTitleCase(locale.getDisplayCountry()) : context.getResources().getString(id);
        return s.equals("") ? toTitleCase(getCountry()) : s;
    }

    public String getDisplayVariant(Context context) {
        if (getVariant() == null) return null;
        final int id = context.getResources().getIdentifier("variant_name_" + getVariant(), "string", BuildConfig.APPLICATION_ID);
        final String s = id == 0 ? toTitleCase(locale.getDisplayVariant()) : context.getResources().getString(id);
        return s.equals("") ? toTitleCase(getVariant()) : s;
    }

    /**
     * Returns the unique tag for this language composed by its language code, its country code and
     * its variant code separated by '-'.
     *
     * @return the unique tag for this language.
     */
    public String toTag() {
        final String language = getLanguage();
        final String country = getCountry();
        final String variant = getVariant();
        final StringBuilder tag = new StringBuilder();
        tag.append(language);
        if (country != null) {
            tag.append("-").append(country);
            if (variant != null) tag.append("-").append(variant);
        }
        return tag.toString();
    }

    public Locale toLocale() {
        return locale;
    }

    public static Language forTag(String tag) {
        final String fields[] = tag.split("-", 3);
        if (fields.length == 1) return new Language(fields[0]);
        if (fields.length == 2) return new Language(fields[0], fields[1]);
        if (fields.length == 3) return new Language(fields[0], fields[1], fields[2]);
        return null; // It is impossible to reach this line
    }

    public static Language forLocale(Locale locale) {
        return new Language(locale);
    }

    private static String toTitleCase(String s) {
        char[] chars = s.toLowerCase().toCharArray();
        boolean found = false;
        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i]) || chars[i]=='.' || chars[i]=='(') {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

}
