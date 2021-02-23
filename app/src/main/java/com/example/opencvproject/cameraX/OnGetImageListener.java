package com.example.opencvproject.cameraX;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import com.example.opencvproject.R;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener extends SurfaceView implements SurfaceHolder.Callback {
    private static final int INPUT_SIZE = 224;
    private static final String TAG = "OnGetImageListener";

    private Bitmap rgbBitmap = null;
    private Bitmap cropRgbBitmap = null;

    private FaceDet mFaceDet;
    private Paint mFaceLandmarkPaint;
    private File mCascadeFile;
    private YuvToRgbConverter yuvConverter;
    private SurfaceHolder holder;
    private Rect inputImageRect;
    private float resizeRatio = 0f;
    // The glasses, cigarette bitmap
    private Bitmap glassesBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.glasses);
    private Bitmap cigaretteBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.cigarette);

    public OnGetImageListener(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                inputImageRect = new Rect(0, 0, getRight(), getBottom());
            }
        });
    }

    public void initialize() {
        yuvConverter = new YuvToRgbConverter(getContext());
        try {
            InputStream is = getContext().getAssets().open("shape_predictor_68_face_landmarks.dat");
            File cascadeDir = getContext().getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "shape_predictor_68_face_landmarks.dat");

            if (!mCascadeFile.exists()) {
                FileOutputStream os = new FileOutputStream(mCascadeFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                is.close();
                os.close();
            }

            mFaceDet = new FaceDet(mCascadeFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }

        mFaceLandmarkPaint = new Paint();
        mFaceLandmarkPaint.setColor(Color.RED);
        mFaceLandmarkPaint.setStrokeWidth(2);
        mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
        matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    private Bitmap rotate(Bitmap bitmap, Integer degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public void onImageAvailable(final Image image, Integer rotateDegree) {
        if (holder == null) {
            return;
        }
        rgbBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        cropRgbBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        yuvConverter.yuvToRgb(image, rgbBitmap);
        rgbBitmap = rotate(rgbBitmap, rotateDegree);
        drawResizedBitmap(rgbBitmap, cropRgbBitmap);
        Log.d(TAG, "rgbBitmap: " + rgbBitmap.getWidth() + " " + cropRgbBitmap.getWidth());

        List<VisionDetRet> results = mFaceDet.detect(cropRgbBitmap);
        // Draw on bitmap
        if (results != null) {
            Canvas canvas = new Canvas(rgbBitmap);

            resizeRatio = ((float) rgbBitmap.getWidth()) / ((float) cropRgbBitmap.getWidth());
            for (final VisionDetRet ret : results) {
                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                Point leftEye = null, rightEye = null, leftMouth = null, rightMouth = null;
                for (int i = 0; i < landmarks.size(); i++) {

                    // draw all the landmark

//                    int pointX = (int) ((landmarks.get(i).x * resizeRatio));
//                    int pointY = (int) ((landmarks.get(i).y + 90) * resizeRatio);
//                    canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
                    if (i == 36) {
                        leftEye = landmarks.get(i);
                        continue;
                    }
                    if (i == 45) {
                        rightEye = landmarks.get(i);
                        continue;

                    }
                    if (i == 48) {
                        leftMouth = landmarks.get(i);
                        continue;
                    }
                    if (i == 54) {
                        rightMouth = landmarks.get(i);
                    }
                }
                drawGlasses(canvas, leftEye, rightEye);
                drawCigarette(canvas, leftMouth, rightMouth);
            }
        }

        Trace.endSection();
        tryDrawing(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private void tryDrawing(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Cannot draw onto the canvas as it's null");
        } else {
            drawMyStuff(canvas);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawMyStuff(Canvas canvas) {
        canvas.drawBitmap(rgbBitmap, null, inputImageRect, null);
    }


    private void drawGlasses(Canvas canvas, Point leftEye, Point rightEye) {
        if (leftEye == null || rightEye == null) return;
        canvas.drawBitmap(glassesBitmap,
                null,
                new Rect(
                        (int) (leftEye.x * resizeRatio),
                        (int) ((leftEye.y + 90) * resizeRatio),
                        (int) (rightEye.x * resizeRatio),
                        (int) ((leftEye.y + 90) * resizeRatio) + 30),
                null);
    }

    private void drawCigarette(Canvas canvas, Point leftMouth, Point rightMouth) {
        if (leftMouth == null || rightMouth == null) return;
        int mouthLength = (int) ((rightMouth.x - leftMouth.x) * resizeRatio);
        canvas.drawBitmap(cigaretteBitmap,
                null,
                new Rect(
                        (int) (leftMouth.x * resizeRatio) - mouthLength,
                        (int) ((leftMouth.y + 90) * resizeRatio),
                        (int) (leftMouth.x * resizeRatio),
                        (int) ((leftMouth.y + 90) * resizeRatio) + mouthLength),
                null);
    }
}
