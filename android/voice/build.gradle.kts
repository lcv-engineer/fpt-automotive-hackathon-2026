plugins {
    id("com.android.library")
}

android {
    namespace = "com.viva.voice"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29 // AAOS / Android 10, the CarSky Device baseline
        consumerProguardFiles("consumer-rules.pro")

        // 03-contracts.md §2: "Config trong app doc tu BuildConfig.ASR_BASE_URL
        // - khong hard-code". Overridden per build; PA-2 (adb reverse) is the
        // default because it works before the Container Node exists.
        buildConfigField(
            "String",
            "ASR_BASE_URL",
            "\"" + (project.findProperty("vivaAsrBaseUrl") ?: "http://127.0.0.1:8080") + "\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }

    testOptions {
        unitTests.all {
            // Gradle forks the test JVM and does not inherit the daemon's -D
            // flags. Without this, -Dviva.bench.csv silently arrives as null and
            // the bench-scoring test passes without scoring anything.
            it.systemProperty("viva.bench.csv", System.getProperty("viva.bench.csv") ?: "")
        }
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // AudioCapture phát Flow<PcmFrame> (28-PIPELINE §8 P0.1). `api` chứ không phải
    // `implementation`: Flow nằm trong chữ ký công khai của module, consumer phải
    // thấy được kiểu đó.
    api(libs.kotlinx.coroutines.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
}
