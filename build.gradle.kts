import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.5" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}

// Static analysis is configured here to preserve the app's release-test wiring.
project(":app") {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        toolVersion = "1.23.5"
        buildUponDefaultConfig = true
        ignoreFailures = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline.xml")
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
        }
    }
    dependencies {
        "detektPlugins"("io.nlopez.compose.rules:detekt:0.3.11")
    }
}
