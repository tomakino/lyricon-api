import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    signing
    id("com.vanniktech.maven.publish")
    alias(libs.plugins.dokka)
}

group = "io.github.proify.lyricon"
project.version = rootProject.extra.get("centralVersion") as String

configure<LibraryExtension> {
    namespace = "io.github.proify.lyricon.central"
    compileSdk {
        version = release(rootProject.extra.get("compileSdkVersion") as Int)
    }
    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":contract"))
    api(project(":provider"))
    api(project(":subscriber"))

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val version: String = rootProject.extra.get("centralVersion") as String

mavenPublishing {
    coordinates(
        "io.github.proify.lyricon",
        "central",
        version
    )

    pom {
        name.set("central")
        description.set("Lyricon central bridge for SystemUI")
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
