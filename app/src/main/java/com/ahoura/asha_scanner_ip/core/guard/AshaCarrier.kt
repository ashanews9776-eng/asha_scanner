package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

/**
 * Carrier classification for cellular and fixed networks.
 * Hamrah Aval (MCI, MCC 43211) blocks UDP WireGuard and HTTP/3 entirely;
 * it requires MASQUE over HTTP/2 TCP + TLS ClientHello fragmentation.
 */
object AshaCarrier {

    fun isMci(context: Context): Boolean = carrierClass(context) == "mci"

    fun carrierClass(context: Context): String {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivity?.activeNetwork
        val caps = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }

        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        if (isWifi || isEthernet) {
            // Wi-Fi or Ethernet: verify SIM operator as well, in case user is on hotspot or cellular Wi-Fi modem
            val simCarrier = checkTelephony(context)
            if (simCarrier != "unknown") return simCarrier
            return "fixed"
        }

        if (isCellular) {
            return checkTelephony(context)
        }

        return checkTelephony(context)
    }

    private fun checkTelephony(context: Context): String {
        val tm = resolveActiveTelephony(context) ?: return "unknown"
        val simOp = runCatching { tm.simOperator.orEmpty() }.getOrDefault("")
        val netOp = runCatching { tm.networkOperator.orEmpty() }.getOrDefault("")
        val simName = runCatching { tm.simOperatorName.orEmpty() }.getOrDefault("").lowercase()
        val netName = runCatching { tm.networkOperatorName.orEmpty() }.getOrDefault("").lowercase()

        return when {
            simOp == "43211" || netOp == "43211" ||
                listOf("mci", "ir-mci", "hamrah", "mobile communication company of iran").any {
                    simName.contains(it) || netName.contains(it)
                } -> "mci"

            simOp == "43235" || netOp == "43235" ||
                listOf("irancell", "mtn", "iran cell").any {
                    simName.contains(it) || netName.contains(it)
                } -> "irancell"

            simOp == "43220" || netOp == "43220" ||
                listOf("rightel", "right tel").any {
                    simName.contains(it) || netName.contains(it)
                } -> "rightel"

            else -> "unknown"
        }
    }

    private fun resolveActiveTelephony(context: Context): TelephonyManager? {
        val defaultTm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val dataSubId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            runCatching { android.telephony.SubscriptionManager.getActiveDataSubscriptionId() }.getOrDefault(-1)
        } else {
            runCatching { android.telephony.SubscriptionManager.getDefaultDataSubscriptionId() }.getOrDefault(-1)
        }
        if (dataSubId >= 0 && defaultTm != null) {
            val subTm = runCatching { defaultTm.createForSubscriptionId(dataSubId) }.getOrNull()
            if (subTm != null) return subTm
        }
        return defaultTm
    }
}
