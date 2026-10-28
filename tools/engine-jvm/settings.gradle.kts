// A JVM-only build of the engine package, so it can be tested and benchmarked without Android,
// and so the offline bank generator can run on a desktop. Sources are the app's own, not copies.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "engine-jvm"
