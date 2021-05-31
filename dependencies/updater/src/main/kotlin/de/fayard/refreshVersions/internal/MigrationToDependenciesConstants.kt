/*
 * Copyright (c) 2021. The Meowool Organization Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * In addition, if you modified the project, you must include the Meowool
 * organization URL in your code file: https://github.com/meowool
 */
package de.fayard.refreshVersions.internal

import de.fayard.refreshVersions.core.internal.ArtifactVersionKeyReader
import de.fayard.refreshVersions.core.internal.InternalRefreshVersionsApi
import de.fayard.refreshVersions.core.internal.RefreshVersionsConfigHolder
import de.fayard.refreshVersions.core.internal.cli.AnsiColor
import de.fayard.refreshVersions.core.internal.cli.CliGenericUi
import de.fayard.refreshVersions.core.internal.hasHardcodedVersion
import kotlinx.coroutines.isActive
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import kotlin.coroutines.coroutineContext

@InternalRefreshVersionsApi
fun Configuration.shouldBeIgnored(): Boolean {
  return name.startsWith(prefix = "_internal") || // Real-life example: _internal_aapt2_binary (introduced by AGP)
    name in ignoredConfigurationNames || name.startsWith('-')
  // TODO: If unwanted configurations still get through, we can filter to known ones here, like
  // implementation, api, compileOnly, runtimeOnly, kapt, plus test, MPP and MPP test variants.
}

private val ignoredConfigurationNames = listOf(
  "kotlinCompilerPluginClasspath",
  "kotlinKaptWorkerDependencies",
  "lintClassPath"
)

// TODO: Ignore the following dependency: org.jetbrains.kotlin:kotlin-android-extensions-runtime

@InternalRefreshVersionsApi
fun Configuration.countDependenciesWithHardcodedVersions(
  versionsMap: Map<String, String>,
  versionKeyReader: ArtifactVersionKeyReader
): Int = dependencies.count { dependency ->
  dependency is ExternalDependency && dependency.hasHardcodedVersion(versionsMap, versionKeyReader)
}

@InternalRefreshVersionsApi
fun Project.countDependenciesWithHardcodedVersions(versionsMap: Map<String, String>): Int {
  val versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader
  return configurations.sumBy { configuration ->
    if (configuration.shouldBeIgnored()) 0 else {
      configuration.countDependenciesWithHardcodedVersions(versionsMap, versionKeyReader)
    }
  }
}

internal fun promptProjectSelection(rootProject: Project): Project? {
  require(rootProject == rootProject.rootProject) { "Expected a rootProject but got $rootProject" }
  val versionsMap = RefreshVersionsConfigHolder.readVersionsMap()
  val projectsWithHardcodedDependenciesVersions: List<Pair<Project, Int>> = rootProject.allprojects.mapNotNull {
    val hardcodedDependenciesVersionsCount = it.countDependenciesWithHardcodedVersions(versionsMap)
    if (hardcodedDependenciesVersionsCount > 0) {
      it to hardcodedDependenciesVersionsCount
    } else null
  }
  val cliGenericUi = CliGenericUi()
  val index = cliGenericUi.showMenuAndGetIndexOfChoice(
    header = "All the following modules have hardcoded dependencies versions",
    footer = "Type the number of the Gradle module you want to migrate first:",
    numberedEntries = projectsWithHardcodedDependenciesVersions.map { (project, hardcodedVersionsCount) ->
      "${project.path} ($hardcodedVersionsCount)"
    } + "Exit"
  )
  return projectsWithHardcodedDependenciesVersions.getOrNull(index.value)?.first
}

internal suspend fun runInteractiveMigrationToDependenciesConstants(project: Project) {
  val versionsMap = RefreshVersionsConfigHolder.readVersionsMap()
  while (coroutineContext.isActive) {
    val selectedConfiguration = project.promptConfigurationSelection(versionsMap) ?: return
    runConfigurationDependenciesMigration(
      project,
      versionsMap,
      selectedConfiguration
    )
  }
}

private fun Project.promptConfigurationSelection(versionsMap: Map<String, String>): Configuration? {
  @Suppress("UnstableApiUsage")
  val versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader
  val configurationsWithHardcodedDependenciesVersions = configurations.mapNotNull { configuration ->
    if (configuration.shouldBeIgnored()) return@mapNotNull null
    val count = configuration.countDependenciesWithHardcodedVersions(versionsMap, versionKeyReader)
    return@mapNotNull if (count == 0) null else configuration to count
  }
  val cliGenericUi = CliGenericUi()
  val index = cliGenericUi.showMenuAndGetIndexOfChoice(
    header = "${AnsiColor.WHITE.boldHighIntensity}${AnsiColor.GREEN.background}" +
      "You selected project $path" +
      "${AnsiColor.RESET}\n" +
      "The following configurations have dependencies with hardcoded versions",
    footer = "Type the number of the configuration you want to migrate first:",
    numberedEntries = configurationsWithHardcodedDependenciesVersions.map { (configuration, count) ->
      "${configuration.name} ($count)"
    } + "Back"
  )
  return configurationsWithHardcodedDependenciesVersions.getOrNull(index.value)?.first
}
