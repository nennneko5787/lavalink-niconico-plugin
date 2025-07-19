plugins {
    java
    alias(libs.plugins.lavalink)
}

group = "com.nennneko5787.nndplugin"
version = "0.0.1"

base {
	archivesName = "NicoNicoPlugin"
}

lavalinkPlugin {
    name = "NicoNicoPlugin"
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
	compileOnly("com.github.topi314.lavasearch:lavasearch:1.0.0")
	implementation("com.github.topi314.lavasearch:lavasearch-plugin-api:1.0.0")
    implementation("commons-io:commons-io:2.20.0")
}
