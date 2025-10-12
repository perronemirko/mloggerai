import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.9.0"
}

group = "com.mloggerai.plugin"
version = "0.0.2"

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

    implementation("org.bouncycastle:bctls-jdk14:1.82")
    implementation("org.brotli:dec:0.1.2")
    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")
    implementation("org.graalvm.sdk:graal-sdk:25.0.0")
// https://mvnrepository.com/artifact/org.openjsse/openjsse
    implementation("org.openjsse:openjsse:1.1.14")
    // https://mvnrepository.com/artifact/org.slf4j.impl/log4j12
    implementation("ch.qos.logback:logback-classic:1.5.19")




    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    intellijPlatform {
        pycharmCommunity("2024.2.4")
        bundledPlugins(emptyList())
        testFramework(TestFrameworkType.Starter)
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242.4")
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