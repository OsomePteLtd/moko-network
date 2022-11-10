/*
 * Copyright 2021 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */
enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
    repositories {


        mavenCentral()
        google()
        gradlePluginPortal()


    }
}

dependencyResolutionManagement {
    repositories {

        val githubUser = ""
        val githubToken = ""

        maven {
            setUrl("https://maven.pkg.github.com/OsomePteLtd/kmp-mobile-shared")
            credentials {
                username = githubUser
                password = githubToken
            }
        }
        mavenCentral()
        google()

    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
