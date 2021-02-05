package com.example.opencvproject;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MyCamera extends Activity {
    private FrameLayout mainLayout;
    private String TAG = "MyCamera";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        mainLayout = findViewById(R.id.frameLayout1);

        Camera mCamera = Camera.open();

        CameraPreview maPreview = new CameraPreview(this, mCamera);
        mainLayout.addView(maPreview);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) maPreview.getLayoutParams();
        params.gravity = Gravity.CENTER;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

//                    try {
//                        // load cascade file from application resources
//                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
//                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
//                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
//                        FileOutputStream os = new FileOutputStream(mCascadeFile);
//
//                        byte[] buffer = new byte[4096];
//                        int bytesRead;
//                        while ((bytesRead = is.read(buffer)) != -1) {
//                            os.write(buffer, 0, bytesRead);
//                        }
//                        is.close();
//                        os.close();
//
//                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
//                        if (mJavaDetector.empty()) {
//                            Log.e(TAG, "Failed to load cascade classifier");
//                            mJavaDetector = null;
//                        } else
//                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
//
//                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
//
//                        cascadeDir.delete();
//
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
//                    }
//
//                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
}