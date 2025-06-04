pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()

		maven("https://snapshots-repo.kordex.dev")
		maven("https://releases-repo.kordex.dev")
	}
	plugins {
		// Update this in libs.version.toml when you change it here.
		kotlin("jvm") version "2.1.21"
		kotlin("plugin.serialization") version "2.1.21"

		id("dev.kordex.gradle.kordex") version "1.7.1"

		id("com.github.johnrengelman.shadow") version "8.1.1"
	}
}

rootProject.name = "board-bot"
