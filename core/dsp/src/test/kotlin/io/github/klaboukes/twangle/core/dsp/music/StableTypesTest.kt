package io.github.klaboukes.twangle.core.dsp.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * The types `compose-stability.conf` (at the repo root) names as stable for Compose are truly immutable (Phase 89):
 * every field of each, and of each type they hold, is final, so nothing inside changes after construction. A stable
 * type that changed inside would leave a screen showing old values, since Compose skips what it thinks unchanged.
 * Read-only lists and sets, private arrays made once in the constructor and lazy values are allowed: the classes
 * never change them. A `var` added to one of these types fails here; then take it out of the file, or keep it a `val`.
 */
class StableTypesTest {

    private val named: List<String> by lazy {
        val path = System.getProperty("twangle.stabilityConfig") ?: "../../compose-stability.conf"
        File(path).readLines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun theFileNamesTheMusicTypesTheTunerShows() {
        assertEquals(
            listOf(Tuning::class, Instrument::class, Note::class, LoweredName::class).map { it.java.name },
            named,
        )
    }

    @Test
    fun everyTypeNamedIsImmutable() {
        val problems = mutableListOf<String>()
        for (name in named) checkImmutable(Class.forName(name), problems, mutableSetOf())
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private fun checkImmutable(type: Class<*>, problems: MutableList<String>, seen: MutableSet<Class<*>>) {
        if (!seen.add(type) || type.isPrimitive || type.isEnum || type == String::class.java) return
        // A sealed interface: its kinds, nested in it.
        if (type.isInterface) {
            type.declaredClasses.filter { type.isAssignableFrom(it) }.forEach { checkImmutable(it, problems, seen) }
            return
        }
        var c: Class<*>? = type
        while (c != null && c != Any::class.java) {
            for (field in c.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val where = "${c.simpleName}.${field.name}"
                if (!Modifier.isFinal(field.modifiers)) problems += "$where can be changed (a var)"
                val held = field.type
                when {
                    held.isArray -> if (!Modifier.isPrivate(field.modifiers)) problems += "$where is an array others can change"
                    // Read-only collections and lazy values, never changed by the classes that hold them.
                    held == List::class.java || held == Set::class.java || held == Lazy::class.java -> Unit
                    held.name.startsWith("java.") || held.name.startsWith("kotlin.") ->
                        if (!held.isPrimitive && held != String::class.java && !held.isEnum && !isBoxed(held)) problems += "$where holds a ${held.name}"
                    else -> checkImmutable(held, problems, seen)
                }
            }
            c = c.superclass
        }
    }

    private fun isBoxed(type: Class<*>) =
        type in listOf(Integer::class.java, java.lang.Float::class.java, java.lang.Double::class.java, java.lang.Boolean::class.java)
}
