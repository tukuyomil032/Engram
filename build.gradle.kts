plugins {
    Java
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
}

dependencies {
    api(libs.commons.math3)
    implementation(libs.guava)
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

    manifest {
        attributes(
            "paperweight-mappings-namespace" to "spigot"
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("Engram")
    archiveClassifier.set("all")
}

tasks {
  runServer {
    minecraftVersion("1.21.11")
  }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
