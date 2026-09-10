import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2025.2.6.2")
        //local(file("/home/xenial/.local/share/JetBrains/Toolbox/apps/android-studio"))
        testFramework(TestFrameworkType.Platform)
    }
}
