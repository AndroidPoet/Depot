import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "dev.androidpoet.depot.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Depot"
            packageVersion = "0.1.0"
            description = "F-Droid client that installs to a phone over adb"
            vendor = "AndroidPoet"
            licenseFile.set(rootProject.file("LICENSE"))
            includeAllModules = true
            macOS {
                bundleID = "dev.androidpoet.depot"
                packageVersion = "1.0.0"
            }
            windows {
                menuGroup = "Depot"
                perUserInstall = true
                upgradeUuid = "6a22596e-20d5-4530-9f83-cb6138e1b8bb"
            }
            linux {
                packageName = "depot"
            }
        }
    }
}
