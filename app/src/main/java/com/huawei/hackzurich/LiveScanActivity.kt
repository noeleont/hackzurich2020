package com.huawei.hackzurich

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.LensEngine
import com.huawei.hms.mlsdk.common.MLAnalyzer
import com.huawei.hms.mlsdk.common.MLAnalyzer.MLTransactor
import com.huawei.hms.mlsdk.objects.MLObject
import com.huawei.hms.mlsdk.objects.MLObjectAnalyzer
import com.huawei.hms.mlsdk.objects.MLObjectAnalyzerSetting
import java.io.IOException
import java.util.*
import com.huawei.hackzurich.R
import com.huawei.hackzurich.GraphicOverlay
import com.huawei.hackzurich.LensEnginePreview
import com.huawei.hackzurich.MLObjectGraphic

public class LiveScanActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG: String = "LiveScanActivity"

    private val CAMERA_PERMISSION_CODE = 0

    private var analyzer: MLObjectAnalyzer? = null

    private var mLensEngine: LensEngine? = null

    private var isStarted = true

    private var mPreview: LensEnginePreview? = null

    private var mOverlay: GraphicOverlay? = null

    private var lensType = LensEngine.BACK_LENS

    var mlsNeedToDetect = true

    private val STOP_PREVIEW = 1

    private val START_PREVIEW = 2

    private var isPermissionRequested = false

    private val ALL_PERMISSION = arrayOf(
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_scan)
        if (savedInstanceState != null) {
            lensType = savedInstanceState.getInt("lensType")
        }
        mPreview = findViewById(R.id.object_preview)
        mOverlay = findViewById(R.id.object_overlay)
        createObjectAnalyzer()
        val start =
            findViewById<Button>(R.id.detect_start)
        start.setOnClickListener(this)
        // Checking Camera Permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            createLensEngine()
        } else {
            this.checkPermission()
        }
    }

    private fun requestCameraPermission() {
        val permissions =
            arrayOf(Manifest.permission.CAMERA)
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            )
        ) {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                CAMERA_PERMISSION_CODE
            )
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            createLensEngine()
            startLensEngine()
        } else {
            this.checkPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        mPreview?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mLensEngine != null) {
            mLensEngine!!.release()
        }
        if (analyzer != null) {
            try {
                analyzer!!.stop()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Stop failed: " + e.message
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        var hasAllGranted = true
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createLensEngine()
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                hasAllGranted = false
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        permissions[0]!!
                    )
                ) {
                    showWaringDialog()
                } else {
                    Toast.makeText(this, "Test text", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putInt("lensType", lensType)
        super.onSaveInstanceState(savedInstanceState)
    }

    // When you need to implement a scene that stops after recognizing specific content
    // and continues to recognize after finishing processing, refer to this code
    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                START_PREVIEW -> {
                    mlsNeedToDetect = true
                    Log.d("object", "start to preview")
                    startPreview()
                }
                STOP_PREVIEW -> {
                    mlsNeedToDetect = false
                    Log.d("object", "stop to preview")
                    stopPreview()
                }
                else -> {
                }
            }
        }
    }

    private fun stopPreview() {
        mlsNeedToDetect = false
        if (mLensEngine != null) {
            mLensEngine!!.release()
        }
        if (analyzer != null) {
            try {
                analyzer!!.stop()
            } catch (e: IOException) {
                Log.d("object", "Stop failed: " + e.message)
            }
        }
        isStarted = false
    }

    private fun startPreview() {
        if (isStarted) {
            return
        }
        createObjectAnalyzer()
        mPreview?.release()
        createLensEngine()
        startLensEngine()
        isStarted = true
    }

    override fun onClick(v: View?) {
        mHandler.sendEmptyMessage(START_PREVIEW)
    }

    private fun createObjectAnalyzer() {
        // Create an object analyzer
        // Use MLObjectAnalyzerSetting.TYPE_VIDEO for video stream detection.
        // Use MLObjectAnalyzerSetting.TYPE_PICTURE for static image detection.
        val setting =
            MLObjectAnalyzerSetting.Factory().setAnalyzerType(MLObjectAnalyzerSetting.TYPE_VIDEO)
                .allowMultiResults()
                .allowClassification()
                .create()
        analyzer = MLAnalyzerFactory.getInstance().getLocalObjectAnalyzer(setting)
        analyzer?.setTransactor(object : MLTransactor<MLObject> {
            override fun destroy() {}
            override fun transactResult(result: MLAnalyzer.Result<MLObject>) {
                if (!mlsNeedToDetect) {
                    return
                }
                mOverlay?.clear()
                val objectSparseArray = result.analyseList
                for (i in 0 until objectSparseArray.size()) {
                    val graphic = MLObjectGraphic(
                        mOverlay,
                        objectSparseArray.valueAt(i)
                    )
                    mOverlay?.add(graphic)
                }
                // When you need to implement a scene that stops after recognizing specific content
                // and continues to recognize after finishing processing, refer to this code
                for (i in 0 until objectSparseArray.size()) {
                    if (objectSparseArray.valueAt(i).typeIdentity == MLObject.TYPE_FOOD) {
                        mlsNeedToDetect = true
                        mHandler.sendEmptyMessage(STOP_PREVIEW)
                    }
                }
            }
        })
    }

    private fun createLensEngine() {
        val context = this.applicationContext
        // Create LensEngine
        mLensEngine = LensEngine.Creator(context, analyzer).setLensType(lensType)
            .applyDisplayDimension(640, 480)
            .applyFps(25.0f)
            .enableAutomaticFocus(true)
            .create()
    }

    private fun startLensEngine() {
        if (mLensEngine != null) {
            try {
                mPreview?.start(mLensEngine, mOverlay)
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "Failed to start lens engine.",
                    e
                )
                mLensEngine!!.release()
                mLensEngine = null
            }
        }
    }

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !isPermissionRequested) {
            isPermissionRequested = true
            val permissionsList =
                ArrayList<String>()
            for (perm in getAllPermission()) {
                if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(perm)) {
                    permissionsList.add(perm)
                }
            }
            if (!permissionsList.isEmpty()) {
                requestPermissions(permissionsList.toTypedArray(), 0)
            }
        }
    }

    private fun getAllPermission(): List<String> {
        return Collections.unmodifiableList(
            Arrays.asList(
                *ALL_PERMISSION
            )
        )
    }

    private fun showWaringDialog() {
        val dialog =
            AlertDialog.Builder(this)
        dialog.setMessage(R.string.Information_permission)
            .setPositiveButton(R.string.go_authorization,
                DialogInterface.OnClickListener { dialog, which -> //Guide the user to the setting page for manual authorization.
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts(
                        "package",
                        applicationContext.packageName,
                        null
                    )
                    intent.data = uri
                    startActivity(intent)
                })
            .setNegativeButton("Cancel",
                DialogInterface.OnClickListener { dialog, which -> //Instruct the user to perform manual authorization. The permission request fails.
                    finish()
                }).setOnCancelListener(DialogInterface.OnCancelListener {
                //Instruct the user to perform manual authorization. The permission request fails.
            })
        dialog.setCancelable(false)
        dialog.show()
    }
}