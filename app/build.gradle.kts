import java.net.URI
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
}

fun catalogUrl(value: String, allowLocalHttp: Boolean): String {
    if (value.isBlank()) return ""
    val uri = URI(value.trim())
    val local = allowLocalHttp && uri.scheme == "http" &&
            uri.host in listOf("127.0.0.1", "localhost", "10.0.2.2")
    require((uri.scheme == "https" || local) && uri.host != null &&
            uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "The catalog URL must be HTTPS, or an allowed debug loopback URL."
    }
    return uri.toASCIIString().trimEnd('/') + "/"
}

fun buildStringLiteral(value: String): String = "\"" +
        value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val catalogBase = catalogUrl(providers.gradleProperty("barcodeApiBaseUrl").orElse("").get(), false)
val debugCatalogBase = catalogUrl(providers.gradleProperty("barcodeApiDebugBaseUrl")
    .orElse(catalogBase).get(), true)
val catalogRegion = providers.gradleProperty("barcodeApiRegion").orElse("TR").get()
    .uppercase(Locale.ROOT)
require(catalogRegion.matches(Regex("[A-Z]{2}"))) { "barcodeApiRegion must be a two-letter country code." }

android {
    namespace = "com.example.barcodescanner"
    compileSdk = 36
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "tr")
    }
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.barcodescanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.1"
        buildConfigField("String", "CATALOG_BASE_URL", buildStringLiteral(catalogBase))
        buildConfigField("String", "CATALOG_REGION", buildStringLiteral(catalogRegion))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "CATALOG_BASE_URL", buildStringLiteral(debugCatalogBase))
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
}

dependencies {

    implementation(libs.mlkit.barcode)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.mlkit)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.glide)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
