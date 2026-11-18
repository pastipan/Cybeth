import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "co.cuadra.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.cuadra.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    // Llave fija de Cybeth: así cada versión nueva se instala encima de la anterior sin borrar nada.
    val ksFile = file("keystore.properties")
    val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }
    signingConfigs {
        create("cybeth") {
            storeFile = file(ks.getProperty("storeFile", "cybeth.jks"))
            storePassword = ks.getProperty("storePassword")
            keyAlias = ks.getProperty("keyAlias", "cybeth")
            keyPassword = ks.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile.exists()) signingConfigs.getByName("cybeth") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md", "META-INF/LICENSE.md",
                "META-INF/NOTICE.txt", "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES", "META-INF/AL2.0", "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Inicio de sesión con huella
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Revisión periódica del correo
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Guardar la clave del correo cifrada
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Leer el correo por IMAP (Gmail)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation("junit:junit:4.13.2")
}
