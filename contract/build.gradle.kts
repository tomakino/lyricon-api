plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    signing
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.dokka)
}

group = "io.github.proify.lyricon"
project.version = rootProject.extra.get("contractVersion") as String

android {
    namespace = "io.github.proify.lyricon.contract"
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

val version: String = rootProject.extra.get("contractVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "contract",
        version
    )

    pom {
        name.set("contract")
        description.set("Shared contracts for Lyricon")
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
