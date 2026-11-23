plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.collektive)
    alias(libs.plugins.dokka)
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.kotlin.qa)
    // alias(libs.plugins.publishOnCentral) // Publishing disabled for now, see the end of the file
}

group = "it.unibo.collektive"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    jvm()
    js { nodejs() }
    linuxX64()
    macosArm64()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.collektive.dsl)
            implementation(libs.collektive.stdlib)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Publishing on Maven Central, disabled for now: to enable it, uncomment this and the plugin above
// publishOnCentral {
//     repoOwner.set("Collektive")
//     projectLongName.set("Collektive Swarms")
//     projectDescription.set(
//         "Aggregate programming library for swarm shape formation: shapes as Signed Distance Functions (SDF), " +
//             "formed by decoupled positioning, coverage control, and actuation strategies on Collektive",
//     )
//     licenseName.set("Apache License, Version 2.0")
//     licenseUrl.set("https://www.apache.org/licenses/LICENSE-2.0")
// }
//
// publishing {
//     publications {
//         withType<MavenPublication> {
//             pom {
//                 developers {
//                     developer {
//                         name.set("Angela Cortecchia")
//                         email.set("angela.cortecchia@unibo.it")
//                         url.set("https://github.com/angelacorte")
//                     }
//                     developer {
//                         name.set("Gianluca Aguzzi")
//                         email.set("gianluca.aguzzi@unibo.it")
//                         url.set("https://gianluca.aguzzi.dev/")
//                     }
//                     developer {
//                         name.set("Danilo Pianini")
//                         email.set("danilo.pianini@gmail.com")
//                         url.set("http://www.danilopianini.org/")
//                     }
//                 }
//             }
//         }
//     }
// }
//
// signing {
//     val signingKey: String? by project
//     val signingPassword: String? by project
//     if (signingKey != null) {
//         useInMemoryPgpKeys(signingKey, signingPassword)
//     } else {
//         isRequired = false // Local builds (e.g., publishToMavenLocal) without keys
//     }
// }
