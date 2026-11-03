package com.xcluice.chromecatch

import android.content.Intent

/**
 * Holds the one-time MediaProjection consent token for the life of the process.
 * Android ties this consent to a single grant; if the process is killed (e.g. by
 * aggressive battery optimization) the user will need to tap "Allow screen capture"
 * in MainActivity once more. Exempting ChromeCatch from battery optimization in
 * system settings avoids that.
 */
object ProjectionHolder {
    var resultCode: Int? = null
    var data: Intent? = null
}
