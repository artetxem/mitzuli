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

package com.mitzuli.core;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProcessedByteCountingInputStream extends FilterInputStream {

    private long processedByteCount = 0;

    public ProcessedByteCountingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        final int res = super.read();
        if (res != -1) processedByteCount++;
        return res;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        final int res = super.read(b, off, len);
        if (res > 0) processedByteCount += res;
        return res;
    }

    @Override
    public long skip(long n) throws IOException {
        final long res = super.skip(n);
        if (res > 0) processedByteCount += res;
        return res;
    }

    public long getProcessedByteCount() {
        return processedByteCount;
    }

}
