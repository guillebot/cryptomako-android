plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.gschimmel.cryptomako.vault"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            // Keep CryptorProvider ServiceLoader entries
            pickFirsts += "META-INF/services/org.cryptomator.cryptolib.api.CryptorProvider"
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    api("org.cryptomator:cryptolib:2.2.2")
    // Guava Android-friendly; cryptolib pulls jre variant — force android
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.slf4j:slf4j-nop:2.0.17")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.1.10")
}
