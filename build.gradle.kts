import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.1.0"
  id("org.jetbrains.intellij") version "1.17.4"
  id("io.sentry.jvm.gradle") version "5.3.0"
}

group = "com.joltai"
version = "0.2.32"

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation("org.json:json:20250107")
  implementation("io.sentry:sentry:5.3.0")
  implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
}

intellij {
  version.set("2024.1.7")
  type.set("IC")
  plugins.set(listOf(/* Plugin Dependencies */))

  // Explicitly tell the plugin system to include Sentry dependencies
  instrumentCode.set(false)
}

tasks.register<Copy>("copyEnvFile") {
  from(".env")
  into(layout.buildDirectory.dir("resources/main"))
}

tasks.named("processResources") {
  dependsOn("copyEnvFile")
}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
      freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
  }

  patchPluginXml {
    sinceBuild.set("241")
    untilBuild.set("243.*")
  }

  signPlugin {
    certificateChain.set(System.getProperty("JBB_CERTIFICATE_CHAIN"))
    privateKey.set(System.getProperty("JBB_PRIVATE_KEY"))
    password.set(System.getProperty("JBB_PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getProperty("JBB_PUBLISH_TOKEN"))
  }
}
