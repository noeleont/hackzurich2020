package com.huawei.hackzurich

import android.graphics.Point
import android.os.Bundle
import android.util.SparseArray
import android.view.SurfaceView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.valueIterator
import androidx.core.view.drawToBitmap
import androidx.lifecycle.coroutineScope
import com.huawei.hms.mlsdk.common.LensEngine
import com.huawei.hms.mlsdk.common.MLAnalyzer
import com.huawei.hms.mlsdk.common.MLAnalyzer.MLTransactor
import com.huawei.hms.mlsdk.dsc.*
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Double.parseDouble
import java.lang.NumberFormatException

class MainActivity : AppCompatActivity() {

    // TODO: Make analyzer generic
    private lateinit var textAnalyzer: MLTextAnalyzer
    private lateinit var lensEngine: LensEngine
    private lateinit var textView: TextView
    private var maxBlock: MLText.Block? = null

    private fun setupAnalyzer() {
        textAnalyzer = MLTextAnalyzer.Factory(applicationContext).create()
        // TODO: Add callback to constructor
        var processor = OcrDetectorProcessor()
        maxBlock = null
        processor.callback { res -> process(res) }
        textAnalyzer.setTransactor(processor)
    }

    private fun setupLens() {
        lensEngine = LensEngine.Creator(applicationContext, textAnalyzer)
            .setLensType(LensEngine.BACK_LENS)
            .applyDisplayDimension(1440, 1080)
            .applyFps(30.0f)
            .enableAutomaticFocus(true)
            .create()
    }

    private fun showText(res: SparseArray<MLText.Block>): Unit {
        var result = ""
        for (value in res.valueIterator()) {
            try {
                val lines = value?.contents
                for (line in lines!!) {
                    println(line.border)
                    val words = line.contents
                    for (word in words) {
                        result += word.stringValue + " "
                    }
                }
                result += "\n"
            } catch (e: java.lang.Exception) {

            }
        }
    }

    private fun calcMaxBlock(res: SparseArray<MLText.Block?>): Unit {
        for (block in res.valueIterator()) {
            if (block == null) continue
            try {
                val num = parseDouble(block.stringValue)
                val border = block.border
                if (maxBlock == null) {
                    maxBlock = block
                } else if (border.height() > maxBlock!!.border.height()) {
                    maxBlock = block
                }
            } catch (e: NumberFormatException) {
                println("NaN")
            }
        }
        textView.text = maxBlock?.stringValue
    }


    private fun process(res: SparseArray<MLText.Block?>): Unit {
        for (block in res.valueIterator()) {
            if (block == null) continue
            try {
                val num = parseDouble(block.stringValue)
                val border = block.border
                if (maxBlock == null) {
                    maxBlock = block
                } else if (border.height() > maxBlock!!.border.height()) {
                    maxBlock = block
                }
            } catch (e: NumberFormatException) {
                println("NaN")
            }
        }
        textView.text = maxBlock?.stringValue
    }

    private fun setupCheck() {
        val checkStatusTextView = findViewById<TextView>(R.id.main_check)

        lifecycle.coroutineScope.launchWhenCreated {
            try {
                delay(1000)
                runHmsConfigurationCheck()
                checkStatusTextView.text = getString(R.string.checking_setup_result_ok)
            } catch (checkException: Exception) {
                checkStatusTextView.text =
                    getString(R.string.checking_setup_result_fail, checkException.message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupCheck()

        textView = findViewById<TextView>(R.id.textView)

        val setting =
            MLDocumentSkewCorrectionAnalyzerSetting.Factory().create()
        val analyzer =
            MLDocumentSkewCorrectionAnalyzerFactory.getInstance()
                .getDocumentSkewCorrectionAnalyzer(setting)

        analyzer.setTransactor(SkewDetectorProcessor())

        button.setOnClickListener {
            button.text = "Hello"
            setupAnalyzer()
            setupLens()
            val mSurfaceView: SurfaceView = findViewById(R.id.surfaceView2)
            try {
                lensEngine.run(mSurfaceView.holder)
            } catch (e: IOException) {
                //println(e)
                button.text = "Been here"
                // Exception handling logic.
            }
        }

        button2.setOnClickListener {

            if (textAnalyzer != null) {
                try {
                    textAnalyzer.stop()
                } catch (e: IOException) {
                    // Exception handling.
                }
            }
            lensEngine?.release()
            val mSurfaceView: SurfaceView = findViewById(R.id.surfaceView2)
            var canvas = mSurfaceView.holder.lockCanvas()
        }



    }

    private suspend fun runHmsConfigurationCheck() {
        testHmsCorePresence()
        testAccountByRequestingPushNotificationsToken()
    }

    private suspend fun testAccountByRequestingPushNotificationsToken() {
        val pushToken = withContext(Dispatchers.IO) {
            HmsUtils.getPushNotificationsToken(this@MainActivity)
        }
        check(pushToken.isNotEmpty()) { "Push notifications token retrieved, but empty. Clear app data and try again." }
    }

    private fun testHmsCorePresence() {
        check(HmsUtils.isHmsAvailable(this)) { "Please make sure you have HMS Core installed on the test device." }
    }
}


class OcrDetectorProcessor : MLTransactor<MLText.Block?> {

    private lateinit var op: (res: SparseArray<MLText.Block?>) -> Unit
    override fun transactResult(results: MLAnalyzer.Result<MLText.Block?>) {
        this.op(results.analyseList)

        //println(items.get(0)?.stringValue)
        // Determine detection result processing as required. Note that only the detection results are processed.
        // Other detection-related APIs provided by ML Kit cannot be called.
    }

    override fun destroy() {
        // Callback method used to release resources when the detection ends.
    }

    fun callback(op: (SparseArray<MLText.Block?>) -> Unit) {
        this.op = { res: SparseArray<MLText.Block?> -> op(res) }
    }
}

class SkewDetectorProcessor : MLTransactor<MLDocumentSkewDetectResult?> {
    override fun transactResult(results: MLAnalyzer.Result<MLDocumentSkewDetectResult?>) {
        val items = results.analyseList

        if (items  != null && items.get(0)?.resultCode == MLDocumentSkewCorrectionConstant.SUCCESS) {
// Detection success.
            val detectResult: MLDocumentSkewDetectResult = items.get(0)!!
            val leftTop: Point = detectResult.leftTopPosition
            val rightTop: Point = detectResult.rightTopPosition
            val leftBottom: Point = detectResult.leftBottomPosition
            val rightBottom: Point = detectResult.rightBottomPosition
            val coordinates: MutableList<Point> = ArrayList()
            coordinates.add(leftTop)
            coordinates.add(rightTop)
            coordinates.add(rightBottom)
            coordinates.add(leftBottom)
            val coordinateData =
                MLDocumentSkewCorrectionCoordinateInput(coordinates as List<Point>?)
            println("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            println()

        } else {
// Detection failure.
        }

        //println(items.get(0)?.stringValue)
        // Determine detection result processing as required. Note that only the detection results are processed.
        // Other detection-related APIs provided by ML Kit cannot be called.
    }

    override fun destroy() {
        // Callback method used to release resources when the detection ends.
    }
}