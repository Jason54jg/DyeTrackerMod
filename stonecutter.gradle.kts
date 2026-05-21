plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter parameters {
    // Define cross-version swaps here as source-level differences are discovered (29-4+).
    // Example:
    //
    // swaps["render_call"] = when {
    //     current.parsed >= "1.21.11" -> "renderV2()"
    //     else -> "renderV1()"
    // }
}

stonecutter active "1.21.10"

// Aggregate `build` across every registered version. Stonecutter 0.7+ no longer
// auto-generates this; we register it manually so `./gradlew chiseledBuild`
// produces all per-version jars in one invocation.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every registered Stonecutter version's :build task."
    dependsOn(stonecutter.tasks.named("build"))
}
