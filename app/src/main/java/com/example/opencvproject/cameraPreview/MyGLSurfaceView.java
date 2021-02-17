package com.example.opencvproject.cameraPreview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("ViewConstructor")
class MyGLSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer
{
    private final static String LOG_TAG = "MyGLSurfaceView";
    private MyCamera mCamera;
    private SurfaceTexture mSurface;
    private DirectVideo mDirectVideo;

    public MyGLSurfaceView(Context context, MyCamera camera)
    {
        super(context);

        mCamera = camera;
        setEGLContextClientVersion(2);

        setRenderer(this);
    }

    @Override
    public void onDrawFrame(GL10 gl)
    {
        float[] mtx = new float[16];
        mSurface.updateTexImage();
        mSurface.getTransformMatrix(mtx);

        mDirectVideo.draw();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.v(LOG_TAG, "Surface Changed");
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.v(LOG_TAG, "Surface Created");
        int texture = createTexture();
        mDirectVideo = new DirectVideo(texture);
        mSurface = new SurfaceTexture(texture);
        mCamera.start(mSurface);
    }

    private int createTexture()
    {
        int[] textures = new int[1];

        // generate one texture pointer and bind it as an external texture.
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);

        // No mip-mapping with camera source.
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

        // Clamp to edge is only option.
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return textures[0];
    }

    public static int loadShader(int type, String shaderCode){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }
}