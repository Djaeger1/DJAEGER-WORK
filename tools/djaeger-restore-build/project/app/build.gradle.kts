plugins { id("com.android.application") }

android {
    namespace = "com.djaeger.ai_backup_restore"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.djaeger.ai_backup_restore"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.1-choose-file"
    }
    buildTypes {
        release { minifyEnabled = false; shrinkResources = false }
    }
}
