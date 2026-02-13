package com.rupeelog.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Base64

object SignatureHelper {
    fun getAppSignature(context: Context): String {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val signature = signatures?.firstOrNull() ?: return "No Signature Found"
            
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(signature.toByteArray())
            
            return digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            return "Error: ${e.message}"
        }
    }
}
