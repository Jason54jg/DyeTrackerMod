plugins {
    id("dev.kikugie.stonecutter")
    // Minecraft dropped obfuscation starting with 26.1 (no more mappings, no remapping).
    // Loom ships two plugin ids off the same artifact: `-remap` for the older, obfuscated
    // versions (anything with a yarn_mappings entry) and the plain id for 26.1+.
    id("net.fabricmc.fabric-loom-remap") version "1.16.2" apply false
    id("net.fabricmc.fabric-loom") version "1.16.2" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

// Presence of yarn_mappings in versions/<mc>/gradle.properties is what tells obfuscated
// versions apart from the unobfuscated 26.x line (see also the dependencies block below).
val isObfuscated = findProperty("yarn_mappings") != null
apply(plugin = if (isObfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // The `minecraft`/`mappings`/`mod*` configurations come from whichever Loom plugin variant
    // got applied above. Since that plugin is applied dynamically (not declared in the static
    // plugins{} block), Kotlin DSL never generates type-safe accessors for them — go through
    // the untyped DependencyHandler.add(configuration, notation) API instead.

    // Minecraft version comes from the active Stonecutter subproject name.
    add("minecraft", "com.mojang:minecraft:${stonecutter.current.version}")

    // 26.x ships no mappings at all (client/server jars are already deobfuscated), so the
    // mappings dependency only applies to the obfuscated, Yarn-mapped versions.
    if (isObfuscated) {
        add("mappings", "net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    }

    // mod* configurations only exist under the -remap plugin; unobfuscated versions use the
    // plain configurations since there's nothing to remap.
    if (isObfuscated) {
        add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        add("modImplementation", "net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    } else {
        implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    }

    // Unit testing (pure JVM; no Minecraft/Fabric runtime).
    // See docs/delivery/33/33-2-junit-guide.md for rationale and coordinates.
    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// `the<T>()` looks the extension up by type at runtime instead of relying on a precompiled
// `loom { }` accessor, which — same reason as the dependencies block above — doesn't exist
// for a dynamically-applied plugin.
the<net.fabricmc.loom.api.LoomGradleExtensionAPI>().apply {
    // Mandatory for the Stonecutter shared-source layout — without this, Loom expects
    // fabric.mod.json under each version subproject's resources rather than the shared root.
    fabricModJsonPath.set(rootProject.file("src/main/resources/fabric.mod.json"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// 26.x runs on java-runtime-epsilon (Java 25), but a JVM always runs older bytecode fine, and
// the Kotlin compiler doesn't support a JVM_25 target yet — so every version still compiles to
// release 21 and just runs under whichever JRE launches the game.
tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
