plugins {
    java
}

repositories {
    mavenCentral()
}

val playwrightVersion = "1.63.0"
val junitVersion = "6.1.3"     // JUnit 6 requires Java 17+
val allureVersion = "3.0.0"    // Allure Java 3.x requires Java 17+
val jacksonVersion = "2.18.2"

dependencies {
    implementation("com.microsoft.playwright:playwright:$playwrightVersion")
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.univocity:univocity-parsers:2.9.1")
    implementation(platform("io.qameta.allure:allure-bom:$allureVersion"))
    implementation("io.qameta.allure:allure-java-commons")   // Allure API used by the framework code (attachments)

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.qameta.allure:allure-jupiter")    // Allure results writer for JUnit Jupiter
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

// one time browser download
tasks.register<JavaExec>("installBrowsers") {
    group = "verification"
    description = "Downloads the Playwright browsers and their OS dependencies"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args("install", "--with-deps")
}

// ---- PROJECT / ENV selection -------------------------------------------------
// Precedence: -Pproject=... , PROJECT env var, ../.env, default (same for ENV)
fun dotenv(key: String): String? {
    val file = rootDir.resolve("../.env")
    if (!file.exists()) return null
    return file.readLines().map { it.trim() }
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")?.trim()?.trim('"', '\'')
        ?.takeIf { it.isNotEmpty() }
}

val projectName: String = (findProperty("project") as String?) ?: System.getenv("PROJECT") ?: dotenv("PROJECT") ?: "project-a"
val envName: String = System.getenv("ENV") ?: dotenv("ENV") ?: "dev"
val projectPackage = projectName.replace("-", "")   // project-a -> projecta

tasks.test {
    useJUnitPlatform {
        // ./gradlew test -PincludeTags=api   (ui | api | e2e, comma-separated)
        (findProperty("includeTags") as String?)?.let { includeTags(*it.split(",").map(String::trim).toTypedArray()) }
    }
    // only run the selected project's tests: automation.tests.<project>*
    filter { includeTestsMatching("automation.tests.$projectPackage.*") }

    // ./gradlew test -Pparallel=false runs serially (parallel is on by default, see junit-platform.properties)
    (findProperty("parallel") as String?)?.let { systemProperty("junit.jupiter.execution.parallel.enabled", it) }

    environment("PROJECT", projectName)
    environment("ENV", envName)

    // Allure raw results end up in build/allure-results (see src/test/resources/allure.properties)
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
