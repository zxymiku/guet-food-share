import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
val apiBase = (System.getenv("API_BASE_URL") ?: local.getProperty("API_BASE_URL") ?: "https://food.zxymiku.top").trimEnd('/')
require(apiBase == "https://food.zxymiku.top") { "API_BASE_URL must use the project domain https://food.zxymiku.top" }
android {
    namespace = "com.guet.foodshare"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.guet.foodshare"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1000
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"${apiBase.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        manifestPlaceholders["AMAP_KEY"] = System.getenv("AMAP_ANDROID_KEY") ?: local.getProperty("AMAP_ANDROID_KEY") ?: ""
    }
    signingConfigs {
        create("production") {
            val path=System.getenv("SIGNING_KEYSTORE_PATH")
            if(path!=null){storeFile=file(path);storePassword=System.getenv("SIGNING_STORE_PASSWORD");keyAlias=System.getenv("SIGNING_KEY_ALIAS");keyPassword=System.getenv("SIGNING_KEY_PASSWORD")}
        }
    }
    buildTypes {getByName("release"){signingConfig=signingConfigs.getByName("production");isMinifyEnabled=false}}
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies { implementation("com.amap.api:3dmap:10.0.600"); implementation("androidx.core:core-ktx:1.15.0") }
