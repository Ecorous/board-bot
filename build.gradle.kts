import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	application

	kotlin("jvm")
	kotlin("plugin.serialization")

	id("dev.kordex.gradle.kordex")
	id("com.github.johnrengelman.shadow")
}

group = "org.ecorous.boardbot"
version = "0.1.0"

repositories {
	google()
	mavenCentral()

	maven {
		name = "Sonatype Snapshots (Legacy)"
		url = uri("https://oss.sonatype.org/content/repositories/snapshots")
	}

	maven {
		name = "Sonatype Snapshots"
		url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
	}
}

dependencies {
	implementation(libs.kotlin.stdlib)
	implementation(libs.kx.ser)
//	implementation(libs.kord.extensions)

	// Database dependencies
	implementation(libs.bundles.exposed)

	// Logging dependencies
	implementation(libs.groovy)
	implementation(libs.jansi)
	implementation(libs.logback)
	implementation(libs.logback.groovy)
	implementation(libs.logging)
}

application {
	mainClass.set("org.ecorous.boardbot.AppKt")
}

tasks.withType<KotlinCompile> {
	// Current LTS version of Java
	kotlinOptions.jvmTarget = "21"

	kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.jar {
	manifest {
		attributes(
			"Main-Class" to "org.ecorous.boardbot.AppKt"
		)
	}
}

kordEx {
	i18n {
		classPackage = "org.ecorous.boardbot.i18n"
		translationBundle = "boardbot.strings"
	}
}

java {
	// Current LTS version of Java
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}
