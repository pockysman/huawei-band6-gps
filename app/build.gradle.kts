plugins {
    id("com.android.application")
}

android {
    namespace = "com.band6.gps"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.band6.gps"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.3.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.media:media:1.7.0")
}

// Sisipkan DEX inti HookUtils v1.2.7_p2 (cn.wwl.xposed.hook.MainHook + DexKit bridge) ke dalam APK
tasks.matching { it.name == "mergeProjectDexDebug" }.configureEach {
    doLast {
        val hookDex = file("libs/hookutils_relocated.dex")
        if (hookDex.exists()) {
            val outDir = layout.buildDirectory.dir("intermediates/dex/debug/mergeProjectDexDebug/15").get().asFile
            outDir.mkdirs()
            hookDex.copyTo(File(outDir, "classes.dex"), overwrite = true)
        }
    }
}
