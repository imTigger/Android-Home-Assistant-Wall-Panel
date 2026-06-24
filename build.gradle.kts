plugins {
    // AGP 9.x has built-in Kotlin support, so the kotlin.android plugin is NOT applied.
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
