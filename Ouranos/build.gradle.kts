import org.gradle.kotlin.dsl.dependencies

plugins {
    id("java-library")
    id("com.gradleup.shadow") version("8.3.0")
}

group = "com.github.blackjack200.ouranos"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.maven.apache.org/maven2/")
    maven("https://jitpack.io")
}

dependencies {
    api(libs.org.allaymc.stateupdater.common)
    api(libs.org.allaymc.stateupdater.block.updater)
    api(libs.org.cloudburstmc.protocol.bedrock.connection)
    api(libs.cn.hutool.hutool.core)
    api(libs.org.projectlombok.lombok)
    api(libs.com.google.code.gson.gson)

    annotationProcessor(libs.org.projectlombok.lombok)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        dependencies {
            include(dependency("org.allaymc.stateupdater:common:0.1.1"))
            include(dependency("org.allaymc.stateupdater:block-updater:1.21.110-R1"))
            include(dependency("cn.hutool:hutool-core:5.8.40"))
            include(dependency("org.cloudburstmc.protocol:bedrock-codec:3.0.0.Beta12-SNAPSHOT"))
            include(dependency("org.cloudburstmc.protocol:common:3.0.0.Beta12-SNAPSHOT"))
            include(dependency("org.cloudburstmc.protocol:bedrock-connection:3.0.0.Beta12-SNAPSHOT"))
            include(dependency("org.cloudburstmc.fastutil:core:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.sets:object-sets:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.sets:long-sets:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.sets:int-sets:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.maps:object-int-maps:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.maps:long-object-maps:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.maps:int-object-maps:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.commons:object-common:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.commons:long-common:8.5.15"))
            include(dependency("org.cloudburstmc.fastutil.commons:int-common:8.5.15"))

            include(dependency("com.google.code.gson:gson:2.13.2"))
            include(dependency("org.projectlombok:lombok:1.18.36"))

        }

        relocate("it.unimi.dsi.fastutil", "com.github.blackjack200.ouranos.shaded.fastutil")
        relocate("org.cloudburstmc.protocol", "com.github.blackjack200.ouranos.shaded.protocol")
        relocate("com.google.gson", "com.github.blackjack200.ouranos.shaded.gson")
        relocate("lombok", "com.github.blackjack200.ouranos.shaded.lombok")
        relocate("org.allaymc", "com.github.blackjack200.ouranos.shaded.allymc")
        relocate("cn.hutool", "com.github.blackjack200.ouranos.shaded.hutool")
    }
}
