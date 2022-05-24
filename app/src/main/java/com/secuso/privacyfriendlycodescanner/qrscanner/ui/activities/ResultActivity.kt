package com.secuso.privacyfriendlycodescanner.qrscanner.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.zxing.client.result.ParsedResult
import com.google.zxing.client.result.ParsedResultType
import com.journeyapps.barcodescanner.BarcodeResult
import com.secuso.privacyfriendlycodescanner.qrscanner.R
import com.secuso.privacyfriendlycodescanner.qrscanner.database.HistoryItem
import com.secuso.privacyfriendlycodescanner.qrscanner.ui.activities.ResultActivity
import com.secuso.privacyfriendlycodescanner.qrscanner.ui.resultfragments.*
import com.secuso.privacyfriendlycodescanner.qrscanner.ui.viewmodel.ResultViewModel

/**
 * This activity displays the results of scan. Either from the history or from a scan directly.<br></br>
 *
 *
 * Use the method [.startResultActivity] if called from a scan.<br></br>
 * Use the method [.startResultActivity] if called from the history.
 *
 * @author Christopher Beckmann
 * @see HistoryActivity
 *
 * @see ScannerActivity
 */
class ResultActivity : AppCompatActivity() {
    private var proceedButton: Button? = null
    private var viewModel: ResultViewModel? = null
    private var currentResultFragment: ResultFragment? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        proceedButton = findViewById(R.id.btnProceed)
        viewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        initStateIfNecessary(savedInstanceState)
        val ab = supportActionBar
        ab?.setDisplayHomeAsUpEnabled(true)
        if (isFinishing) {
            return
        }
        loadFragment(viewModel!!.mParsedResult)
        displayGeneralData()
    }

    /**
     * After this function is called the the following values will not be null:
     *
     *  * currentHistoryItem
     *  * mCodeImage
     *  * mSavedToHistory
     *  * mParsedResult
     *
     * If the state can not be created the activity will call [AppCompatActivity.finish]
     * This method will also update the [HistoryItem] in the database with a recreation of the QR Code if the image is missing.
     * @param savedInstanceState is the bundle that is given to the [.onCreate] or [.onRestoreInstanceState] Methods
     */
    private fun initStateIfNecessary(savedInstanceState: Bundle?) {
        val hasHistoryItem = intent.getBooleanExtra(HISTORY_DATA, false)
        if (savedInstanceState == null) {
            if (hasHistoryItem && historyItem != null) {
                viewModel!!.initFromHistoryItem(historyItem)
            } else if (barcodeResult != null) {
                viewModel!!.initFromScan(barcodeResult)
            } else {
                // no data to display -> exit
                Toast.makeText(
                    this,
                    R.string.activity_result_toast_error_cant_load,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.share, menu)
        menuInflater.inflate(R.menu.copy, menu)
        menuInflater.inflate(R.menu.save, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val saveMi = menu.findItem(R.id.save)
        if (saveMi != null) {
            saveMi.isVisible = !viewModel!!.mSavedToHistory
        }
        return true
    }

    private fun displayGeneralData() {
        val qrImageView = findViewById<ImageView>(R.id.activity_result_qr_image)
        val qrTypeText = findViewById<TextView>(R.id.textView)
        Glide.with(this).load(viewModel!!.mCodeImage).into(qrImageView)
        var type = viewModel!!.mParsedResult.type.name
        type = when (viewModel!!.mParsedResult.type) {
            ParsedResultType.URI -> getString(R.string.activity_result_type_uri)
            ParsedResultType.ADDRESSBOOK, ParsedResultType.EMAIL_ADDRESS, ParsedResultType.PRODUCT, ParsedResultType.GEO, ParsedResultType.TEL, ParsedResultType.WIFI, ParsedResultType.SMS, ParsedResultType.CALENDAR, ParsedResultType.ISBN, ParsedResultType.VIN, ParsedResultType.TEXT ->                 //type = getString(R.string.activity_result_type_text);
                viewModel!!.mParsedResult.type.name
            else -> viewModel!!.mParsedResult.type.name
        }
        qrTypeText.text = type
    }

    fun onClick(view: View) {
        if (view.id == R.id.btnProceed) {
            if (currentResultFragment != null) {
                currentResultFragment!!.onProceedPressed(this)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share -> {
                val sharingIntent = Intent(Intent.ACTION_SEND)
                sharingIntent.type = "text/plain"
                sharingIntent.putExtra(Intent.EXTRA_TEXT, viewModel!!.mParsedResult.displayResult)
                startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)))
                true
            }
            R.id.save -> {
                viewModel!!.saveHistoryItem(viewModel!!.currentHistoryItem)
                invalidateOptionsMenu()
                Toast.makeText(this, R.string.activity_result_toast_saved, Toast.LENGTH_SHORT)
                    .show()
                true
            }
            R.id.copy -> {
                val clipboardManager =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clipData =
                    ClipData.newPlainText("Text", viewModel!!.mParsedResult.displayResult)
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(applicationContext, R.string.content_copied, Toast.LENGTH_SHORT)
                    .show()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadFragment(parsedResult: ParsedResult) {
        val ft = supportFragmentManager.beginTransaction()
        val resultFragment: ResultFragment
        when (parsedResult.type) {
            ParsedResultType.ADDRESSBOOK -> resultFragment = ContactResultFragment()
            ParsedResultType.EMAIL_ADDRESS -> resultFragment = EmailResultFragment()
            ParsedResultType.PRODUCT -> resultFragment = ProductResultFragment()
            ParsedResultType.URI -> resultFragment = URLResultFragment()
            ParsedResultType.GEO -> resultFragment = GeoResultFragment()
            ParsedResultType.TEL -> resultFragment = TelResultFragment()
            ParsedResultType.SMS -> resultFragment = SMSResultFragment()
            ParsedResultType.WIFI -> resultFragment = WifiResultFragment()
            ParsedResultType.ISBN, ParsedResultType.VIN, ParsedResultType.CALENDAR, ParsedResultType.TEXT -> {
                resultFragment = TextResultFragment()

                // hide "search" button if search engines are disabled
                if (!PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("pref_search_engine_enabled", true)
                ) {
                    proceedButton!!.visibility = View.GONE
                }
            }
            else -> {
                resultFragment = TextResultFragment()
                if (!PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("pref_search_engine_enabled", true)
                ) {
                    proceedButton!!.visibility = View.GONE
                }
            }
        }
        currentResultFragment = resultFragment
        resultFragment.putQRCode(parsedResult)
        ft.replace(R.id.activity_result_frame_layout, resultFragment)
        ft.commit()
        proceedButton!!.text = resultFragment.getProceedButtonTitle(this)
    }

    companion object {
        private const val HISTORY_DATA = "ResultActivity.HISTORY_DATA"
        private var barcodeResult: BarcodeResult? = null
        private var historyItem: HistoryItem? = null
        fun startResultActivity(context: Context, barcodeResult: BarcodeResult) {
            Companion.barcodeResult = barcodeResult
            historyItem = null
            val resultIntent = Intent(context, ResultActivity::class.java)
            context.startActivity(resultIntent)
        }

        @JvmStatic
        fun startResultActivity(context: Context, historyItem: HistoryItem) {
            barcodeResult = null
            Companion.historyItem = historyItem
            val resultIntent = Intent(context, ResultActivity::class.java)
            resultIntent.putExtra(HISTORY_DATA, true)
            context.startActivity(resultIntent)
        }
    }
}