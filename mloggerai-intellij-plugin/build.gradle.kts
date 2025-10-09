import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.mloggerai.plugin"
version = "0.0.1"

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
        pycharmCommunity("2024.2.4")
        bundledPlugins(emptyList())
        instrumentationTools()
        testFramework(TestFrameworkType.Starter)
    }

    sourceSets {
        create("integrationTest") {
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        }
    }

    val integrationTestImplementation by configurations.getting {
        extendsFrom(configurations.testImplementation.get())
    }

    dependencies {
        integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
        integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
        integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
    }

    val integrationTest = task<Test>("integrationTest") {
        val integrationTestSourceSet = sourceSets.getByName("integrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath
        systemProperty("path.to.build.plugin", tasks.prepareSandbox.get().pluginDirectory.get().asFile)
        useJUnitPlatform()
        dependsOn(tasks.prepareSandbox)
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