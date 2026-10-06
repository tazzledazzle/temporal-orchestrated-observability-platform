plugins {
    scala
    id("io.gatling.gradle") version "3.12.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Workflow interfaces for typed stubs in simulations
    gatling(project(":workflows"))
    // Temporal Java SDK — same version as worker
    gatling("io.temporal:temporal-sdk:1.25.2")
    gatling("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    gatling("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    gatling("ch.qos.logback:logback-classic:1.5.12")
}

gatling {
    // Pass tier and temporal.host as JVM system properties to simulations
    jvmArgs = mutableListOf(
        "-Dtier=${project.findProperty("tier") ?: "baseline"}",
        "-Xmx1g"
    )
}

// k6 exec task — reads thresholds.env into environment vars
tasks.register<Exec>("k6ObservabilityLoad") {
    group = "load-test"
    description = "Stress-test observability HTTP endpoints with k6"
    val reportDir = layout.buildDirectory.get().asFile.resolve("reports/k6")
    doFirst { reportDir.mkdirs() }
    commandLine(
        "k6", "run",
        "--summary-export", reportDir.resolve("summary.json").absolutePath,
        "src/k6/observability-http.js"
    )
    // Parse thresholds.env into environment variables for k6
    val thresholdsFile = rootProject.file("scripts/load/thresholds.env")
    if (thresholdsFile.exists()) {
        val props = thresholdsFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val idx = line.indexOf('=')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
        environment(props)
    }
}

// Aggregate task: Gatling simulations then k6
tasks.register("loadTest") {
    group = "load-test"
    description = "Run all load scenarios. Pass -Ptier=baseline|target|stress"
    dependsOn("gatlingRun")
    finalizedBy("k6ObservabilityLoad")
}
