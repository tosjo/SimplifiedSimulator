plugins {
    application
}

group = "simplifiedsimulator"
version = "1.3.0"

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
