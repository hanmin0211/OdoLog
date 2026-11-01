import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

dependencies {
    implementation("io.ktor:ktor-server-netty:3.1.3")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test"))
}

java { targetCompatibility = JavaVersion.VERSION_21 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
application { mainClass.set("odolog.server.MainKt") }
tasks.test { useJUnitPlatform() }
