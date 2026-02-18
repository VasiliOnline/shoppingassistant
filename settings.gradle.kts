rootProject.name = "shoppingassistant"

include(":app")
include(":core")
include(":feature")
include(":server")
include(":domain")
include(":rank")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
