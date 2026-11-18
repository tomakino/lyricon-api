import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    signing
    id("com.vanniktech.maven.publish")
}

group = "io.github.proify.lyricon"
project.version = rootProject.extra.get("providerSdkVersion") as String

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.provider"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }
}

dependencies {
    api(project(":contract"))
    api(project(":model"))
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat.resources)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val version: String = rootProject.extra.get("providerSdkVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "provider",
        version
    )

    pom {
        name.set("provider")
        description.set("Provide lyrics services for Lyricon")
        inceptionYear.set("2026")
        url.set("https://github.com/tomakino/lyricon-api")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("tomakino")
                name.set("tomakino")
                url.set("https://github.com/tomakino")
            }
        }
        scm {
            url.set("https://github.com/tomakino/lyricon-api")
            connection.set("scm:git:git://github.com/tomakino/lyricon-api.git")
            developerConnection.set("scm:git:ssh://git@github.com/tomakino/lyricon-api.git")
        }
    }
    publishToMavenCentral()
    signAllPublications()
}

afterEvaluate {
    signing {
        useGpgCmd()
    }
}