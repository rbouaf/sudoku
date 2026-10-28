plugins { kotlin("jvm") version "2.4.0" }

val app = file("../../app/src")
sourceSets {
    main { kotlin.srcDir("$app/main/java/app/materialsudoku/engine"); kotlin.srcDir("src/main/kotlin") }
    test { kotlin.srcDir("$app/test/java/app/materialsudoku/engine") }
}
kotlin { jvmToolchain(21) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}
tasks.test {
    maxHeapSize = "2g"
    testLogging { events("failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    systemProperty("calibrate", System.getProperty("calibrate") ?: "")
    System.getProperty("levels")?.let { systemProperty("levels", it) }
}
tasks.register<JavaExec>("run") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(System.getProperty("main") ?: "app.materialsudoku.engine.tools.BankToolKt")
    args = (System.getProperty("args") ?: "").split(" ").filter { it.isNotEmpty() }
    maxHeapSize = "4g"
}
