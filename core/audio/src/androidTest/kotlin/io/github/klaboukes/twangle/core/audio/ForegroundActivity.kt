package io.github.klaboukes.twangle.core.audio

import android.app.Activity

/**
 * An empty screen for tests that record: Android silences the microphone of an app with nothing in
 * the foreground (the microphone is only allowed while the app is in use), and the recorder then
 * rightly reports it busy.
 */
class ForegroundActivity : Activity()
