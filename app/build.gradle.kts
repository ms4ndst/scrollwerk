import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The release signing key, from the environment (GitHub Actions) or from
 * ~/.android/scrollwerk-release.properties (your computer). Debug and release builds are both signed with it,
 * so a build from any computer, or from a GitHub release, installs over any other. Without a key,
 * debug builds use Android's usual per-computer debug key and release builds stay unsigned.
 */
val signing: Map<String, String>? = run {
    val env = System.getenv()
    if (env["SCROLLWERK_KEYSTORE"] != null) {
        return@run mapOf(
            "keystore" to env.getValue("SCROLLWERK_KEYSTORE"),
            "keystorePassword" to env["SCROLLWERK_KEYSTORE_PASSWORD"].orEmpty(),
            "keyAlias" to (env["SCROLLWERK_KEY_ALIAS"] ?: "scrollwerk"),
            "keyPassword" to (env["SCROLLWERK_KEY_PASSWORD"] ?: env["SCROLLWERK_KEYSTORE_PASSWORD"].orEmpty()),
        )
    }
    val file = File(System.getProperty("user.home"), ".android/scrollwerk-release.properties")
    if (!file.isFile) return@run null
    val props = Properties().apply { file.inputStream().use(::load) }
    listOf("keystore", "keystorePassword", "keyAlias", "keyPassword").associateWith { props.getProperty("scrollwerk.$it").orEmpty() }
}

/** Runs git in the project; null if git or the history isn't there (a source download). */
fun git(vararg args: String): String? = runCatching {
    val process = ProcessBuilder("git", *args).directory(rootDir).redirectErrorStream(true).start()
    val out = process.inputStream.bufferedReader().readText().trim()
    out.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
}.getOrNull()

/** Every commit makes the version code go up, so each build installs over the one before. */
val gitVersionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

/** "0.2.0" on a tag v0.2.0; "0.2.0-3-gabc1234" three commits later; "0.1.0-dev" without tags or git. */
val gitVersionName = git("describe", "--tags", "--match", "v[0-9]*", "--dirty")?.removePrefix("v")
    ?: git("rev-parse", "--short", "HEAD")?.let { "0.1.0-dev+$it" }
    ?: "0.1.0-dev"

android {
    namespace = "dev.scrollwerk.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.scrollwerk.launcher"
        minSdk = 31 // Android 12: dynamic color, the OS range the plan soaks on
        targetSdk = 36
        versionCode = gitVersionCode
        versionName = gitVersionName
    }

    signingConfigs {
        if (signing != null) {
            create("scrollwerk") {
                storeFile = file(signing.getValue("keystore"))
                storePassword = signing.getValue("keystorePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfigs.findByName("scrollwerk")?.let { signingConfig = it }
        }
        debug {
            signingConfigs.findByName("scrollwerk")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
