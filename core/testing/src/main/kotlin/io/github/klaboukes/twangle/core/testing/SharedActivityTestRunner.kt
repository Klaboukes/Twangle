package io.github.klaboukes.twangle.core.testing

import androidx.test.runner.AndroidJUnitRunner

/**
 * The usual test runner, except that it leaves activities open between tests. After each test
 * AndroidJUnitRunner closes every activity and waits for it to go, about 0.7 s on the Xiaomi test
 * phone; that would also close the activity [createSharedActivityComposeRule] keeps for the whole
 * run. A test that opens its own activity closes it itself (`ActivityScenario.use`).
 *
 * The same as the instrumentation argument `waitForActivitiesToComplete=false`, but part of the test
 * APK, so it holds however the tests are started (the script, Gradle, Android Studio).
 */
class SharedActivityTestRunner : AndroidJUnitRunner() {
    override fun shouldWaitForActivitiesToComplete(): Boolean = false
}
