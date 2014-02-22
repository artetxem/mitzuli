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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.edmodo.cropper.cropwindow.CropOverlayView;
import com.edmodo.cropper.cropwindow.edge.Edge;


/**
 * A custom view to take cropped pictures with the device camera
 * This is somehow an adaptation of com.edmodo.cropper.CropImageView for camera images
 */
public class CameraCropperView extends FrameLayout { //TODO Handle the case in which autofocus is not available (is that possible?)

    public static interface OpenCameraCallback {
        public void onCameraOpened(boolean success);
    }

    public static interface CroppedPictureCallback {
        public void onPictureCropped(Bitmap croppedPicture);
    }

    private static final Comparator<Size> sizeComparator = new Comparator<Size>() {
        @Override public int compare(Size lhs, Size rhs) {
            return lhs.width*lhs.height - rhs.width*rhs.height;
        }
    };


    private Camera camera;
    private int cameraRotation;
    private Size pictureSize;
    private List<Size> previewSizes;

    private final SurfaceHolder previewHolder;
    private final SurfaceView preview;
    private final CropOverlayView cropOverlayView;
    private final Rect cropOverlayBitmapRect;

    private int layoutWidth;
    private int layoutHeight;

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {

        @Override public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // Starting the preview for the first time is slow, so we will do it in a separate thread
            new Thread() {@Override public void run() {camera.startPreview();}}.start();
        }

        @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

        @Override public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) camera.stopPreview();
        }

    };


    private class OpenCameraTask extends AsyncTask<Void, Void, Boolean> {

        private final Context context;
        private final OpenCameraCallback callback;

        public OpenCameraTask(Context context, OpenCameraCallback callback) {
            this.context = context;
            this.callback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... args) {
            if (camera != null) return true;

            try {
                int cameraOrientation;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    // Choose the first back-facing camera
                    boolean cameraAvailable = false;
                    final CameraInfo cameraInfo = new CameraInfo();
                    int cameraId = 0;
                    while (!cameraAvailable && cameraId < Camera.getNumberOfCameras()) {
                        Camera.getCameraInfo(cameraId, cameraInfo);
                        if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) cameraAvailable = true;
                        else cameraId++;
                    }
                    if (!cameraAvailable) return false; // No back-facing camera available...
                    camera = Camera.open(cameraId);
                    cameraOrientation = cameraInfo.orientation;
                } else {
                    camera = Camera.open();
                    cameraOrientation = 90; // TODO Is this correct?
                }
                if (camera == null) return false; // No camera available...
                final Camera.Parameters params = camera.getParameters();

                // Choose the biggest possible picture size and the corresponding preview sizes with the same ratio
                pictureSize = null;
                previewSizes = new ArrayList<Size>();
                final List<Size> supportedPictureSizes = params.getSupportedPictureSizes();
                final List<Size> supportedPreviewSizes = params.getSupportedPreviewSizes();
                Collections.sort(supportedPictureSizes, Collections.reverseOrder(sizeComparator));
                for (Size supportedPictureSize : supportedPictureSizes) {
                    for (Size supportedPreviewSize : supportedPreviewSizes) {
                        if (supportedPictureSize.width * supportedPreviewSize.height == supportedPictureSize.height * supportedPreviewSize.width) {
                            pictureSize = supportedPictureSize;
                            previewSizes.add(supportedPreviewSize);
                        }
                    }
                    if (pictureSize != null) break;
                }
                Collections.sort(previewSizes, Collections.reverseOrder(sizeComparator));
                if (pictureSize == null) { // No picture size ratio and preview size ratio match (shouldn't happen...)
                    releaseCamera();
                    return false;
                }

                // Set parameters
                params.setPictureSize(pictureSize.width, pictureSize.height);
                params.setPreviewSize(previewSizes.get(0).width, previewSizes.get(0).height);
                params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
                params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(params);

                // Set the camera orientation
                int rotation = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                int degrees = 0;
                switch (rotation) {
                    case Surface.ROTATION_0: degrees = 0; break;
                    case Surface.ROTATION_90: degrees = 90; break;
                    case Surface.ROTATION_180: degrees = 180; break;
                    case Surface.ROTATION_270: degrees = 270; break;
                }
                cameraRotation = (cameraOrientation - degrees + 360) % 360;
                camera.setDisplayOrientation(cameraRotation);
                // TODO Should we set the orientation in the params as well (params.setRotation())???

            } catch (Exception e) {
                releaseCamera();
                return false; // Some error while opening the camera...
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            callback.onCameraOpened(success);
        }

    }


    public CameraCropperView(Context context) {
        super(context);

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View v = inflater.inflate(R.layout.camera_cropper_view, this, true);

        preview = (SurfaceView) v.findViewById(R.id.cameraPreview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        cropOverlayView = (CropOverlayView) v.findViewById(R.id.cropOverlayView);
        cropOverlayBitmapRect = new Rect();
    }

    private Size bestSizeForHeight(List<Size> sizes, int height) {
        Size bestSize = null;
        for (Size size : sizes)
            if (bestSize == null ||
                    Math.abs((double)size.height/(double)height-1) < Math.abs((double)bestSize.height/(double)height-1))
                bestSize = size;
        return bestSize;
    }

    private Size bestSizeForWidth(List<Size> sizes, int width) {
        Size bestSize = null;
        for (Size size : sizes)
            if (bestSize == null ||
                    Math.abs((double)size.width/(double)width-1) < Math.abs((double)bestSize.width/(double)width-1))
                bestSize = size;
        return bestSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (camera != null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            if (cameraRotation == 90 || cameraRotation == 270) {
                int aux = widthMode;
                widthMode = heightMode;
                heightMode = aux;
                aux = widthSize;
                widthSize = heightSize;
                heightSize = aux;
            }

            Size previewSize = null;
            if (widthMode == MeasureSpec.UNSPECIFIED && heightMode == MeasureSpec.UNSPECIFIED) {
                previewSize = previewSizes.get(0); // Choose the biggest preview size
                layoutWidth = previewSize.width;
                layoutHeight = previewSize.height;
            } else if (widthMode == MeasureSpec.UNSPECIFIED) { // heigthMode == EXACTLY or AT_MOST
                previewSize = bestSizeForHeight(previewSizes, heightSize);
                layoutWidth = previewSize.width * heightSize / previewSize.height;
                layoutHeight = heightSize;
            } else if (heightMode == MeasureSpec.UNSPECIFIED) { // widthMode == EXACTLY or AT_MOST
                previewSize = bestSizeForWidth(previewSizes, widthSize);
                layoutWidth = widthSize;
                layoutHeight = previewSize.height * widthSize / previewSize.width;
            } else if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                if (widthSize * pictureSize.height > heightSize * pictureSize.width)
                    previewSize = bestSizeForWidth(previewSizes, widthSize);
                else
                    previewSize = bestSizeForHeight(previewSizes, heightSize);
                layoutWidth = widthSize;
                layoutHeight = heightSize;
            } else if (widthMode == MeasureSpec.EXACTLY) { // heigthMode == AT_MOST
                previewSize = bestSizeForWidth(previewSizes, widthSize);
                layoutWidth = widthSize;
                if (widthSize * pictureSize.height > heightSize * pictureSize.width)
                    layoutHeight = heightSize;
                else
                    layoutHeight = pictureSize.height * widthSize / pictureSize.width;
            } else if (heightMode == MeasureSpec.EXACTLY) { // widthMode == AT_MOST
                previewSize = bestSizeForHeight(previewSizes, heightSize);
                if (widthSize * pictureSize.height > heightSize * pictureSize.width)
                    layoutWidth = pictureSize.width * heightSize / pictureSize.height;
                else
                    layoutWidth = widthSize;
                layoutHeight = heightSize;
            } else { // widthMode == heightMode == AT_MOST
                if (widthSize * pictureSize.height > heightSize * pictureSize.width) {
                    previewSize = bestSizeForHeight(previewSizes, heightSize);
                    layoutWidth = pictureSize.width * heightSize / pictureSize.height;
                    layoutHeight = heightSize;
                } else {
                    previewSize = bestSizeForWidth(previewSizes, widthSize);
                    layoutWidth = widthSize;
                    layoutHeight = pictureSize.height * widthSize / pictureSize.width;
                }
            }

            if (cameraRotation == 90 || cameraRotation == 270) {
                final int aux = layoutWidth;
                layoutWidth = layoutHeight;
                layoutHeight = aux;
            }

            // TODO Review this...
            final Camera.Parameters params = camera.getParameters();
            if (!previewSize.equals(params.getPreviewSize())) {
                camera.stopPreview();
                params.setPreviewSize(previewSize.width, previewSize.height);
                camera.setParameters(params);
                camera.startPreview();
            }

            cropOverlayBitmapRect.left = 0;
            cropOverlayBitmapRect.top = 0;
            cropOverlayBitmapRect.right = layoutWidth;
            cropOverlayBitmapRect.bottom = layoutHeight;
            cropOverlayView.setBitmapRect(cropOverlayBitmapRect);

            setMeasuredDimension(layoutWidth, layoutHeight);

        } else {
            setMeasuredDimension(widthSize, heightSize);
        }

    }

    //This should crop the camera preview, but it does not work properly in old Android versions, so we will let the preview be stretched instead
    /*@Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int pictureWidth, pictureHeight;
        if (cameraRotation == 90 || cameraRotation == 270) {
            pictureWidth = pictureSize.height;
            pictureHeight = pictureSize.width;
        } else {
            pictureWidth = pictureSize.width;
            pictureHeight = pictureSize.height;
        }

        if (layoutWidth * pictureHeight > layoutHeight * pictureWidth) {
            final int scaledHeight = pictureHeight * layoutWidth / pictureWidth;
            preview.layout(0, (layoutHeight - scaledHeight) / 2, layoutWidth, (layoutHeight + scaledHeight) / 2);
        } else {
            final int scaledWidth = pictureWidth * layoutHeight / pictureHeight;
            preview.layout((layoutWidth - scaledWidth) / 2, 0, (layoutWidth + scaledWidth) / 2, layoutHeight);
        }
    }*/


    public void openCamera(Context context, OpenCameraCallback callback) {
        new OpenCameraTask(context, callback).execute();
    }

    public void releaseCamera() {
        if (camera != null){
            camera.release();
            camera = null;
        }
    }

    public void takeCroppedPicture(final CroppedPictureCallback callback) {
        final Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera cam) {
                Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);

                if (cameraRotation != 0) {
                    final Bitmap oldPicture = picture;
                    final Matrix matrix = new Matrix();
                    matrix.postRotate(cameraRotation);
                    picture = Bitmap.createBitmap(picture, 0, 0, picture.getWidth(), picture.getHeight(), matrix, false); // TODO Should we set filter to true???
                    oldPicture.recycle();
                }

                int pictureWidth, pictureHeight;
                if (cameraRotation == 90 || cameraRotation == 270) {
                    pictureWidth = pictureSize.height;
                    pictureHeight = pictureSize.width;
                } else {
                    pictureWidth = pictureSize.width;
                    pictureHeight = pictureSize.height;
                }

                // This would be the code to use if we were to crop the camera preview
                /*float scaleFactor, widthOffset, heightOffset;
                if (layoutWidth * pictureHeight > layoutHeight * pictureWidth) {
                    scaleFactor = (float)pictureWidth / (float)layoutWidth;
                    widthOffset = 0;
                    heightOffset = (pictureHeight - layoutHeight*scaleFactor) / 2;
                } else {
                    scaleFactor = (float)pictureHeight / (float)layoutHeight;
                    widthOffset = (pictureWidth - layoutWidth*scaleFactor) / 2;
                    heightOffset = 0;
                }
                final Bitmap croppedPicture = Bitmap.createBitmap(picture,
                        (int) (Edge.LEFT.getCoordinate() * scaleFactor + widthOffset),
                        (int) (Edge.TOP.getCoordinate() * scaleFactor + heightOffset),
                        (int) (Edge.getWidth() * scaleFactor),
                        (int) (Edge.getHeight() * scaleFactor));*/

                // This would be the code to use if we were to stretch the camera preview
                final float scaleFactorHorizontal = (float)pictureWidth / (float)layoutWidth;
                final float scaleFactorVertical = (float)pictureHeight / (float)layoutHeight;
                final Bitmap croppedPicture = Bitmap.createBitmap(picture,
                        (int) (Edge.LEFT.getCoordinate() * scaleFactorHorizontal),
                        (int) (Edge.TOP.getCoordinate() * scaleFactorVertical),
                        (int) (Edge.getWidth() * scaleFactorHorizontal),
                        (int) (Edge.getHeight() * scaleFactorVertical));

                camera.startPreview();
                callback.onPictureCropped(croppedPicture);
            }
        };
        final Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera cam) {
                camera.takePicture(null, null, pictureCallback);
            }
        };
        camera.autoFocus(autoFocusCallback);
        //mCamera.takePicture(null, null, pictureCallback);
    }

}
