package com.vitorfreitas.getyourhelp.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.vitorfreitas.getyourhelp.data.Coordinates

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/**
 * Shows a task's area in a maps app (Google Maps, if installed) with a pin labelled [label].
 * Without a maps app it opens Google Maps in the browser. Returns false if nothing could open it.
 * The app has no map of its own, so it needs no Maps API key.
 */
fun openInMaps(context: Context, label: String, coordinates: Coordinates?): Boolean {
    val geoUri = if (coordinates != null) {
        val point = "${coordinates.latitude},${coordinates.longitude}"
        "geo:$point?q=$point(${Uri.encode(label)})".toUri()
    } else {
        "geo:0,0?q=${Uri.encode(label)}".toUri()
    }
    val query = coordinates?.let { "${it.latitude},${it.longitude}" } ?: label
    val webUri = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}".toUri()

    for (uri in listOf(geoUri, webUri)) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            return true
        } catch (_: ActivityNotFoundException) {
            // No app for this link, so try the next one
        }
    }
    return false
}
