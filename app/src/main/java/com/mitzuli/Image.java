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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.WriteFile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Rect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.File;


public abstract class Image {


    public static Image fromCroppedData(byte[] data, int rotation, int x, int y, int width, int height) {
        return new CroppedDataImage(data, rotation, x, y, width, height);
    }

    public static Image fromMat(Mat mat) {
        return new MatImage(mat);
    }

    public static Image fromGrayscalePix(Pix pix) {
        return new GrayscalePixImage(pix);
    }

    public abstract void recycle();
    public abstract Bitmap toBitmap();
    public abstract Mat toGrayscaleMat();
    public abstract Mat toRgbMat();
    public abstract Pix toGrayscalePix();
    public abstract void write(File file);



    private static class MatImage extends Image {

        private Mat mat;

        public MatImage(Mat mat) {
            if (mat == null) throw new NullPointerException();
            if (mat.type() != CvType.CV_8UC1 && mat.type() != CvType.CV_8UC3) throw new IllegalArgumentException("Unsupported Mat type: " + mat.type());
            this.mat = mat;
        }

        @Override
        public void recycle() {
            mat.release();
            mat = null;
        }

        @Override
        public Bitmap toBitmap() {
            if (mat == null) throw new IllegalStateException("Recycled image");
            final Bitmap bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
            org.opencv.android.Utils.matToBitmap(mat, bitmap);
            return bitmap;
        }

        @Override
        public Mat toGrayscaleMat() {
            if (mat == null) throw new IllegalStateException("Recycled image");
            if (mat.type() == CvType.CV_8UC1) { // Grayscale
                return mat.clone();
            } else { // RGB
                final Mat result = new Mat(mat.size(), CvType.CV_8UC1);
                Imgproc.cvtColor(mat, result, Imgproc.COLOR_RGB2GRAY);
                return result;
            }
        }

        @Override
        public Mat toRgbMat() {
            if (mat == null) throw new IllegalStateException("Recycled image");
            if (mat.type() == CvType.CV_8UC1) { // Grayscale
                final Mat result = new Mat(mat.size(), CvType.CV_8UC3);
                Imgproc.cvtColor(mat, result, Imgproc.COLOR_GRAY2RGB);
                return result;
            } else { // RGB
                return mat.clone();
            }
        }

        @Override
        public Pix toGrayscalePix() {
            if (mat == null) throw new IllegalStateException("Recycled image");
            final byte[] bytes = new byte[(int)mat.total()];
            mat.get(0, 0, bytes);
            return ReadFile.readBytes8(bytes, mat.width(), mat.height());
        }

        @Override
        public void write(File file) {
            if (mat == null) throw new IllegalStateException("Recycled image");
            Highgui.imwrite(file.getAbsolutePath(), mat);
        }

    }

    private static class GrayscalePixImage extends Image {

        private Pix pix;

        public GrayscalePixImage(Pix pix) {
            if (pix == null) throw new NullPointerException();
            if (pix.getDepth() != 8) throw new IllegalArgumentException("Unsupported Pix depth: " + pix.getDepth());
            this.pix = pix;
        }

        @Override
        public void recycle() {
            pix.recycle();
            pix = null;
        }

        @Override
        public Bitmap toBitmap() {
            if (pix == null) throw new IllegalStateException("Recycled image");
            return WriteFile.writeBitmap(pix);
        }

        @Override
        public Mat toGrayscaleMat() {
            if (pix == null) throw new IllegalStateException("Recycled image");
            return new MatOfByte(WriteFile.writeBytes8(pix)).reshape(0, pix.getHeight());
        }

        public Mat toRgbMat() {
            final Image aux = Image.fromMat(toGrayscaleMat());
            final Mat result = aux.toRgbMat();
            aux.recycle();
            return result;
        }

        @Override
        public Pix toGrayscalePix() {
            if (pix == null) throw new IllegalStateException("Recycled image");
            return pix.copy();
        }

        @Override
        public void write(File file) {
            if (pix == null) throw new IllegalStateException("Recycled image");
            Image.fromMat(toGrayscaleMat()).write(file);
        }

    }

    private static class CroppedDataImage extends Image {

        private byte[] data;
        private final int rotation;
        private final int x, y, width, height;

        public CroppedDataImage(byte[] data, int rotation, int x, int y, int width, int height) {
            if (data == null) throw new NullPointerException();
            this.data = data;
            this.rotation = rotation;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public void recycle() {
            data = null;
        }

        @Override
        public Bitmap toBitmap() {
            if (data == null) throw new IllegalStateException("Recycled image");
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
            Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (rotation != 0) {
                final Bitmap oldPicture = picture;
                final Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                picture = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, false); // TODO Should we set filter to true???
                oldPicture.recycle();
            }
            final Bitmap croppedPicture = Bitmap.createBitmap(picture, x, y, width, height);
            picture.recycle();
            return croppedPicture;
        }

        @Override
        public Mat toGrayscaleMat() {
            if (data == null) throw new IllegalStateException("Recycled image");
            return toMat(true);
        }

        @Override
        public Mat toRgbMat() {
            if (data == null) throw new IllegalStateException("Recycled image");
            return toMat(false);
        }

        private Mat toMat(boolean grayscale) {
            if (data == null) throw new IllegalStateException("Recycled image");
            final Mat mat = Highgui.imdecode(new MatOfByte(data), grayscale ? Highgui.CV_LOAD_IMAGE_GRAYSCALE : Highgui.CV_LOAD_IMAGE_COLOR);
            if (rotation == 90) {
                Core.transpose(mat, mat);
                Core.flip(mat, mat, 1);
            } else if (rotation == 180) {
                Core.flip(mat, mat, -1);
            } else if (rotation == 270) {
                Core.transpose(mat, mat);
                Core.flip(mat, mat, 0);
            }
            final Mat submat = mat.submat(new Rect(x, y, width, height));
            final Mat res = submat.clone();
            mat.release();
            submat.release();
            return res;
        }

        @Override
        public Pix toGrayscalePix() {
            if (data == null) throw new IllegalStateException("Recycled image");
            final Mat mat = toGrayscaleMat();
            final Pix pix = Image.fromMat(mat).toGrayscalePix();
            mat.release();
            return pix;
        }

        @Override
        public void write(File file) {
            if (data == null) throw new IllegalStateException("Recycled image");
            final Mat mat = toRgbMat();
            Highgui.imwrite(file.getAbsolutePath(), mat);
            mat.release();
        }

    }


}
