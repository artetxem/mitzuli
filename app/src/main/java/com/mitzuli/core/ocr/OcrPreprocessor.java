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

package com.mitzuli.core.ocr;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.googlecode.leptonica.android.Convert;
import com.googlecode.leptonica.android.Dewarp;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.WriteFile;

import android.graphics.Bitmap;
import android.os.Environment;

public class OcrPreprocessor {

    // Debugging stuff
    private static final boolean DEBUG = false;
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");


    public static Bitmap preprocess(final Bitmap input) {

        // Debugging stuff
        final File dir = new File(Environment.getExternalStorageDirectory(), "mitzuli_ocr");
        final String id = TIMESTAMP.format(new Date());
        if (DEBUG) dir.mkdirs();

        // The bitmap which with we will be working (the input bitmap should not be modified directly)
        Bitmap bitmap = Bitmap.createBitmap(input.getWidth(), input.getHeight(), input.getConfig());

        // Create an OpenCV Mat object with the input bitmap
        final Mat mat = new Mat();
        org.opencv.android.Utils.bitmapToMat(input, mat);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(mat, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_1_input.jpg"));
        }

        // Convert the input image to grayscale
        final Mat grayscale = new Mat(mat.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(mat, grayscale, Imgproc.COLOR_RGB2GRAY);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(grayscale, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_2_grayscale.jpg"));
        }

        // Edge detection
        final Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3,3));
        final Mat open = new Mat(mat.size(), CvType.CV_8UC1);
        final Mat close = new Mat(mat.size(), CvType.CV_8UC1);
        final Mat avg = new Mat(mat.size(), CvType.CV_8UC1);
        final Mat gradient = new Mat(mat.size(), CvType.CV_8UC1);
        final Mat edgemap = new Mat(mat.size(), CvType.CV_8UC1);
        Imgproc.morphologyEx(grayscale, open, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(grayscale, close, Imgproc.MORPH_CLOSE, kernel);
        Core.addWeighted(open, 0.5, close, 0.5, 0, avg);
        Imgproc.morphologyEx(avg, gradient, Imgproc.MORPH_GRADIENT, kernel);
        Imgproc.threshold(gradient, edgemap, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(edgemap, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_3_edges.jpg"));
        }

        // Extract word level connected-components from the dilated edge map
        final Mat dilatedEdges = new Mat(mat.size(), CvType.CV_8UC1);
        Imgproc.dilate(edgemap, dilatedEdges, kernel);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(dilatedEdges, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_4_dilated_edges.jpg"));
        }
        final List<MatOfPoint> wordCCs = new ArrayList<MatOfPoint>();
        Imgproc.findContours(dilatedEdges, wordCCs, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Filter word level connected-components individually and calculate their average attributes
        final List<MatOfPoint> individuallyFilteredWordCCs = new ArrayList<MatOfPoint>();
        final List<MatOfPoint> removedWordCCs = new ArrayList<MatOfPoint>();
        double avgWidth = 0, avgHeight = 0, avgArea = 0;
        for (MatOfPoint cc : wordCCs) {
            final Rect boundingBox = Imgproc.boundingRect(cc);
            if (boundingBox.height >= 6 // bounding box height >= 6
                    && boundingBox.area() >= 50 // bounding box area >= 50
                    && (double)boundingBox.width/(double)boundingBox.height >= 0.25 // bounding box aspect ratio >= 1:4
                    && boundingBox.width <= 0.75*mat.width() // bounding box width <= 0.75 image width
                    && boundingBox.height <= 0.75*mat.height()) // bounding box height <= 0.75 image height
            {
                individuallyFilteredWordCCs.add(cc);
                avgWidth += boundingBox.width;
                avgHeight += boundingBox.height;
                avgArea += boundingBox.area();
            } else {
                if (DEBUG) removedWordCCs.add(cc);
            }
        }
        avgWidth /= individuallyFilteredWordCCs.size();
        avgHeight /= individuallyFilteredWordCCs.size();
        avgArea /= individuallyFilteredWordCCs.size();
        if (DEBUG) {
            Imgproc.drawContours(mat, removedWordCCs, -1, new Scalar(0,0,255), -1); //URDIN ILUNA
            removedWordCCs.clear();
        }

        // Filter word level connected-components in relation to their average attributes
        final List<MatOfPoint> filteredWordCCs = new ArrayList<MatOfPoint>();
        for (MatOfPoint cc : individuallyFilteredWordCCs) {
            final Rect boundingBox = Imgproc.boundingRect(cc);
            if (boundingBox.width >= 0.125*avgWidth // bounding box width >= 0.125 average width
                    && boundingBox.width <= 8*avgWidth // bounding box width <= 8 average width
                    && boundingBox.height >= 0.25*avgHeight // bounding box height >= 0.25 average height
                    && boundingBox.height <= 4*avgHeight) // bounding box height <= 4 average height
            {
                filteredWordCCs.add(cc);
            } else {
                if (DEBUG) removedWordCCs.add(cc);
            }
        }
        if (DEBUG) {
            Imgproc.drawContours(mat, filteredWordCCs, -1, new Scalar(0,255,0), -1); //BERDEA
            Imgproc.drawContours(mat, removedWordCCs, -1, new Scalar(128,0,128), -1); //MORE ILUNA
            removedWordCCs.clear();
        }

        // Extract paragraph level connected-components
        final Mat filteredWordCCsMask = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0,0,0));
        Imgproc.drawContours(filteredWordCCsMask, filteredWordCCs, -1, new Scalar(255,0,0), -1);
        final List<MatOfPoint> paragraphCCs = new ArrayList<MatOfPoint>();
        final Mat paragraphs = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0));
        final Mat aux = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0));
        filteredWordCCsMask.copyTo(aux);
        Imgproc.morphologyEx(aux, paragraphs, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(30,30)));
        Imgproc.findContours(paragraphs, paragraphCCs, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Filter paragraph level connected-components according to the word level connected-components inside
        final List<MatOfPoint> textCCs = new ArrayList<MatOfPoint>();
        for (MatOfPoint paragraphCC : paragraphCCs) {
            final List<MatOfPoint> wordCCsInParagraphCC = new ArrayList<MatOfPoint>();
            final Mat paragraphCCMask = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0));
            final Mat wordsInParagraphCC = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0));
            Imgproc.drawContours(paragraphCCMask, Collections.singletonList(paragraphCC), -1, new Scalar(255,0,0), -1);
            filteredWordCCsMask.copyTo(wordsInParagraphCC, paragraphCCMask);
            Imgproc.findContours(wordsInParagraphCC, wordCCsInParagraphCC, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            final Rect boundingBox = Imgproc.boundingRect(paragraphCC);
            final double center = mat.size().width/2;
            final double distToCenter = center > boundingBox.x + boundingBox.width ? center - boundingBox.x - boundingBox.width : center < boundingBox.x ? boundingBox.x - center: 0.0;
            if (DEBUG) {
                System.err.println("****************************************");
                System.err.println("\tArea:                " + boundingBox.area());
                System.err.println("\tDistance to center:  " + distToCenter);
                System.err.println("\tCCs inside:          " + wordCCsInParagraphCC.size());
            }
            if ((wordCCsInParagraphCC.size() >= 10 || wordCCsInParagraphCC.size() >= 0.3*filteredWordCCs.size()) && mat.size().width/distToCenter >= 4) {
                textCCs.addAll(wordCCsInParagraphCC);
                if (DEBUG) {
                    System.err.println("\tText:                 YES");
                    Imgproc.drawContours(mat, Collections.singletonList(paragraphCC), -1, new Scalar(0,128,0), 5);
                }
            } else {
                if (DEBUG) {
                    System.err.println("\tText:                 NO");
                    Imgproc.drawContours(mat, Collections.singletonList(paragraphCC), -1, new Scalar(128,0,0), 5);
                }
            }
        }
        final Mat textCCsMask = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0,0,0));
        Imgproc.drawContours(textCCsMask, textCCs, -1, new Scalar(255,0,0), -1);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(mat, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_5_filtering.jpg"));
        }

        // Obtain the final text mask from the filtered connected-components
        final Mat textMask = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(0,0,0));
        Imgproc.dilate(textCCsMask, textMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(15,15)));
        Imgproc.morphologyEx(textMask, textMask, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(30,30)));
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(textMask, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_6_text_mask.jpg"));
        }

        // Binarize the input image in grayscale through adaptive Gaussian thresholding
        final Mat binary = new Mat(mat.size(), CvType.CV_8UC1);
        Imgproc.adaptiveThreshold(grayscale, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 7);
        if (DEBUG) {
            org.opencv.android.Utils.matToBitmap(binary, bitmap);
            saveImage(bitmap, new File(dir, "img_" + id + "_7_binary.jpg"));
        }

        // Apply the text mask to the binarized image
        final Mat binaryText = new Mat(mat.size(), CvType.CV_8UC1, new Scalar(255,0,0));
        binary.copyTo(binaryText, textMask);
        org.opencv.android.Utils.matToBitmap(binaryText, bitmap);
        if (DEBUG) saveImage(bitmap, new File(dir, "img_" + id + "_8_binary_text.jpg"));

        // Dewarp the text using Leptonica
        final Pix pixs = Convert.convertTo8(ReadFile.readBitmap(bitmap));
        final Pix pixsDewarp = Dewarp.dewarp(pixs, 0, Dewarp.DEFAULT_SAMPLING, 5, true);
        bitmap = WriteFile.writeBitmap(pixsDewarp);
        if (DEBUG) saveImage(bitmap, new File(dir, "img_" + id + "_9_dewarp.jpg"));

        return bitmap;
    }


    private static void saveImage(Bitmap bitmap, File file) {
        if (file.exists ()) file.delete ();
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
