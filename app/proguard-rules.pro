# Project-specific R8 rules. Compose, AndroidX, DataStore and core-splashscreen ship their own
# consumer rules, and the app uses no reflection, serialization or resources looked up by name.

# Settings are saved by enum constant name (DataStoreSettingsRepository: theme.name, enumOrNull).
# R8 keeps Enum.name() as written in the source, but keep these constants explicitly so a saved
# setting can never stop matching after an update. Never rename these constants either.
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.TunerMode { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.InTunePrecision { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.ThemeMode { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.TunerDisplay { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.WaveLook { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.MeterLook { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.InTuneEffect { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.metronome.Meter { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.music.Notation { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.metronome.ClickSound { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.settings.PracticeTool { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.music.PitchClass { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.chord.ChordQuality { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.scale.ScaleType { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.drone.DroneSound { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.metronome.Subdivision { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.eartraining.Exercise { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.eartraining.Interval { public static final ** *; }
-keepclassmembers enum io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection { public static final ** *; }
