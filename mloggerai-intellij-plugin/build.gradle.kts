plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.mloggerai.plugin"
version = "0.0.4"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.json:json:20250517")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.5.1")

    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        pycharmCommunity("2024.2.4")
        bundledPlugins(emptyList())
        instrumentationTools()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242")
            untilBuild.set("252.*")
        }
    }

    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
        channels.set(listOf("default")) // o "beta", "alpha"
    }

    instrumentCode.set(true)
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        })
    }
}