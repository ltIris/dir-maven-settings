plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.dirmaven"
version = "1.1.2"

val ideaHome = providers.gradleProperty("ideaHome").orElse(providers.environmentVariable("IDEA_HOME")).orNull

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("252")
            untilBuild.set("263.*")
        }
    }
    instrumentCode.set(false)
}

dependencies {
    intellijPlatform {
        if (ideaHome != null) {
            local(ideaHome)
        } else {
            intellijIdeaUltimate("2025.2.5")
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")
    }
}

// The IDE distribution already supplies these platform modules; importing them again creates duplicate IDE class roots.
configurations.named("compileClasspath") {
    exclude(group = "bundledModule")
}

tasks {
    // 构建插件分发包：dist/directory-maven-settings-1.1.2.zip
    buildPlugin {
        archiveBaseName.set("directory-maven-settings")
        archiveVersion.set(project.version.toString())
        destinationDirectory.set(layout.projectDirectory.dir("dist"))
    }

    // 自检任务：java -cp ... dev.dirmaven.core.MatcherSelfCheck
    val selfCheck by registering(JavaExec::class) {
        mainClass.set("dev.dirmaven.core.MatcherSelfCheck")
        classpath = sourceSets["main"].runtimeClasspath
    }

    // 编译选项
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:all")
    }
}
