package com.example.opencvproject.cameraX

import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import com.example.opencvproject.R
import com.example.opencvproject.utils.computeExifOrientation
import com.example.opencvproject.utils.decodeExifOrientation
import com.tzutalin.dlib.FaceDet
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Math.toDegrees
import kotlin.math.*

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
class OnGetImageListener(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs),
    SurfaceHolder.Callback {
    private lateinit var rgbBitmap: Bitmap
    private lateinit var cropRgbBitmap: Bitmap
    private var mFaceDet: FaceDet? = null
    private var mCascadeFile: File? = null
    private var yuvConverter = YuvToRgbConverter(getContext())
    private lateinit var surfaceHolder: SurfaceHolder
    private val inputImageRect = Rect()
    private val glassRect: Rect = Rect()
    private val cigaretteRect = Rect()
    private var resizeRatio = 0f

    // The glasses, cigarette bitmap
    private val glassesBitmap = BitmapFactory.decodeResource(resources, R.drawable.glasses)
    private val cigaretteBitmap = BitmapFactory.decodeResource(resources, R.drawable.cigarette)
    fun initialize() {
        try {
            val inputStream = context.assets.open("shape_predictor_68_face_landmarks.dat")
            val cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE)
            mCascadeFile = File(cascadeDir, "shape_predictor_68_face_landmarks.dat")
            if (mCascadeFile?.exists() == false) {
                val outputStream = FileOutputStream(mCascadeFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                inputStream.close()
                outputStream.close()
            }
            mFaceDet = FaceDet(mCascadeFile?.absolutePath)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load cascade. Exception thrown: $e")
        }
    }

    @Synchronized
    fun deInitialize() {
        mFaceDet?.release()
    }

    private fun drawResizedBitmap(src: Bitmap, dst: Bitmap) {
        val minDim = min(src.width, src.height).toFloat()
        val matrix = Matrix()

        // We only want the center square out of the original rectangle.
        val translateX = -max(0f, (src.width - minDim) / 2)
        val translateY = -max(0f, (src.height - minDim) / 2)
        matrix.preTranslate(translateX, translateY)
        val scaleFactor = dst.height / minDim
        matrix.postScale(scaleFactor, scaleFactor)

        // Rotate around the center if necessary.
        matrix.postTranslate(-dst.width / 2.0f, -dst.height / 2.0f)
        matrix.postTranslate(dst.width / 2.0f, dst.height / 2.0f)
        val canvas = Canvas(dst)
        canvas.drawBitmap(src, matrix, null)
    }

    private fun rotate(bitmap: Bitmap, degree: Int, mirrored: Boolean): Bitmap {
        val matrix = decodeExifOrientation(computeExifOrientation(degree, mirrored))
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    }

    fun onImageAvailable(image: Image, rotateDegree: Int, mirrored: Boolean) {
        if (this::surfaceHolder.isInitialized.not()) {
            return
        }
        rgbBitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        cropRgbBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        yuvConverter.yuvToRgb(image, rgbBitmap)
        // Todo rotate Bitmap take huge resource, so find other way to rotate it.
        rgbBitmap = rotate(rgbBitmap, rotateDegree, mirrored)
        drawResizedBitmap(rgbBitmap, cropRgbBitmap)
        Log.d(TAG, "rgbBitmap: " + rgbBitmap.width + " " + cropRgbBitmap.width)
        val results = mFaceDet?.detect(cropRgbBitmap)
        // Draw on bitmap
        if (results != null) {
            val canvas = Canvas(rgbBitmap)
            resizeRatio = rgbBitmap.width.toFloat() / cropRgbBitmap.width.toFloat()
            for (ret in results) {
                val landmarks = ret.faceLandmarks

                val eyeBrowLeft = landmarks[20]
                val topNose = landmarks[27]
                val bottomNose = landmarks[30]
                val leftEye = landmarks[36]
                val topLeftEyePoint = landmarks[38]
                val topRightEyePoint = landmarks[43]
                val rightEye = landmarks[45]
                val leftMouth = landmarks[67]
                val rightMouth = landmarks[64]

                // Todo replace 90 with margin top

                //  draw all the landmark
//                for (int i = 0; i < landmarks.size(); i++) {
//                    int pointX = (int) ((landmarks.get(i).x * resizeRatio));
//                    int pointY = (int) ((landmarks.get(i).y + 90) * resizeRatio);
//                    canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
//                }
                drawGlasses(
                    canvas,
                    leftEye,
                    rightEye,
                    topLeftEyePoint,
                    topRightEyePoint,
                    eyeBrowLeft.y,
                    topNose,
                    bottomNose
                )
                drawCigarette(canvas, leftMouth, rightMouth)
            }
        }
        tryDrawing(surfaceHolder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceCreated(holder: SurfaceHolder) {
        this.surfaceHolder = holder
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    private fun tryDrawing(holder: SurfaceHolder) {
        val canvas = holder.lockCanvas()
        if (canvas == null) {
            Log.e(TAG, "Cannot draw onto the canvas as it's null")
        } else {
            drawMyStuff(canvas)
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMyStuff(canvas: Canvas) {
        canvas.drawBitmap(rgbBitmap, null, inputImageRect, null)
    }

    private fun drawGlasses(
        canvas: Canvas,
        leftEye: Point,
        rightEye: Point,
        topLeftEyePoint: Point,
        topRightEyePoint: Point,
        eyeBrowLeft: Int,
        topNose: Point,
        bottomNose: Point
    ) {
        val eyeAndEyeBrowDistance = (topLeftEyePoint.y - eyeBrowLeft) / 2 * resizeRatio
        val length = (topRightEyePoint.x - topLeftEyePoint.x).toDouble()
        val height = abs(topLeftEyePoint.y - topRightEyePoint.y).toDouble()
        var degrees = toDegrees(tan(height / length)).toFloat()
        if (topLeftEyePoint.y > topRightEyePoint.y) {
            degrees = -degrees
        }
        val topRect = (topNose.y + 90) * resizeRatio - eyeAndEyeBrowDistance
        glassRect.set(
            (leftEye.x * resizeRatio - eyeAndEyeBrowDistance).toInt(),
            topRect.toInt(),
            (rightEye.x * resizeRatio + eyeAndEyeBrowDistance).toInt(),
            (topRect + distanceBetweenPoints(topNose, bottomNose)).toInt()
        )
        canvas.drawRotateCanvas(glassesBitmap, degrees, glassRect)
    }

    private fun Canvas.drawRotateCanvas(bitmap: Bitmap, degrees: Float, rect: Rect) {
        save()
        rotate(degrees, rect.exactCenterX(), rect.exactCenterY())
        drawBitmap(
            bitmap,
            null,
            rect,
            null
        )
        restore()
    }

    private fun distanceBetweenPoints(
        p1: Point,
        p2: Point
    ): Double {
        val ac = abs(p2.y - p1.y)
        val cb = abs(p2.x - p1.x)
        return hypot(ac.toDouble(), cb.toDouble()) * resizeRatio
    }

    private fun drawCigarette(canvas: Canvas, leftMouth: Point, rightMouth: Point) {
        val mouthLength = (rightMouth.x - leftMouth.x) * resizeRatio
        cigaretteRect.set(
            (leftMouth.x * resizeRatio - mouthLength).toInt(),
            ((leftMouth.y + 90) * resizeRatio).toInt(),
            (leftMouth.x * resizeRatio).toInt(),
            ((leftMouth.y + 90) * resizeRatio + mouthLength).toInt()
        )
        canvas.drawBitmap(
            cigaretteBitmap,
            null,
            cigaretteRect,
            null
        )
    }

    companion object {
        private const val INPUT_SIZE = 224
        private const val TAG = "OnGetImageListener"
    }

    init {
        viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                inputImageRect.set(0, 0, right, bottom)
            }
        })
    }
}