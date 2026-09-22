import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Shared, non-secret defaults. Personal values belong in AppConfig_test.properties.
val stringDefaults = linkedMapOf(
    "SMB_HOST" to "",
    "SMB_SHARE_NAME" to "",
    "SMB_USERNAME" to "",
    "SMB_PASSWORD" to "",
    "SMB_DOMAIN" to "",
    "ADMIN_PASSCODE" to "1234",
    "SLACK_CHANNEL" to "",
    "SLACK_BOT_TOKEN" to "",
    "DATABASE_PATH" to "psni_server_room_sign_in.db",
    "AUDIT_LOG_PATH" to "psni_server_room_sign_in_audit.txt",
    "ERROR_LOG_PATH" to "psni_server_room_sign_in_error.txt",
    "TIMESTAMP_PATTERN" to "yyyy-MM-dd HH:mm:ss",
    "REASON_OPTIONS" to "Servers/Network|Utilities maintenance|Inspection/Audit|Emergency repairs"
)
val booleanDefaults = linkedMapOf(
    "SMB_EXPORT_ENABLED" to false,
    "SLACK_NOTIFICATIONS_ENABLED" to false,
    "useMockData" to false
)

// BuildConfig fields contain Java source literals, so escape user-supplied strings.
fun javaString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        append(when (char) {
            '\\' -> "\\\\"
            '"' -> "\\\""
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (char.code < 32 || char.code > 126) {
                "\\u" + char.code.toString(16).padStart(4, '0')
            } else char.toString()
        })
    }
    append('"')
}
android {
    namespace = "com.example.psnisignin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.psnisignin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Compile Java source code for Java 17.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            stringDefaults.forEach { (key, value) -> buildConfigField("String", key, javaString(value)) }
            booleanDefaults.forEach { (key, value) ->
                buildConfigField("boolean", if (key == "useMockData") "USE_MOCK_DATA" else key, value.toString())
            }
        }
    }
}

// Configure the Kotlin compiler to generate Java 17-compatible bytecode.
// This replaces the deprecated android { kotlinOptions { ... } } syntax.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.hierynomus:smbj:0.13.0")
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        // Read only when debug fields are needed; release never loads this file.
        val localConfigFile = rootProject.layout.projectDirectory.file("AppConfig_test.properties")
        val localConfigText = providers.fileContents(localConfigFile).asText
        val debugFields = localConfigText.orElse("").map { contents ->
            val localConfig = Properties().apply { contents.reader().use { load(it) } }
            val knownKeys = stringDefaults.keys + booleanDefaults.keys
            require(localConfig.stringPropertyNames().all { it in knownKeys }) {
                "Unknown setting in AppConfig_test.properties; use AppConfig_test.example.properties as a reference."
            }
            val values = linkedMapOf<String, com.android.build.api.variant.BuildConfigField<String>>()
            stringDefaults.forEach { (key, fallback) ->
                val value = localConfig.getProperty(key, fallback)
                require(key != "REASON_OPTIONS" || value.split('|').all { it.isNotBlank() }) {
                    "REASON_OPTIONS must contain non-empty choices separated by |."
                }
                values[key] = com.android.build.api.variant.BuildConfigField("String", javaString(value), null)
            }
            booleanDefaults.forEach { (key, fallback) ->
                val value = localConfig.getProperty(key)?.trim()?.let {
                    requireNotNull(it.toBooleanStrictOrNull()) { "$key must be true or false." }
                } ?: fallback
                val fieldName = if (key == "useMockData") "USE_MOCK_DATA" else key
                values[fieldName] = com.android.build.api.variant.BuildConfigField("boolean", value.toString(), null)
            }
            values
        }
        requireNotNull(variant.buildConfigFields).putAll(debugFields)
    }

}
