import org.gradle.kotlin.dsl.signing

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    signing
    id("com.vanniktech.maven.publish")
}

group = "io.github.proify.lyricon"
project.version = rootProject.extra.get("coreVersion") as String

android {
    namespace = "io.github.proify.lyricon.core"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }
    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        aidl = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.resources)
    testImplementation(libs.junit)
}
val version: String = rootProject.extra.get("coreVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "core",
        version
    )

    pom {
        name.set("core")
        description.set("Shared contracts for Lyricon")
        inceptionYear.set("2026")
        url.set("https://github.com/proify/lyricon")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("Proify")
                name.set("Proify")
                url.set("https://github.com/proify")
            }
        }
        scm {
            url.set("https://github.com/proify/lyricon")
            connection.set("scm:git:git://github.com/proify/lyricon.git")
            developerConnection.set("scm:git:ssh://git@github.com/proify/lyricon.git")
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