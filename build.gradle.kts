plugins {
    java
    id("com.gradleup.shadow") version "9.2.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.tukuyomil032.engram"
version = "0.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "lumine"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.mythic.dist) { isTransitive = false }
    compileOnly(libs.mythic.crucible) { isTransitive = false }
    implementation(libs.sqlite.jdbc)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.paper.api)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("engram")
}

tasks.shadowJar {
    archiveBaseName.set("Engram")
    archiveClassifier.set("all")
    relocate("org.sqlite", "com.tukuyomil032.engram.lib.sqlite")
}

tasks {
    runServer {
        minecraftVersion("1.21.4")
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
