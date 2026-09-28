plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group = "io.causalguard"
version = "0.1.0"

android {
    namespace = "com.causalguard.rules"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "NewApi"
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
}
