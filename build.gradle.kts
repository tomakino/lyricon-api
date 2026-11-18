// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false

}

extra["compileSdkVersion"] = 37
extra["targetSdkVersion"] = 37

extra["contractVersion"] = "0.1.85"
extra["providerSdkVersion"] = "0.1.85"
extra["subscriberSdkVersion"] = "0.1.85"
extra["lyricModelVersion"] = "0.1.85"