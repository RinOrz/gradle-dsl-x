@file:Suppress("SpellCheckingInspection")

plugins { id("com.gradle.plugin-publish") }

allprojects {
  group = "com.meowool.toolkit"
  version = "1.1-SNAPSHOT"
}

subprojects {
  if (!buildFile.exists()) return@subprojects

  apply(plugin = "com.gradle.plugin-publish")
  mavenPublishSO1()

  pluginBundle {
    website = findProperty("POM_URL")!!.toString()
    vcsUrl = findProperty("POM_SCM_URL")!!.toString()
    tags = listOf("gradle-dsl", "gradle-utils", "gradle-toolkit", "kotlin", "kotlin-dsl", "ktx")
  }

  afterEvaluate {
    // Don't let the fork of 'refreshVersion' spotless.
    if (projectDir.absolutePath.endsWith("dependencies/updater")) {
      tasks.findByName("spotlessApply")?.enabled = false
      tasks.findByName("spotlessKotlin")?.enabled = false
    }
  }

  tasks.findByName("dokkaHtml")?.enabled = false
  tasks.withType<Test> { useJUnit() }
}