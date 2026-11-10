package io.github.klaboukes.twangle.app

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import io.github.klaboukes.twangle.feature.tuner.RatingWindow

/** The Play build's rating window; the free build's twin in src/foss has none (Phase 92). */
fun storeRatingWindow(appContext: Context): RatingWindow = PlayRatingWindow(appContext)

/**
 * Google Play's own rating window (In-App Review, Phase 50). The Play Store app draws it and talks
 * to Google; Twangle hands it nothing but the request, and adds no permission (the library's merged
 * manifest holds only Play's hidden helper activity). Google decides whether it shows at all: it
 * keeps a quota, and an install from the PC never shows it. Every failure (no Play Store, an old
 * one, no account) is silent: the player sees nothing and the tuner carries on.
 */
class PlayRatingWindow(private val appContext: Context) : RatingWindow {

    /** Made on first use, so the app's start never touches Google Play. */
    private val manager: ReviewManager by lazy { ReviewManagerFactory.create(appContext) }

    override fun show(activity: Activity) {
        runCatching {
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing && !activity.isDestroyed) {
                    runCatching { manager.launchReviewFlow(activity, request.result) }
                }
            }
        }
    }
}
