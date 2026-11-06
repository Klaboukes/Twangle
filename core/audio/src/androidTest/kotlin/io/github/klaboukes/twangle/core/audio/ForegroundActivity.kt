package io.github.klaboukes.twangle.core.audio

import android.app.Activity

/**
 * An empty screen for tests that record or play: Android silences the microphone of an app with nothing
 * in the foreground (the microphone is only allowed while the app is in use), and the recorder then
 * rightly reports it busy; Android 15 also refuses such an app audio focus (on the Lenovo tablet, "blocked
 * by hardening"), so the tone and the drone stay silent.
 */
class ForegroundActivity : Activity()
