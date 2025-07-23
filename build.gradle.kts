plugins {
    java
    alias(libs.plugins.lavalink)
}

group = "net.nennneko5787.nndplugin"
version = "0.0.1"

base {
	archivesName = "niconico-plugin"
}

lavalinkPlugin {
    apiVersion = libs.versions.lavalink.api
    serverVersion = libs.versions.lavalink.server
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}

dependencies {
    implementation("commons-io:commons-io:2.20.0")
}
