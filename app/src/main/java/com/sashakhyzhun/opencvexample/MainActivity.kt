package com.sashakhyzhun.opencvexample

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class MainActivity : Activity(), CvCameraViewListener2 {

    private var mIsColorSelected = false
    private var mRgba: Mat? = null
    private var mBlobColorRgba: Scalar? = null
    private var mBlobColorHsv: Scalar? = null
    private var mDetector: ColorBlobDetector? = null
    private var SPECTRUM_SIZE: Size? = null
    private var CONTOUR_COLOR: Scalar? = null

    private lateinit var colorNameTextView: TextView
    private lateinit var rgbValuesTextView: TextView
    private lateinit var minRgbValuesTextView: TextView
    private lateinit var maxRgbValuesTextView: TextView
    private lateinit var resultButton: Button
    private lateinit var colorLabel: ImageView
    private lateinit var mOpenCvCameraView: CameraBridgeViewBase

    private val handler = Handler()
    private val stopCameraRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Stopping camera feed")
            mOpenCvCameraView.disableView()
            resultButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_light))
            resultButton.isEnabled = true
            displayResults()
        }
    }

    private var startTime: Long = 0
    private var minRgb: Scalar? = null
    private var maxRgb: Scalar? = null

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully")
                    mOpenCvCameraView.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    init {
        Log.i(TAG, "Instantiated new " + this.javaClass)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mOpenCvCameraView = findViewById(R.id.color_blob_detection_activity_surface_view)
        mOpenCvCameraView.visibility = View.VISIBLE
        mOpenCvCameraView.setCvCameraViewListener(this)

        colorNameTextView = findViewById(R.id.color_name)
        rgbValuesTextView = findViewById(R.id.rgb_values)
        minRgbValuesTextView = findViewById(R.id.min_rgb_values)
        maxRgbValuesTextView = findViewById(R.id.max_rgb_values)
        resultButton = findViewById(R.id.result_button)
        colorLabel = findViewById(R.id.colorLabel) // Initialize ImageView for color label

        resultButton.setOnClickListener {
            val intent = Intent(this, ResultsActivity::class.java)
            intent.putExtra("COLOR_NAME", getColorNameFromRgba(mBlobColorRgba!!))
            intent.putExtra("REAL_TIME_RGB", "${mBlobColorRgba!!.`val`[0].toInt()} ${mBlobColorRgba!!.`val`[1].toInt()} ${mBlobColorRgba!!.`val`[2].toInt()}")
            intent.putExtra("MIN_RGB", "${minRgb!!.`val`[0].toInt()} ${minRgb!!.`val`[1].toInt()} ${minRgb!!.`val`[2].toInt()}")
            intent.putExtra("MAX_RGB", "${maxRgb!!.`val`[0].toInt()} ${maxRgb!!.`val`[1].toInt()} ${maxRgb!!.`val`[2].toInt()}")
            startActivity(intent)
        }
    }

    public override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView()
        }
    }

    public override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView()
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        Log.d(TAG, "Camera view started")
        mRgba = Mat(height, width, CvType.CV_8UC4)
        mDetector = ColorBlobDetector()
        mBlobColorRgba = Scalar(255.0)
        mBlobColorHsv = Scalar(255.0)
        SPECTRUM_SIZE = Size(200.0, 64.0)
        CONTOUR_COLOR = Scalar(255.0, 0.0, 0.0, 255.0)
        startTime = System.currentTimeMillis()
        minRgb = Scalar(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        maxRgb = Scalar(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        resultButton.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark))
        resultButton.isEnabled = false
        handler.postDelayed(stopCameraRunnable, 15000) // Stop camera after 15 seconds
    }

    override fun onCameraViewStopped() {
        mRgba!!.release()
        handler.removeCallbacks(stopCameraRunnable) // Remove the callback when the camera is stopped
        Log.d(TAG, "Camera view stopped")
    }

    override fun onCameraFrame(inputFrame: CvCameraViewFrame): Mat {
        if (inputFrame.rgba() == null) {
            Log.e(TAG, "Camera frame is null")
            return Mat() // Return an empty matrix
        }

        mRgba = inputFrame.rgba()

        // Rotate the frame by 90 degrees anticlockwise
        val rotatedRgba = Mat()
        val rotationMatrix = Imgproc.getRotationMatrix2D(
            Point(mRgba!!.cols() / 2.0, mRgba!!.rows() / 2.0),
            270.0,
            1.0
        )
        Imgproc.warpAffine(mRgba!!, rotatedRgba, rotationMatrix, mRgba!!.size())

        // Get the dimensions of the frame
        val cols = rotatedRgba.cols()
        val rows = rotatedRgba.rows()

        // Define the size of the detection rectangle (adjust as needed)
        val frameWidth = 75 // width of the detection rectangle
        val frameHeight = 125 // height of the detection rectangle

        val centerX = cols / 2
        val centerY = rows / 2

        // Calculate the region for detection (only inside the rectangle)
        val detectionRect = Rect(centerX - frameWidth / 2, centerY - frameHeight / 2, frameWidth, frameHeight)

        // Extract the region inside the rectangle
        val touchedRegionRgba = rotatedRgba.submat(detectionRect)
        val touchedRegionHsv = Mat()
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL)

        mBlobColorHsv = Core.sumElems(touchedRegionHsv)
        val pointCount = detectionRect.width * detectionRect.height
        for (i in mBlobColorHsv!!.`val`.indices)
            mBlobColorHsv!!.`val`[i] /= pointCount.toDouble()

        mBlobColorRgba = convertScalarHsv2Rgba(mBlobColorHsv!!)

        mDetector!!.setHsvColor(mBlobColorHsv)
        mIsColorSelected = true

        // Update min and max RGB values
        val currentR = mBlobColorRgba!!.`val`[0]
        val currentG = mBlobColorRgba!!.`val`[1]
        val currentB = mBlobColorRgba!!.`val`[2]

        if (currentR < minRgb!!.`val`[0]) minRgb!!.`val`[0] = currentR
        if (currentG < minRgb!!.`val`[1]) minRgb!!.`val`[1] = currentG
        if (currentB < minRgb!!.`val`[2]) minRgb!!.`val`[2] = currentB

        if (currentR > maxRgb!!.`val`[0]) maxRgb!!.`val`[0] = currentR
        if (currentG > maxRgb!!.`val`[1]) maxRgb!!.`val`[1] = currentG
        if (currentB > maxRgb!!.`val`[2]) maxRgb!!.`val`[2] = currentB

        touchedRegionRgba.release()
        touchedRegionHsv.release()

        // Draw the rectangle on the frame to guide the user
        Imgproc.rectangle(
            rotatedRgba,
            Point((centerX - frameWidth / 2).toDouble(), (centerY - frameHeight / 2).toDouble()),
            Point((centerX + frameWidth / 2).toDouble(), (centerY + frameHeight / 2).toDouble()),
            Scalar(0.0, 255.0, 0.0, 255.0), // Green color with full opacity
            2 // Thickness of the rectangle's outline
        )

        // Process the detection only inside the rectangle
        if (mIsColorSelected) {
            mDetector!!.process(rotatedRgba.submat(detectionRect))
            val contours = mDetector!!.contours
            Imgproc.drawContours(rotatedRgba.submat(detectionRect), contours, -1, CONTOUR_COLOR!!)
        }

        // Update the color name and RGB values only from the rectangle region
        val colorName = getColorNameFromRgba(mBlobColorRgba!!)
        runOnUiThread {
            colorNameTextView.text = "Color: $colorName"
            rgbValuesTextView.text = "R: ${mBlobColorRgba!!.`val`[0].toInt()} G: ${mBlobColorRgba!!.`val`[1].toInt()} B: ${mBlobColorRgba!!.`val`[2].toInt()}"
            minRgbValuesTextView.text = "Min R: ${minRgb!!.`val`[0].toInt()} G: ${minRgb!!.`val`[1].toInt()} B: ${minRgb!!.`val`[2].toInt()}"
            maxRgbValuesTextView.text = "Max R: ${maxRgb!!.`val`[0].toInt()} G: ${maxRgb!!.`val`[1].toInt()} B: ${maxRgb!!.`val`[2].toInt()}"

            // Update the ImageView to show the detected color
            val colorBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            colorBitmap.setPixel(0, 0, Color.rgb(
                mBlobColorRgba!!.`val`[0].toInt(),
                mBlobColorRgba!!.`val`[1].toInt(),
                mBlobColorRgba!!.`val`[2].toInt()
            ))
            colorLabel.setImageBitmap(colorBitmap)
        }

        return rotatedRgba
    }

    private fun convertScalarHsv2Rgba(hsvColor: Scalar): Scalar {
        val pointMatRgba = Mat()
        val pointMatHsv = Mat(1, 1, CvType.CV_8UC3, hsvColor)
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4)
        return Scalar(pointMatRgba.get(0, 0))
    }

    private fun getColorNameFromRgba(rgbaColor: Scalar): String {
        val r = rgbaColor.`val`[0].toInt()
        val g = rgbaColor.`val`[1].toInt()
        val b = rgbaColor.`val`[2].toInt()

        return when {
            (r in 64..69) && (g in 67..72) && (b in 33..36) -> "Unknown(14-15mg/dl)"
            (r in 79..84) && (g in 71..75) && (b in 27..32) -> "Unknown(5-6mg/dl)"
            r > 200 && g < 50 && b < 50 -> "Red"
            r < 50 && g > 200 && b < 50 -> "Green"
            r < 50 && g < 50 && b > 200 -> "Blue"
            r > 200 && g > 200 && b < 50 -> "Yellow"
            r > 200 && g < 50 && b > 200 -> "Magenta"
            r < 50 && g > 200 && b > 200 -> "Cyan"
            r > 200 && g > 200 && b > 200 -> "White"
            r < 50 && g < 50 && b < 50 -> "Black"
            else -> "Unknown"
        }
    }

    private fun displayResults() {
        // Results are updated in the UI thread via onCameraFrame
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

