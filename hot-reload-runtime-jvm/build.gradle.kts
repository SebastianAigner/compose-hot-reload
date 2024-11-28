@file:OptIn(ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    `maven-publish`
    `publishing-conventions`
    `dev-runtime-jar`
}

kotlin {
    compilerOptions {
        explicitApi()
    }
}

dependencies {
    sharedImplementation(compose.runtime)

    devCompileOnly(project(":hot-reload-agent"))
    devCompileOnly(project(":hot-reload-runtime-api"))
    devImplementation(project(":hot-reload-orchestration"))
    devImplementation(deps.coroutines.swing)

    devImplementation(deps.javassist)
    devImplementation(deps.asm)
    devImplementation(deps.asm.tree)
    devImplementation(deps.slf4j.api)
    devImplementation(compose.desktop.common)
    devImplementation(compose.material3)
    devImplementation(compose.components.resources)

    testImplementation(kotlin("test"))
    testImplementation(deps.junit.jupiter)
    testImplementation(deps.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications.register<MavenPublication>("maven") {
        from(components["java"])
    }
}
