package com.huawei.hackzurich

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.util.valueIterator
import androidx.core.view.drawToBitmap
import com.huawei.hms.mlsdk.common.LensEngine
import com.huawei.hms.mlsdk.common.LensEngine.PhotographListener
import com.huawei.hms.mlsdk.common.MLAnalyzer
import com.huawei.hms.mlsdk.common.MLAnalyzer.MLTransactor
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import kotlinx.android.synthetic.main.activity_scan.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.Double.parseDouble
import java.util.*

public class LiveScanActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG: String = "LiveScanActivity"

    private val CAMERA_PERMISSION_CODE = 0

    private var analyzer: MLTextAnalyzer? = null

    private var mLensEngine: LensEngine? = null

    private var isStarted = true

    private var mPreview: LensEnginePreview? = null

    private var mOverlay: GraphicOverlay? = null

    private var lensType = LensEngine.BACK_LENS

    var mlsNeedToDetect = true

    private val STOP_PREVIEW = 1

    private val START_PREVIEW = 2

    private val SAVE_IMAGE = 3

    private var lastPicture: Bitmap? = null
    private var sum = 0.0

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

        val redo:Button = findViewById(R.id.redo)
        redo.setOnClickListener(this)

        val detect_start:Button = findViewById(R.id.detect_start)
        detect_start.setOnClickListener(this)

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

    private fun saveToGallery(context: Context, byteArray: ByteArray, albumName: String) {
        val filename = "${System.currentTimeMillis()}.png"
        val write: (OutputStream) -> Unit = {
            it.write(byteArray)
            //bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$albumName")
            }
            val dbHelper = DataBaseHelper(context)

            context.contentResolver.let {
                it.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                    it.openOutputStream(uri)?.let(write)
                }
            }
            // Gets the data repository in write mode
            val db = dbHelper.writableDatabase

// Create a new map of values, where column names are the keys
            val values = ContentValues().apply {
                put(TableInfo.IMAGE, "${Environment.DIRECTORY_DCIM}/$albumName/$filename")
                put(TableInfo.TOTAL, sum)
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()

// Insert the new row, returning the primary key value of the new row
            val newRowId = db?.insert(TableInfo.DB_NAME, null, values)
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + File.separator + albumName
            val file = File(imagesDir)
            if (!file.exists()) {
                file.mkdir()
            }
            val image = File(imagesDir, filename)
            write(FileOutputStream(image))
        }
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
                SAVE_IMAGE -> {
                    mlsNeedToDetect = true
                    Toast.makeText(applicationContext, "Saved", Toast.LENGTH_SHORT).show()
                    startPreview()
                    //mLensEngine?.photograph(null, PhotographListener {
                    //    saveToGallery(applicationContext, it, "smokinglevy")
                        // This method is called when the photographing is complete.
                        // The data parameter and the corresponding image data can be obtained here for further processing.
                    //})
                }
                else -> {
                }
            }
        }
    }

    private fun stopPreview() {
        mlsNeedToDetect = false
        if (mLensEngine != null) {
            //mLensEngine!!.release()
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
        redo.alpha = 0F
        mOverlay?.lastClick = null

        val detect_start:Button = findViewById(R.id.detect_start)
        detect_start.isEnabled = true
        createObjectAnalyzer()

        mPreview?.release()
        createLensEngine()
        startLensEngine()
        isStarted = true
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.redo -> mHandler.sendEmptyMessage(START_PREVIEW)
            R.id.detect_start -> mHandler.sendEmptyMessage(SAVE_IMAGE)
        }


    }

    private fun createObjectAnalyzer() {
/*
        // Create a language set.
        val languageList = listOf<String>("de", "en", "fr")
        // Set parameters.
        val setting: MLRemoteTextSetting = MLRemoteTextSetting.Factory() // Set the on-cloud text detection mode.
            // MLRemoteTextSetting.OCR_COMPACT_SCENE: dense text recognition
            // MLRemoteTextSetting.OCR_LOOSE_SCENE: sparse text recognition
            .setTextDensityScene(MLRemoteTextSetting.OCR_LOOSE_SCENE) // Specify the languages that can be recognized, which should comply with ISO 639-1.
            .setLanguageList(languageList) // Set the format of the returned text border box.
            // MLRemoteTextSetting.NGON: Return the coordinates of the four corner points of the quadrilateral.
            // MLRemoteTextSetting.ARC: Return the corner points of a polygon border in an arc. The coordinates of up to 72 corner points can be returned.
            .setBorderType(MLRemoteTextSetting.ARC)
            .create()

        analyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(setting)
*/
        analyzer = MLTextAnalyzer.Factory(applicationContext).create()
        analyzer?.setTransactor(object : MLTransactor<MLText.Block?> {
            override fun destroy() {}
            override fun transactResult(result: MLAnalyzer.Result<MLText.Block?>) {
                if (!mlsNeedToDetect) {
                    return
                }
                mOverlay?.clear()
                val objectSparseArray = result.analyseList
                for (block in result.analyseList.valueIterator()) {
                    val lastClick = mOverlay?.lastClick
                    try {
                        if (lastClick != null && block!!.border.contains(lastClick.x, lastClick.y)) {
                            sum += parseDouble(block!!.stringValue)
                            total_view.text = "Total: \n $sum"
                            redo.alpha = 1F
                            mHandler.sendEmptyMessage(STOP_PREVIEW)
                        }
                    } catch (e: Exception) {
                        mOverlay?.lastClick = null
                    }

                    val graphic = MLTextGraphic(
                        mOverlay,
                        block
                    )
                    mOverlay?.add(graphic)

                }
                // When you need to implement a scene that stops after recognizing specific content
                // and continues to recognize after finishing processing, refer to this code
                /*for (i in 0 until objectSparseArray.size()) {
                    if (objectSparseArray.valueAt(i).typeIdentity == MLObject.TYPE_FOOD) {
                        mlsNeedToDetect = true
                        mHandler.sendEmptyMessage(STOP_PREVIEW)
                    }
                }*/
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