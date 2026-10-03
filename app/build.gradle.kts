import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 签名信息从 local.properties 读取（该文件不入库，已在 .gitignore 中排除）。
// 需要的键：RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.tinymcserver.app"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = keystoreProps.getProperty("RELEASE_STORE_FILE")?.let { file(it) } ?: file("release.keystore")
            storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    defaultConfig {
        applicationId = "dev.tinymcserver.app"
        minSdk = 26
        // 关键：Android 10+ 中 targetSdk>=29 的应用被禁止 exec 私有目录内的文件（W^X）。
        // 内置 JRE 必须从私有目录执行 java，因此保持 targetSdk=28 走兼容域（Termux 同款方案）。
        targetSdk = 28
        versionCode = 3
        versionName = "1.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    androidResources {
        // JRE 归档保持未压缩，运行时可按需流式解压
        noCompress += listOf("xz", "jar", "zip")
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
