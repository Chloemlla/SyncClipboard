pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Preferred CI/local path: release assets synced into a file Maven repo.
        // Avoids cross-repo GitHub Packages auth requirements.
        val localLumenCrashRepo = providers.environmentVariable("LUMEN_CRASH_MAVEN_DIR")
            .orElse(providers.gradleProperty("lumenCrashMavenDir"))
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?: rootDir.resolve(".m2-lumen-crash").absolutePath
        maven {
            name = "LumenCrashReleaseMaven"
            url = uri(localLumenCrashRepo)
            content {
                includeGroup("com.chloemlla.lumen")
            }
        }

        // Optional fallback: only register when credentials are actually present.
        // Empty credentials can produce noisy 401s and hide the file-repo path.
        val gprUser = providers.gradleProperty("gpr.user")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
            .orElse(providers.environmentVariable("GH_USER"))
            .orNull
            ?.takeIf { it.isNotBlank() }
        val gprKey = providers.gradleProperty("gpr.key")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
            .orElse(providers.environmentVariable("GH_TOKEN"))
            .orNull
            ?.takeIf { it.isNotBlank() }
        if (gprUser != null && gprKey != null) {
            maven {
                name = "GitHubPackagesLumenCrash"
                url = uri("https://maven.pkg.github.com/Chloemlla/Project-Lumen")
                credentials {
                    username = gprUser
                    password = gprKey
                }
                content {
                    includeGroup("com.chloemlla.lumen")
                }
            }
        }
    }
}

rootProject.name = "SyncClipboardMobile"
include(":app")
