import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.jx"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("ideaLocalPath")
        if (localIde.isPresent) local(localIde.get()) else intellijIdea("2026.1.3")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier("1.411")
    }
    testImplementation("junit:junit:4.13.2")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "261.25134.95"; untilBuild = "261.25134.*" }
    }
    pluginVerification {
        ides { current() }
    }
}

val serverJar = layout.projectDirectory.file("../../jx-language-server/target/jx-language-server-0.1.0-SNAPSHOT.jar")
tasks.processResources {
    doFirst { check(serverJar.asFile.isFile) { "Build the shared language server with ./mvnw package first." } }
    from(serverJar) { into("server"); rename { "jx-language-server.jar" } }
    from("../../LICENSE") { into("META-INF") }
}

tasks.test {
    systemProperty("java.awt.headless", "true")
    systemProperty("idea.load.plugins.id", "dev.jx.language")
    systemProperty("jx.repositoryRoot", layout.projectDirectory.dir("../..").asFile.absolutePath)
}
