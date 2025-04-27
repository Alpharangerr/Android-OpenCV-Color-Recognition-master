package com.sashakhyzhun.opencvexample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class ResultsActivity : Activity() {

    private lateinit var colorNameTextView: TextView
    private lateinit var rgbValuesTextView: TextView
    private lateinit var minRgbValuesTextView: TextView
    private lateinit var maxRgbValuesTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        colorNameTextView = findViewById(R.id.color_name)
        rgbValuesTextView = findViewById(R.id.rgb_values)
        minRgbValuesTextView = findViewById(R.id.min_rgb_values)
        maxRgbValuesTextView = findViewById(R.id.max_rgb_values)

        val colorName = intent.getStringExtra("COLOR_NAME") ?: "Unknown"
        val realTimeRgb = intent.getStringExtra("REAL_TIME_RGB") ?: "0 0 0"
        val minRgb = intent.getStringExtra("MIN_RGB") ?: "0 0 0"
        val maxRgb = intent.getStringExtra("MAX_RGB") ?: "0 0 0"

        colorNameTextView.text = "Color: $colorName"
        rgbValuesTextView.text = "Real-time RGB: $realTimeRgb"
        minRgbValuesTextView.text = "Min RGB: $minRgb"
        maxRgbValuesTextView.text = "Max RGB: $maxRgb"
    }
}

