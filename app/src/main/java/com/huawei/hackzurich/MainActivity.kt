package com.huawei.hackzurich

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.os.Bundle
import android.util.SparseArray
import android.view.SurfaceView
import android.view.View
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

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private fun setupCheck() {
        lifecycle.coroutineScope.launchWhenCreated {
            try {
                delay(1000)
                runHmsConfigurationCheck()
            } catch (checkException: Exception) {

            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_scan -> {
                this.startActivity(Intent(this, LiveScanActivity::class.java))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupCheck()
        val dbHelper = DataBaseHelper(this)
        val db: SQLiteDatabase = dbHelper.writableDatabase

        btn_scan.setOnClickListener(this)
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