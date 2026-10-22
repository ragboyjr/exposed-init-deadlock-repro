plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:1.6.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    runtimeOnly("com.h2database:h2:2.3.232")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "repro.MainKt"
}
