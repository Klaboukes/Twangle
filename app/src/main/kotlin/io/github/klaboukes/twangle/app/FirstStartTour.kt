package io.github.klaboukes.twangle.app

/*
 * When the first-start tour ("Show me around", docs/architecture/ui-tuner.md, Tour) shows. MainActivity
 * keeps the state; these rules are pure so JVM tests check them.
 */

/**
 * Whether the tour is due: asked for again from Settings ([requested]), or never [seen] on this
 * install (an update counts as seen) and not ended in this session ([endedNow]: Skip tour, Back or
 * Done, before the saved flag arrives).
 */
internal fun tourDue(seen: Boolean, requested: Boolean, endedNow: Boolean): Boolean = requested || (!seen && !endedNow)

/**
 * Whether the tour shows: while it is [due], on the tuner ([onTuner]) once the tuner itself shows
 * ([tunerShown]: after the microphone question, never over the question itself).
 */
internal fun tourShown(due: Boolean, onTuner: Boolean, tunerShown: Boolean): Boolean = due && onTuner && tunerShown
