import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.2.6.2")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            it.substringAfter("## 🖼️ Visual Showcase").substringBeforeLast("---").trim()
        }

        changeNotes = providers.provider {
            changelog.renderItem(
                changelog.getLatest().withHeader(false).withEmptySections(false)
            )
        }
    }
}
