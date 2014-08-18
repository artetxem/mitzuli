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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import com.googlecode.leptonica.android.Dewarp;
import com.googlecode.leptonica.android.Pix;
import com.mitzuli.Image;

public class OcrPreprocessor {

    // Scalar constants
    private static final Scalar WHITE      = new Scalar(255);
    private static final Scalar BLACK      = new Scalar(0);
    private static final Scalar BLUE       = new Scalar(0,0,255);
    private static final Scalar GREEN      = new Scalar(0,255,0);
    private static final Scalar PURPLE     = new Scalar(128,0,128);
    private static final Scalar DARK_RED   = new Scalar(128,0,0);
    private static final Scalar DARK_GREEN = new Scalar(0,128,0);

    // Structuring element constants
    private static final Mat KERNEL_3X3   = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3,3));
    private static final Mat KERNEL_15X15 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(15,15));
    private static final Mat KERNEL_30X30 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(30,30));

    // Debugging stuff
    private static final boolean DEBUG = false;
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");


    /**
     * Binarizes and cleans the input image for OCR.
     *
     * @param input the input image, which is recycled by this method, so the caller should make a defensive copy of it if necessary.
     * @return the preprocessed image.
     */
    public static Image preprocess(final Image input) {
        return preprocess(input, null);
    }


    /**
     * Binarizes and cleans the input image for OCR, saving debugging images in the given directory.
     *
     * @param input the input image, which is recycled by this method, so the caller should make a defensive copy of it if necessary.
     * @param debugDir the directory to write the debugging images to, or null to disable debugging.
     * @return the preprocessed image.
     */
    static Image preprocess(final Image input, final File debugDir) {
        // TODO Temporary workaround to allow to manually enable debugging (the global final variable should be used)
        boolean DEBUG = debugDir != null;

        // Initialization
        final Mat mat = input.toGrayscaleMat();
        final Mat debugMat = DEBUG ? input.toRgbMat() : null;
        input.recycle();
        final Mat aux = new Mat(mat.size(), CvType.CV_8UC1);
        final Mat binary = new Mat(mat.size(), CvType.CV_8UC1);
        if (DEBUG) Image.fromMat(mat).write(new File(debugDir, "1_input.jpg"));

        // Binarize the input image in mat through adaptive Gaussian thresholding
        Imgproc.adaptiveThreshold(mat, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 51, 13);
        // Imgproc.adaptiveThreshold(mat, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 31, 7);

        // Edge detection
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, KERNEL_3X3);                   // Open
        Imgproc.morphologyEx(mat, aux, Imgproc.MORPH_CLOSE, KERNEL_3X3);                  // Close
        Core.addWeighted(mat, 0.5, aux, 0.5, 0, mat);                                     // Average
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_GRADIENT, KERNEL_3X3);               // Gradient
        Imgproc.threshold(mat, mat, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU); // Edge map
        if (DEBUG) Image.fromMat(mat).write(new File(debugDir, "2_edges.jpg"));

        // Extract word level connected-components from the dilated edge map
        Imgproc.dilate(mat, mat, KERNEL_3X3);
        if (DEBUG) Image.fromMat(mat).write(new File(debugDir, "3_dilated_edges.jpg"));
        final List<MatOfPoint> wordCCs = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mat, wordCCs, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

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
        wordCCs.clear();
        avgWidth /= individuallyFilteredWordCCs.size();
        avgHeight /= individuallyFilteredWordCCs.size();
        avgArea /= individuallyFilteredWordCCs.size();
        if (DEBUG) {
            Imgproc.drawContours(debugMat, removedWordCCs, -1, BLUE, -1);
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
        individuallyFilteredWordCCs.clear();
        if (DEBUG) {
            Imgproc.drawContours(debugMat, filteredWordCCs, -1, GREEN, -1);
            Imgproc.drawContours(debugMat, removedWordCCs, -1, PURPLE, -1);
            removedWordCCs.clear();
        }

        // Extract paragraph level connected-components
        mat.setTo(BLACK);
        Imgproc.drawContours(mat, filteredWordCCs, -1, WHITE, -1);
        final List<MatOfPoint> paragraphCCs = new ArrayList<MatOfPoint>();
        Imgproc.morphologyEx(mat, aux, Imgproc.MORPH_CLOSE, KERNEL_30X30);
        Imgproc.findContours(aux, paragraphCCs, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Filter paragraph level connected-components according to the word level connected-components inside
        final List<MatOfPoint> textCCs = new ArrayList<MatOfPoint>();
        for (MatOfPoint paragraphCC : paragraphCCs) {
            final List<MatOfPoint> wordCCsInParagraphCC = new ArrayList<MatOfPoint>();
            aux.setTo(BLACK);
            Imgproc.drawContours(aux, Collections.singletonList(paragraphCC), -1, WHITE, -1);
            Core.bitwise_and(mat, aux, aux);
            Imgproc.findContours(aux, wordCCsInParagraphCC, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
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
                    System.err.println("\tText:                YES");
                    Imgproc.drawContours(debugMat, Collections.singletonList(paragraphCC), -1, DARK_GREEN, 5);
                }
            } else {
                if (DEBUG) {
                    System.err.println("\tText:                NO");
                    Imgproc.drawContours(debugMat, Collections.singletonList(paragraphCC), -1, DARK_RED, 5);
                }
            }
        }
        filteredWordCCs.clear();
        paragraphCCs.clear();
        mat.setTo(WHITE);
        Imgproc.drawContours(mat, textCCs, -1, BLACK, -1);
        textCCs.clear();
        if (DEBUG) Image.fromMat(debugMat).write(new File(debugDir, "4_filtering.jpg"));

        // Obtain the final text mask from the filtered connected-components
        Imgproc.erode(mat, mat, KERNEL_15X15);
        Imgproc.morphologyEx(mat, mat, Imgproc.MORPH_OPEN, KERNEL_30X30);
        if (DEBUG) Image.fromMat(mat).write(new File(debugDir, "5_text_mask.jpg"));

        // Apply the text mask to the binarized image
        if (DEBUG) Image.fromMat(binary).write(new File(debugDir, "6_binary.jpg"));
        binary.setTo(WHITE, mat);
        if (DEBUG) Image.fromMat(binary).write(new File(debugDir, "7_binary_text.jpg"));

        // Dewarp the text using Leptonica
        Pix pixs = Image.fromMat(binary).toGrayscalePix();
        Pix pixsDewarp = Dewarp.dewarp(pixs, 0, Dewarp.DEFAULT_SAMPLING, 5, true);
        final Image result = Image.fromGrayscalePix(pixsDewarp);
        if (DEBUG) result.write(new File(debugDir, "8_dewarp.jpg"));

        // Clean up
        pixs.recycle();
        mat.release();
        aux.release();
        binary.release();
        if (debugMat != null) debugMat.release();

        return result;
    }

}
