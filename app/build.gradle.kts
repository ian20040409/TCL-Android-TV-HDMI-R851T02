plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace  = "com.lnu.tclhdmilauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lnu.tclhdmilauncher"
        minSdk        = 28
        targetSdk     = 36
        versionCode   = 21
        versionName   = "2.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ── 記憶體 / APK 體積極致最佳化 ───────────────────────────────────────────
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "kotlin/**",
                "DebugProbesKt.bin"
            )
        }
    }
}

// 引入 Android TV 官方 Leanback 支援庫（供 GuidedStepSupportFragment OOBE 精靈使用）
dependencies {
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.leanback:leanback-preference:1.2.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    val shizuku_version = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizuku_version")
    implementation("dev.rikka.shizuku:provider:$shizuku_version")
}
