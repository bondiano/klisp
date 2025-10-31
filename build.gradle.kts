plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "com.bondiano"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("org.jline:jline:3.27.1")
    implementation("io.arrow-kt:arrow-core:2.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(22)

    compilerOptions {
        allWarningsAsErrors = false
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

sourceSets {
    main {
        kotlin.srcDirs("src")
    }
    test {
        kotlin.srcDirs("test")
    }
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

tasks {
    test {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
            showExceptions = true
            showCauses = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    jar {
        manifest {
            attributes["Main-Class"] = "MainKt"
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}