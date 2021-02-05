package com.example.opencvproject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.opencv.core.Mat;

import java.util.List;

@SuppressLint("ViewConstructor")
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback, Runnable {

    private static final String TAG = "CameraPreview";

    private final Context mContext;
    private final SurfaceHolder mHolder;
    private Camera mCamera;
    public List<Camera.Size> mSupportedPreviewSizes;
    public List<Camera.Size> mSupportedPictureSizes;
    private Camera.Size mPreviewSize;
    public Camera.Size mPictureSize;
    public Mat matYuv;
    public int frameHeight, frameWidth;
    private byte[] mFrame;
    private long frameCount = 0;
    private volatile boolean mThreadRun;

    int                         mSize;
    int[]                       mRGBA;
    private Bitmap              mBitmap;
    private int                 mViewMode;

    public static final int     VIEW_MODE_RGBA = 0;
    public static final int     VIEW_MODE_GRAY = 1;
    
    public CameraPreview(Context context, Camera camera) {
        super(context);
        mViewMode = VIEW_MODE_GRAY;
        mContext = context;
        mCamera = camera;
        // supported preview sizes
        mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
        for (Camera.Size str : mSupportedPreviewSizes)
            // supported picture sizes
            mSupportedPictureSizes = mCamera.getParameters().getSupportedPictureSizes();
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        System.arraycopy(data, 0, mFrame, 0, data.length);
        if (frameCount %3 == 0) {
            Canvas mCanvas = mHolder.lockCanvas();
            Bitmap bmp;
            bmp = processFrame(mFrame);
//                synchronized (this) {
//                    try {
//                        this.wait();
//                        if (!mThreadRun)
//                            break;
//
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }

            if (bmp != null) {
                if (mCanvas != null) {
                    mCanvas.drawBitmap(bmp, (mCanvas.getWidth() - frameWidth) / 2, (mCanvas.getHeight() - frameHeight) / 2, null);
                    mHolder.unlockCanvasAndPost(mCanvas);
                }
            }
        } else {
        }
        frameCount++;
        // At preview mode, the frame data will push to here.
        // But we do not want these data.
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            Log.e(TAG, "startCameraPreview", e);
        }

        // set preview size and make any resize, rotate or reformatting changes here
        // start preview with new settings
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            parameters.setPreviewFpsRange(30000, 30000);
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            parameters.setPictureSize(mPictureSize.width, mPictureSize.height);
            mCamera.setParameters(parameters);
            mCamera.setDisplayOrientation(setCameraDisplayOrientation((Activity) mContext, 1));
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(this);
            int size = parameters.getPreviewSize().width * parameters.getPreviewSize().height;
            size = size * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
            mFrame = new byte[size];
            onPreviewStarted(parameters.getPreviewSize().width, parameters.getPreviewSize().height);
            mCamera.startPreview();
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    mCamera.startPreview();
                } catch (Exception ex) {
                    Log.e(TAG, "After 2000 mils startCameraPreview:::::::::::::::::::::::::", ex);
                }
            }, 2000);
            Log.e(TAG, "startCameraPreview", e);
        }
    }

    protected void onPreviewStarted(int previewWidth, int previewHeight) {
        frameHeight = previewHeight;
        frameWidth = previewWidth;
        Log.i(TAG, "called onPreviewStarted("+previewWidth+", "+previewHeight+")");
        /* Create a bitmap that will be used through to calculate the image to */
        mBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        mRGBA = new int[previewWidth * previewHeight];
    }

    private Bitmap processFrame(byte[] data) {
        int frameSize = frameWidth * frameHeight;

        int[] rgba = mRGBA;

        final int view_mode = mViewMode;
        if (view_mode == VIEW_MODE_GRAY) {
            for (int i = 0; i < frameSize; i++) {
                int y = (0xff & ((int) data[i]));
                rgba[i] = 0xff000000 + (y << 16) + (y << 8) + y;
            }
        } else if (view_mode == VIEW_MODE_RGBA) {
            for (int i = 0; i < frameHeight; i++) {
                for (int j = 0; j < frameWidth; j++) {
                    int index = i * frameWidth + j;
                    int supply_index = frameSize + (i >> 1) * frameWidth + (j & ~1);
                    int y = (0xff & ((int) data[index]));
                    int u = (0xff & ((int) data[supply_index]));
                    int v = (0xff & ((int) data[supply_index + 1]));
                    y = Math.max(y, 16);

                    float y_conv = 1.164f * (y - 16);
                    int r = Math.round(y_conv + 1.596f * (v - 128));
                    int g = Math.round(y_conv - 0.813f * (v - 128) - 0.391f * (u - 128));
                    int b = Math.round(y_conv + 2.018f * (u - 128));

                    r = r < 0 ? 0 : (Math.min(r, 255));
                    g = g < 0 ? 0 : (Math.min(g, 255));
                    b = b < 0 ? 0 : (Math.min(b, 255));

                    rgba[i * frameWidth + j] = 0xff000000 + (b << 16) + (g << 8) + r;
                }
            }
        }

        mBitmap.setPixels(rgba, 0/* offset */, frameWidth /* stride */, 0, 0, frameWidth, frameHeight);
        return mBitmap;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
        if (mSupportedPictureSizes != null) {
            mPictureSize = getOptimalPreviewSize(mSupportedPictureSizes, width, height);
        }

        if (mPreviewSize != null) {
            float ratio;
            if (mPreviewSize.height >= mPreviewSize.width)
                ratio = (float) mPreviewSize.height / (float) mPreviewSize.width;
            else
                ratio = (float) mPreviewSize.width / (float) mPreviewSize.height;

            // One of these methods should be used, second method squishes preview slightly
            setMeasuredDimension(width, (int) (width * ratio));
            //        setMeasuredDimension((int) (width * ratio), height);
        }
    }

    public Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.height / size.width;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;

            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }

    public int setCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        //int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        // do something for phones running an SDK before lollipop
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return (result);
    }

    public Camera.Size getPictureSize() {
        return mPictureSize;
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
//        (new Thread(this)).start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        mThreadRun = false;
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
        onPreviewStopped();
    }

    protected void onPreviewStopped() {
        Log.i(TAG, "called onPreviewStopped");
        if(mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }

        if(mRGBA != null) {
            mRGBA = null;
        }
    }

    @Override
    public void run() {
        mThreadRun = true;
        Log.i(TAG, "Started processing thread");
        while (mThreadRun) {
            if (frameCount % 3 == 0) {
                Log.i(TAG, "Processing thread " + frameCount);
//                Bitmap bmp;
//                bmp = processFrame(mFrame);
////                synchronized (this) {
////                    try {
////                        this.wait();
////                        if (!mThreadRun)
////                            break;
////
////                    } catch (InterruptedException e) {
////                        e.printStackTrace();
////                    }
////                }
//
//                if (bmp != null) {
//                    if (mCanvas != null) {
//                        mCanvas.drawBitmap(bmp, (mCanvas.getWidth() - frameWidth) / 2, (mCanvas.getHeight() - frameHeight) / 2, null);
//                        mHolder.unlockCanvasAndPost(mCanvas);
//                    }
//                }
            }
        }
        Log.i(TAG, "Finished processing thread");
    }
}
