import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.RunConfigurationContainer

plugins {
  id("java-library")
  id("maven-publish")
  id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
  id("eclipse")
  id("com.gtnewhorizons.retrofuturagradle") version "2.0.2"
}

group = "com.dynamicwinds"
version = "1.0.0"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(8))
  }
  withSourcesJar()
  withJavadocJar()
}

minecraft {
  mcVersion.set("1.7.10")
  username.set("Developer")
  injectedTags.put("VERSION", project.version)
  extraRunJvmArguments.add("-ea:${project.group}")
  groupsToExcludeFromAutoReobfMapping.addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft")
}

tasks.injectTags.configure {
  outputClassName.set("${project.group}.Tags")
}

tasks.processResources.configure {
  val projVersion = project.version.toString()
  inputs.property("version", projVersion)
  filesMatching("mcmod.info") {
    expand(mapOf("modVersion" to projVersion))
  }
}

val runtimeOnlyNonPublishable: Configuration by configurations.creating {
  description = "Runtime only dependencies that are not published alongside the jar"
  isCanBeConsumed = false
  isCanBeResolved = false
}
listOf(configurations.runtimeClasspath, configurations.testRuntimeClasspath).forEach {
  it.configure { extendsFrom(runtimeOnlyNonPublishable) }
}

repositories {
  maven { name = "OvermindDL1 Maven"; url = uri("https://gregtech.overminddl1.com/") }
  maven { name = "GTNH Maven"; url = uri("https://nexus.gtnewhorizons.com/repository/public/") }
  mavenCentral()
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
}

publishing {
  publications {
    create<MavenPublication>("maven") { from(components["java"]) }
  }
}
