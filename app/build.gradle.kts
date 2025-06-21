plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.eei4369.markio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eei4369.markio"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material) // For FloatingActionButton and Material Design components
    implementation(libs.constraintlayout)

    // Dependencies for Google Maps integration
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation(libs.activity)

    // For RecyclerView (to display lists)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // For Glide (efficient image loading and caching)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // For FlexboxLayout (required for dynamic tags on MainActivity)
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}