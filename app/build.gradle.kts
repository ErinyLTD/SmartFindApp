/*
 * SmartFind - Find your phone with an SMS from a trusted contact
 * Copyright (C) 2026 ErinyLTD
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.smartfind.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smartfind.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.1.2"
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }


    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // MVVM
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Encrypted storage (Finding 4)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.security:security-crypto-ktx:1.1.0")

    // Biometric authentication (Finding 1)
    implementation("androidx.biometric:biometric:1.1.0")

    // Room database for audit log (Finding 3)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Kotest BDD framework + assertions
    testImplementation("io.kotest:kotest-runner-junit5:6.1.3")
    testImplementation("io.kotest:kotest-assertions-core:6.1.3")

    // kotlin-reflect required by custom Robolectric extension (findAnnotation)
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.1.20")

    // Robolectric + Android test support
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")

    // Mockito for mocking Android framework classes
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
}
