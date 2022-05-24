package com.secuso.privacyfriendlycodescanner.qrscanner.ui.resultfragments

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build


class WifiUtils(context: Context) {
    private var wifiManager: WifiManager
    private var connectivityManager: ConnectivityManager

    fun connectToWifi(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val wifiConfig = WifiConfiguration()
                wifiConfig.SSID = "\"" + ssid + "\""
                wifiConfig.preSharedKey = "\"" + password + "\""
                val netId = wifiManager.addNetwork(wifiConfig)
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onUnavailable() {
                    super.onUnavailable()
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)

                }

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)

                }
            }
            connectivityManager.requestNetwork(networkRequest, networkCallback)
        }
    }

    init {
        wifiManager = context.applicationContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager
        connectivityManager = context.applicationContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
    }
}