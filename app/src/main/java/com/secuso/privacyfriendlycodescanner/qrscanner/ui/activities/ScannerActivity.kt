package com.secuso.privacyfriendlycodescanner.qrscanner.ui.activities

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.navigation.NavigationView
import com.google.zxing.*
import com.google.zxing.client.android.BeepManager
import com.google.zxing.client.android.Intents
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.*
import com.journeyapps.barcodescanner.CameraPreview.StateListener
import com.secuso.privacyfriendlycodescanner.qrscanner.R
import com.secuso.privacyfriendlycodescanner.qrscanner.ui.helpers.BaseActivity
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*


/**
 * Handles the scanning action as well as asking for the camera permission if needed.
 * If a scan was sucessfully completed the result is passed to the [ResultActivity].
 *
 * @author Christopher Beckmann
 */
const val REQUEST_CODE_PICK = 1000

class ScannerActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    // UI
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private var permissionNeededExplanation: TextView? = null
    private var flashOnButton: MenuItem? = null
    private var flashOffButton: MenuItem? = null

    // Logic
    private var beepManager: BeepManager? = null
    private val stateListener: StateListener = object : StateListener {
        override fun previewSized() {}
        override fun previewStarted() {}
        override fun previewStopped() {}
        override fun cameraError(error: Exception) {}
        override fun cameraClosed() {}
    }
    private val callback: BarcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val contents = result.toString()
            if (contents.isEmpty()) {
                return
            }
            barcodeScannerView.setStatusText(result.text)
            beepManager!!.playBeepSoundAndVibrate()
            ResultActivity.startResultActivity(this@ScannerActivity, result)
            //            Intent resultIntent = new Intent(ScannerActivity.this, ResultActivity.class);
//            resultIntent.putExtra("QRResult", new ParcelableResultDecorator(result.getResult()), result.getBitmapWithResultPoints());
//            startActivity(resultIntent);
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        val preferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
        permissionNeededExplanation =
            findViewById(R.id.activity_scanner_permission_needed_explanation)
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)
        barcodeScannerView.barcodeView.addStateListener(stateListener)
        beepManager = BeepManager(this)
        if (!preferences.getBoolean("pref_enable_beep_on_scan", true)) {
            beepManager!!.isBeepEnabled = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initScanWithPermissionCheck()
        } else {
            initScan()
        }
    }

    @TargetApi(23)
    private fun initScanWithPermissionCheck() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_CAMERA_REQUEST
            )
            showCameraPermissionRequirement(true)
        } else {
            initScan()
        }
    }

    private fun showCameraPermissionRequirement(show: Boolean) {
        barcodeScannerView.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            barcodeScannerView.pause()
        } else {
            barcodeScannerView.resume()
        }
        permissionNeededExplanation!!.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun initScan() {
        showCameraPermissionRequirement(false)
        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val intent = intent
        if (!intent.hasExtra(Intents.Scan.SCAN_TYPE)) {
            intent.putExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        }
        barcodeScannerView.setTorchListener(TorchListener(this))
        val formats: Collection<BarcodeFormat> =
            listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_39)
        barcodeScannerView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeScannerView.initializeFromIntent(intent)
        barcodeScannerView.decodeSingle(callback)
        barcodeScannerView.resume()
    }

    override fun getNavigationDrawerID(): Int {
        return R.id.nav_scan
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showCameraPermissionRequirement(true)
        } else {
            barcodeScannerView.setStatusText(null)
            initScan()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeScannerView.pauseAndWait()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        //outState.putInt(SAVED_ORIENTATION_LOCK, this.orientationLock);
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initScan()
            } else {
                // TODO
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.flashlight, menu)
        flashOnButton = menu.findItem(R.id.menu_flashlight_on)
        flashOffButton = menu.findItem(R.id.menu_flashlight_off)
        barcodeScannerView.setTorchOff()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_flashlight_on -> {
                barcodeScannerView.setTorchOn()
                true
            }
            R.id.menu_flashlight_off -> {
                barcodeScannerView.setTorchOff()
                true
            }
            R.id.menu_select_image -> {
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                this.startActivityForResult(intent, REQUEST_CODE_PICK)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun readQRImage(bitmap: Bitmap): Result? {
        val intArray = IntArray(bitmap.width * bitmap.height)
        //copy pixel data from the Bitmap into the 'intArray' array
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader: Reader = MultiFormatReader() // use this otherwise ChecksumException
        try {
            //byte[] rawBytes = result.getRawBytes();
            //BarcodeFormat format = result.getBarcodeFormat();
            //ResultPoint[] points = result.getResultPoints();
            return reader.decode(binaryBitmap)
        } catch (e: NotFoundException) {
            e.printStackTrace()
        } catch (e: ChecksumException) {
            e.printStackTrace()
        } catch (e: FormatException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getRgbList(bitmap: Bitmap): List<Int> {
        val rgbList = mutableListOf<Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val colour = bitmap.getPixel(x, y)
                val red = Color.red(colour)
                val green = Color.green(colour)
                val blue = Color.blue(colour)
                rgbList.add(red)
                rgbList.add(green)
                rgbList.add(blue)
            }
        }
        return rgbList
    }

    private fun getRgbIntArray(bitmap: Bitmap): IntArray {
        return getRgbList(bitmap).toIntArray()
    }

    private fun getRgbByteArray(bitmap: Bitmap): ByteArray {
        val rgbList = mutableListOf<Byte>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val colour: Int = bitmap.getPixel(x, y)
                val red = Color.red(colour)
                val green = Color.green(colour)
                val blue = Color.blue(colour)
//                val alpha = Color.alpha(colour)
                rgbList.add(red.toByte())
                rgbList.add(green.toByte())
                rgbList.add(blue.toByte())
//                rgbList.add(alpha)
            }
        }
        return rgbList.toByteArray()
    }

    private fun decodeQrImage(bitmap: Bitmap): Result {
        val bin = HybridBinarizer(
            RGBLuminanceSource(
                bitmap.width,
                bitmap.height,
                getRgbIntArray(bitmap)
            )
        )
        val binaryBitmap = BinaryBitmap(bin)

        val qrCodeReader = QRCodeReader()
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
        hints[DecodeHintType.TRY_HARDER] = true
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf() { BarcodeFormat.QR_CODE }

        return qrCodeReader.decode(binaryBitmap, hints)
    }

    fun bitMatrix2BitMap(bitMatrix: BitMatrix): Bitmap {
        val height = bitMatrix.height
        val width = bitMatrix.width
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun getBitmapBytes(bitmap: Bitmap): ByteArray {
        val byteBuffer = ByteBuffer.allocate(bitmap.rowBytes * bitmap.height)
        byteBuffer.rewind()
        bitmap.copyPixelsToBuffer(byteBuffer)
        byteBuffer.position()
        return byteBuffer.array()
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val a = argb[index] and -0x1000000 shr 24 // a is not used obviously
                val r = argb[index] and 0xff0000 shr 16
                val g = argb[index] and 0xff00 shr 8
                val b = argb[index] and 0xff shr 0

                // well known RGB to YUV algorithm
                val y = (66 * r + 129 * g + 25 * b + 128 shr 8) + 16
                val u = (-38 * r - 74 * g + 112 * b + 128 shr 8) + 128
                val v = (112 * r - 94 * g - 18 * b + 128 shr 8) + 128

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                // pixel AND every other scanline.
                yuv420sp[yIndex++] = (if (y < 0) 0 else if (y > 255) 255 else y).toByte()
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (v < 0) 0 else if (v > 255) 255 else v).toByte()
                    yuv420sp[uvIndex++] = (if (u < 0) 0 else if (u > 255) 255 else u).toByte()
                }
                index++
            }
        }
    }

    fun getNV21(inputWidth: Int, inputHeight: Int, scaled: Bitmap): ByteArray {
        val argb = IntArray(inputWidth * inputHeight)
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight)
        scaled.recycle()
        return yuv
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_PICK) {
            val uri = data?.data ?: return
//            val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            val imageInputStream = contentResolver.openInputStream(uri)
            val bytes = imageInputStream!!.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val result: Result?
            try {
                result = readQRImage(bitmap)
            } catch (e: NotFoundException) {
                Toast.makeText(applicationContext, "Can't Parse QR", Toast.LENGTH_SHORT).show()
                return
            }
            if (result == null) return

            val sourceData = SourceData(
                getNV21(bitmap.width, bitmap.height, bitmap),
//                bitmap2Bytes(bitmap),
//                getRgbByteArray(bitmap),
                bitmap.width,
                bitmap.height,
                ImageFormat.NV21,
                0
            )
            sourceData.cropRect = Rect(0, 0, 0, 0)

            val barcodeResult = BarcodeResult(result, sourceData)


            barcodeScannerView.setStatusText(result.text)
            beepManager!!.playBeepSoundAndVibrate()
            ResultActivity.startResultActivity(this, barcodeResult)
            Log.i(this.localClassName, uri.toString())
        }
    }

    fun onClick(view: View) {
        when (view.id) {
            R.id.activity_scanner_permission_needed_container -> initScanWithPermissionCheck()
            else -> {
            }
        }
    }

    internal inner class TorchListener(parent: ScannerActivity) :
        DecoratedBarcodeView.TorchListener {
        var mParent: WeakReference<ScannerActivity>
        override fun onTorchOn() {
            val parent = mParent.get()
            if (parent != null) {
                parent.flashOnButton!!.isVisible = false
                parent.flashOffButton!!.isVisible = true
            }
        }

        override fun onTorchOff() {
            val parent = mParent.get()
            if (parent != null) {
                parent.flashOnButton!!.isVisible = true
                parent.flashOffButton!!.isVisible = false
            }
        }

        init {
            mParent = WeakReference(parent)
        }
    }

    companion object {
        private const val PERMISSION_CAMERA_REQUEST = 0
    }
}