import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

// BUILD_NUMBER is stored in gradle.properties as a plain integer, but every place it is
// *rendered* zero-pads it to three digits: the versionName, and therefore the APK filename
// and any release tag derived from them. Unpadded counters sort wrongly in a file listing
// ("+10" lands before "+3"), which buries the newest build in the middle of ~/tmp/ and of
// the phone's file manager. Three digits fixes the order up to +999 — which the versionCode
// multiplier below already caps the counter at anyway. The versionCode itself keeps the
// plain integer; the padding is text only.
val forkBuildNumber = project.property("BUILD_NUMBER").toString().trim().toInt()
val forkBuildNumberPadded = forkBuildNumber.toString().padStart(3, '0')
val forkVersionName = "${project.property("VERSION_NAME")}+$forkBuildNumberPadded"
val forkVersionCode = project.property("VERSION_CODE").toString().toInt() * 10000 + forkBuildNumber

base {
    archivesName = "shiroikuma-yotehyo_${forkVersionName}_arm64-v8a"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionCode = forkVersionCode
        versionName = forkVersionName
        vectorDrawables.useSupportLibrary = true
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("core")
        register("foss")
        register("gplay")
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_NAMESPACE").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

tasks.register("buildFoss") {
    description = "Build the signed foss release APK, copy it to ~/tmp, then bump BUILD_NUMBER"
    dependsOn("assembleFossRelease")
    doLast {
        val apkName = "shiroikuma-yotehyo_${forkVersionName}_arm64-v8a.apk"
        val outputDir = layout.buildDirectory.dir("outputs/apk/foss/release").get().asFile
        val targetDir = File(System.getProperty("user.home"), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("[1;36m>>> ~/tmp/$apkName[0m")

            // Optional convenience prompt for manual terminal runs. Under Claude Code
            // this read gets EOF and is skipped — Claude asks and runs `adb push` itself.
            ProcessBuilder("bash", "-c", """
                echo -e '\033[1;33m>>> CONNECT YOUR PHONE VIA USB AND ENABLE ADB <<<\033[0m'
                read -p ${'$'}'\033[1;33m>>> Push to phone? (y/n) \033[0m' ans
                if [[ "${'$'}ans" =~ ^[Yy]${'$'} ]]; then
                    adb shell mkdir -p /sdcard/tmp
                    adb push '${targetFile.absolutePath}' '/sdcard/tmp/$apkName'
                    echo -e '\033[1;32m>>> Pushed to /sdcard/tmp/$apkName\033[0m'
                else
                    echo "Skipped adb push."
                fi
            """.trimIndent()).inheritIO().start().waitFor()
        }

        // Auto-increment BUILD_NUMBER so the next build is the next "+N". The property stays
        // a plain integer on disk — only the rendered forms above are zero-padded.
        val propsFile = rootProject.file("gradle.properties")
        val nextBuildNumber = forkBuildNumber + 1
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$forkBuildNumber",
                "BUILD_NUMBER=$nextBuildNumber"
            )
        )
        val nextPadded = nextBuildNumber.toString().padStart(3, '0')
        println("[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber (next build: +$nextPadded)[0m")
    }
}

dependencies {
    implementation(libs.fossify.commons)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.print)
    implementation(libs.bundles.room)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.truth)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
}
