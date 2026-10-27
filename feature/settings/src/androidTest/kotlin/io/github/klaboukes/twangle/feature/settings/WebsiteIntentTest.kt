package io.github.klaboukes.twangle.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Help & website" asks the phone's browser to show the page: a plain view of the address, for any
 * browser, so Twangle itself needs no internet permission.
 */
@RunWith(AndroidJUnit4::class)
class WebsiteIntentTest {

    @Test
    fun theIntentAsksABrowserToShowTheAddress() {
        val intent = websiteIntent(websiteLink("nl"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse("https://klaboukes.github.io/nl/"), intent.data)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull("any browser, not one chosen by Twangle", intent.`package`)
    }
}
