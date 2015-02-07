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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;


public class SignatureVerifierInputStream extends FilterInputStream {

    private final Signature verifier;
    private final byte[] signature;

    public SignatureVerifierInputStream(InputStream in, String algorithm, PublicKey publicKey, byte[] signature) throws NoSuchAlgorithmException, InvalidKeyException {
        super(in);
        this.verifier = Signature.getInstance(algorithm);
        this.verifier.initVerify(publicKey);
        this.signature = new byte[signature.length];
        System.arraycopy(signature, 0, this.signature, 0, signature.length);
    }

    @Override
    public int read() throws IOException {
        try {
            final int res = super.read();
            if (res != -1) verifier.update((byte)res);
            return res;
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            final int res = super.read(b, off, len);
            if (res != -1) verifier.update(b, off, res);
            return res;
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifies() {
        try {
            return verifier.verify(signature);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

}
