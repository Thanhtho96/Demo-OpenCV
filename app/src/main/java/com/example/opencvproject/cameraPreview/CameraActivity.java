package com.example.opencvproject.cameraPreview;

import android.app.Activity;
import android.os.Bundle;

public class CameraActivity extends Activity {
    private MyGLSurfaceView glSurfaceView;
    private MyCamera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCamera = new MyCamera();

        glSurfaceView = new MyGLSurfaceView(this, mCamera);

        setContentView(glSurfaceView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCamera.stop();
    }
}