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
   // implementation("com.connecthid.sshjpool:SshJPool:1.0.0")
    implementation("org.apache.commons:commons-pool2:2.12.1")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("Git4Idea")
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