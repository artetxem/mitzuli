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

package com.mitzuli.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class IOUtils {

    public static void deleteAll(File f) {
        if (f.isDirectory()) for (File child : f.listFiles()) deleteAll(child);
        f.delete();
    }

    public static byte[] toByteArray(InputStream is) throws IOException {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final byte[] buffer = new byte[16384];
        int nRead;
        while ((nRead = is.read(buffer, 0, buffer.length)) != -1) data.write(buffer, 0, nRead);
        return data.toByteArray();
    }

    public static void write(byte[] data, File file) throws IOException {
        final OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
        os.write(data);
        os.close();
    }

}
