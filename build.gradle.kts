import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}



val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

val localIdePath: String? = localProps.getProperty("local.ide.path")

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
//        if (!localIdePath.isNullOrBlank()) { local(localIdePath) } else {
//            intellijIdea("2025.3.6.1")
//        }
       intellijIdea("2025.3.6.1")
        testFramework(TestFrameworkType.Platform)
    }
}


//tasks {
//    verifyPlugin {
//        // Abaikan peringatan Internal API usages agar build task tidak gagal (tetap dianggap OK/Lolos)
//        // karena ini adalah false positive bridge methods dari Kotlin
//        failureLevel = listOf(
//            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
//            VerifyPluginTask.FailureLevel.INVALID_PLUGIN
//        )
//    }
//}


