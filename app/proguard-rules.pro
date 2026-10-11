# Project-specific R8 rules. Compose, AndroidX, DataStore and core-splashscreen ship their own
# consumer rules, and the app uses no reflection, serialization or resources looked up by name.

# Settings are saved by enum constant name (DataStoreSettingsRepository: style.name, enumOrNull).
# R8 keeps Enum.name() as written in the source, but keep these constants explicitly so a saved
# setting can never stop matching after an update. Never rename these constants either.
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.ThemeMode { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.VisualStyle { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.WaveLook { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.music.Notation { public static final ** *; }
