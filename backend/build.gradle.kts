plugins {
    kotlin("jvm") version "1.8.0"
    id("application")
}

repositories {
    mavenCentral()
}

val ktor_version = "2.3.3"
val exposed_version = "0.41.1"
val hikari_version = "5.0.1"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktor_version")

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.postgresql:postgresql:42.6.0")

    implementation("redis.clients:jedis:4.4.3")

    implementation("ch.qos.logback:logback-classic:1.4.7")

    implementation("com.auth0:java-jwt:4.4.0")

    implementation("io.minio:minio:8.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.tawaasol.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}
