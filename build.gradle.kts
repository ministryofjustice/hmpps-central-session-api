plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.2.4"
  kotlin("plugin.spring") version "1.8.21"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux:3.1.2")
  implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive:3.1.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}
