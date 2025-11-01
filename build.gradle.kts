plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.allopen") version "2.2.21"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.14"
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

    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.14")
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

    val main by getting

    create("benchmark") {
        kotlin.srcDirs("benchmark")
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += main.output + main.runtimeClasspath
    }
}

application {
    mainClass.set("com.bondiano.klisp.MainKt")
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
            attributes["Main-Class"] = "com.bondiano.klisp.MainKt"
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}

// All-open plugin configuration - required for JMH benchmarks
// JMH needs to subclass benchmark classes, so they must be open
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("benchmark")
    }

    configurations {
        named("main") {
            warmups = 10
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "ms"
            // Increase stack size to 10MB to handle deep recursion
            param("jvmArgs", "-Xss10m")
        }
        register("smoke") {
            warmups = 3
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
            outputTimeUnit = "ms"
            // Increase stack size to 10MB to handle deep recursion
            param("jvmArgs", "-Xss10m")
        }
    }
}