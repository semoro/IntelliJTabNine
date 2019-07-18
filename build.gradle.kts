import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    //id "org.jetbrains.intellij" version "0.4.9"
    id("org.jetbrains.intellij") version "0.4.9"
    id("kotlinx-serialization") version "1.3.41"
    kotlin("jvm") version "1.3.41"
}

group = "me.semoro.tabnine"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
    jcenter()
}

intellij {
    version = "IC-2019.1"
    pluginName = "TabNine support"

}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    compile("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.11.1")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val runIde: JavaExec by tasks
runIde.maxHeapSize = "2g"

