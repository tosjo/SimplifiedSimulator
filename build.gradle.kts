import java.time.LocalDate

plugins {
    application
}

group = "simplifiedsimulator"
version = "1.4.1"

repositories {
    mavenCentral()
}

val jmeVersion = "3.9.0-stable"

dependencies {
    implementation("org.jmonkeyengine:jme3-core:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-desktop:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-lwjgl3:$jmeVersion")
    implementation("org.jmonkeyengine:jme3-effects:$jmeVersion")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
}

application {
    mainClass.set("simplifiedsimulator.Main")
    // LWJGL 3 has to run on the main thread on macOS.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
    }
}

tasks.test {
    useJUnitPlatform()
}

// The 2014 controller against the smart one, side by side, in one go: starts both Python
// controllers, opens the duel at the morning rush (in summer, so it is light) and stops the
// controllers when the window closes. Other options: ./gradlew duel --args="--start 17:00 ..."
tasks.register<JavaExec>("duel") {
    group = "application"
    description = "Runs our 2014 controller against the smart controller in a duel window."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("simplifiedsimulator.Main")
    val summer = LocalDate.now().withMonth(6).withDayOfMonth(21)
    val extra = (project.findProperty("duelArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: listOf()
    args(listOf("--duel", "external,external", "--port", "1337", "--start", "07:30", "--date", "$summer") + extra)
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
    // the window may be closed in any way; the controllers are stopped either way
    isIgnoreExitValue = true
    val controllers = mutableListOf<Process>()
    doFirst {
        val python = listOf("python3", "python", "py").firstOrNull { candidate ->
            runCatching { ProcessBuilder(candidate, "--version").start().waitFor() == 0 }.getOrDefault(false)
        } ?: throw GradleException("The duel needs Python 3.8 or newer on the PATH for the controllers")
        val logs = layout.buildDirectory.dir("duel").get().asFile.apply { mkdirs() }
        for ((script, port) in listOf("original2014.py" to 1337, "smart.py" to 1338)) {
            // --once: they stop by themselves when the window closes, even if Gradle is stopped
            controllers += ProcessBuilder(python, "tools/controller/$script", "--port", "$port", "--once")
                .directory(projectDir)
                .redirectErrorStream(true)
                .redirectOutput(File(logs, script.removeSuffix(".py") + ".log"))
                .start()
        }
        Thread.sleep(1500)  // let them start listening before the simulator connects
        controllers.filter { !it.isAlive }.forEach { _ ->
            logger.warn("A controller did not start; see build/duel/*.log (is the port in use?)")
        }
    }
    doLast {
        controllers.forEach { it.destroy() }
    }
}

// Measures one controller without a window; external controllers run in lockstep, for example:
//   ./gradlew showdown --args="--controller external --port 1338 --start 07:00 --duration 3600"
tasks.register<JavaExec>("showdown") {
    group = "verification"
    description = "Measures one signal controller (also an external one) without graphics."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("simplifiedsimulator.ShowdownRunner")
}

// Headless comparison of signal controllers, for example:
//   ./gradlew batch --args="--controllers fixed,actuated --seeds 3 --duration 3600"
tasks.register<JavaExec>("batch") {
    group = "application"
    description = "Runs the simulation without graphics and compares signal controllers."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("simplifiedsimulator.batch.BatchRunner")
}
