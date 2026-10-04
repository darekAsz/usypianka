// Standalone UAT helper (CR-01): a second app with its own uid that tries to drive PlaybackService.
// Not part of the Usypianka build. Run: ./gradlew -p tools/controller-probe installDebug
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "controller-probe"
