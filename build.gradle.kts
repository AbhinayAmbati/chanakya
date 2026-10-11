// Root build file. Plugins are declared here (with `apply false`) so that every
// module can opt in using the version catalog, keeping versions in one place.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
