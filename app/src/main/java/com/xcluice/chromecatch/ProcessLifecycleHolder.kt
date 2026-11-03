package com.xcluice.chromecatch

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * CameraX needs a LifecycleOwner to bind to. Since the selfie is captured from a
 * background service with no Activity on screen, this provides a minimal
 * always-started owner for that bind call.
 */
object ProcessLifecycleHolder {
    val owner: LifecycleOwner = SimpleOwner()

    private class SimpleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.STARTED
        }
        override val lifecycle: Lifecycle get() = registry
    }
}
