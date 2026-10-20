package io.github.klaboukes.twangle.core.testing

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The same as `createComposeRule()` (v2), except that every test in the run shows its content in one
 * shared activity. `createComposeRule()` opens a new activity for each test and closes it afterwards,
 * and on the Xiaomi test phone the closing alone takes about 0.7 s, more than most tests themselves.
 *
 * Each test still gets a fresh composition, on its own recomposer and test clock, and its own
 * [ViewModelStore], so nothing carries over from one test to the next. Only for tests that show
 * composables: a test that changes the activity itself (rotation, recreation, window flags) uses
 * `createComposeRule()` or `ActivityScenario`.
 */
fun createSharedActivityComposeRule(): ComposeContentTestRule =
    SharedActivityComposeRule(createEmptyComposeRule())

private class SharedActivityComposeRule(
    private val rule: ComposeTestRule,
) : ComposeContentTestRule, ComposeTestRule by rule {

    private var viewModels: ViewModelStore? = null

    // Inside the empty rule, so the test's recomposer and clock are in place while content shows.
    override fun apply(base: Statement, description: Description): Statement = rule.apply(
        object : Statement() {
            override fun evaluate() {
                val activity = SharedActivity.open()
                SharedActivity.clear(activity)
                try {
                    base.evaluate()
                } finally {
                    SharedActivity.clear(activity)
                    // A PhoneScreen in another language set the default one; the next test starts in the phone's.
                    DefaultLocale.reset()
                    // On the main thread, as when an activity closes.
                    viewModels?.let { InstrumentationRegistry.getInstrumentation().runOnMainSync(it::clear) }
                    viewModels = null
                }
            }
        },
        description,
    )

    override fun setContent(composable: @Composable () -> Unit) {
        val activity = SharedActivity.open()
        val store = ViewModelStore().also { viewModels = it }
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
        rule.runOnUiThread {
            check(activity.findViewById<ViewGroup>(android.R.id.content).childCount == 0) {
                "setContent can only be called once per test"
            }
            activity.setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = composable)
            }
        }
        rule.waitForIdle()
    }
}

/** One activity for the whole run, emptied before and after each test. */
private object SharedActivity {

    private var opened: ComponentActivity? = null

    fun open(): ComponentActivity {
        opened?.takeIf { !it.isFinishing && !it.isDestroyed }?.let { return it }
        var activity: ComponentActivity? = null
        // Never closed: the test process ends with the run, and the activity with it. Edge to edge, as the
        // app and every phone from Android 15 on: the system bars lie over the window (PhoneScreen).
        ActivityScenario.launch(ComponentActivity::class.java).onActivity {
            it.enableEdgeToEdge()
            activity = it
        }
        return checkNotNull(activity).also { opened = it }
    }

    /**
     * Removes the last test's content, which disposes its composition, and forgets the recomposer the
     * window kept for it: the window stays open, so the next test's content would otherwise compose on
     * a recomposer that test has already stopped.
     */
    fun clear(activity: ComponentActivity) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        activity.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
        activity.window.decorView.compositionContext = null
    }
}
