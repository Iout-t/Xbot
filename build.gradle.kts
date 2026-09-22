val agpVersion = providers.gradleProperty("agpVersion").get()
val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
val hiltVersion = providers.gradleProperty("hiltVersion").get()
val protobufVersion = providers.gradleProperty("protobufVersion").get()

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.google.dagger:hilt-android-gradle-plugin:$hiltVersion")
        classpath("com.google.protobuf:protobuf-gradle-plugin:$protobufVersion")
    }
}

plugins {
    id("com.android.application") version agpVersion apply false
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion apply false
    id("com.google.dagger.hilt") version hiltVersion apply false
    id("com.google.protobuf") version protobufVersion apply false
}

allprojects {
    group = "com.example.automation"
    version = "1.0.0"
}
