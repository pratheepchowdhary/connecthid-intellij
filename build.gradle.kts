plugins {
  id("org.jetbrains.kotlin.jvm") version "2.2.0-RC"
  id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "com.connecthid"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()

  }
}
kotlin {
  sourceSets {
    main {
      kotlin.srcDir("build/generated/src/")
    }
  }
}


dependencies {
  implementation("com.jcraft:jsch:0.1.55")
  implementation("com.github.docker-java:docker-java-core:3.3.4")
  implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")
  implementation("org.postgresql:postgresql:42.6.0")
  implementation("mysql:mysql-connector-java:8.0.33")
  implementation("org.mongodb:mongodb-driver-sync:4.10.2")
  implementation("io.micrometer:micrometer-core:1.11.3")
  implementation("io.micrometer:micrometer-registry-prometheus:1.11.3")

  intellijPlatform {
    intellijIdeaCommunity("2025.1")
    bundledPlugin("org.jetbrains.plugins.terminal")
  }
}


tasks {
  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}