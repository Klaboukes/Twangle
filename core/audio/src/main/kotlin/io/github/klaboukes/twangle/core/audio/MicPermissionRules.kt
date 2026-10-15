package io.github.klaboukes.twangle.core.audio

/** Where the app stands with the `RECORD_AUDIO` permission. */
enum class MicPermissionStatus {
    /** Not granted, and not refused for good as far as the app knows: offer "Allow microphone". */
    NOT_REQUESTED,

    /** The user said no once: explain why the tuner needs the mic, then ask again. */
    DENIED,

    /** Android won't show the dialog any more ("don't ask again", or refused twice): only Settings can grant it. */
    PERMANENTLY_DENIED,

    GRANTED,
}

/**
 * Pure rules for [MicPermissionStatus], kept apart from Android so they can be unit tested. The
 * phone's tuner and the watch app both follow them.
 *
 * Android only reveals a permanent denial indirectly, through `shouldShowRequestPermissionRationale`
 * ("rationale" below), which is false both before the first refusal and after a permanent one. A
 * dialog dismissed without an answer (a tap outside it, Back) changes nothing, so it also comes back
 * denied without a rationale. The rules tell these apart by the rationale before the request and by
 * whether the player ever refused the dialog on this install (`refusedBefore`, saved in the settings).
 */
object MicPermissionRules {

    /** Status when the screen opens, before any request in this session. */
    fun initial(granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        shouldShowRationale -> MicPermissionStatus.DENIED
        // Never asked, or permanently denied in an earlier session. Asking tells them apart.
        else -> MicPermissionStatus.NOT_REQUESTED
    }

    /**
     * Status after the system permission dialog returns. [rationaleBefore] and [rationaleAfter] are
     * the rationale just before the request and after it; [refusedBefore] says whether the player
     * refused the dialog in an earlier request on this install.
     */
    fun afterRequest(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
        refusedBefore: Boolean,
    ): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        // A refusal Android will still ask about again.
        rationaleAfter -> MicPermissionStatus.DENIED
        // The rationale went away: "don't ask again" (Android 10 and older) or a second refusal (11+).
        rationaleBefore -> MicPermissionStatus.PERMANENTLY_DENIED
        // Refused before and no rationale now: Android answered without showing the dialog.
        refusedBefore -> MicPermissionStatus.PERMANENTLY_DENIED
        // Never refused: the dialog was dismissed without an answer. Asking again still works.
        else -> MicPermissionStatus.NOT_REQUESTED
    }

    /** Whether a request with this outcome is a refusal worth remembering for [afterRequest]'s `refusedBefore`. */
    fun isRefusal(status: MicPermissionStatus): Boolean =
        status == MicPermissionStatus.DENIED || status == MicPermissionStatus.PERMANENTLY_DENIED

    /**
     * Status after the app comes back to the foreground (e.g. from Settings), or when the microphone
     * turns out to be refused while the tuner runs. A grant wins. A permission lost while the app was
     * open (taken back in Settings, or a one-time grant that ran out) goes back to the prompt, as
     * [initial] would show it; otherwise the status stays.
     */
    fun onResume(current: MicPermissionStatus, granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        current == MicPermissionStatus.GRANTED -> initial(granted = false, shouldShowRationale = shouldShowRationale)
        else -> current
    }
}
