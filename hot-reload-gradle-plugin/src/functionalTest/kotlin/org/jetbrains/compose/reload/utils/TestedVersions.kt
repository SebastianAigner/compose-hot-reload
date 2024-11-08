package org.jetbrains.compose.reload.utils

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.junit.jupiter.api.extension.ExtensionContext

enum class TestedGradleVersion(val version: GradleVersion) {
    G_8_7(GradleVersion.version("8.7")),
    G_8_10(GradleVersion.version("8.10.2"));

    override fun toString(): String {
        return version.version
    }
}

enum class TestedKotlinVersion(val version: KotlinToolingVersion) {
    KT_2_0(KotlinToolingVersion("2.0.21")),
    KT_2_1(KotlinToolingVersion("2.1.255-SNAPSHOT")), ;

    override fun toString(): String {
        return version.toString()
    }
}

enum class TestedComposeVersion(val version: String) {
    C_1_7(KotlinToolingVersion("1.7.1").toString()), ;

    override fun toString(): String {
        return version
    }
}

enum class TestedAndroidVersion(val version: String) {
    AGP_8_5("8.5.2"),
    AGP_8_7("8.7.1");

    override fun toString(): String {
        return version
    }
}

var ExtensionContext.kotlinVersion: TestedKotlinVersion? by extensionContextProperty()
var ExtensionContext.gradleVersion: TestedGradleVersion? by extensionContextProperty()
var ExtensionContext.composeVersion: TestedComposeVersion? by extensionContextProperty()
var ExtensionContext.androidVersion: TestedAndroidVersion? by extensionContextProperty()