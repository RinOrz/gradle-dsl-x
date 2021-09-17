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

 * In addition, if you modified the project, you must include the Meowool
 * organization URL in your code file: https://github.com/meowool
 *
 * 如果您修改了此项目，则必须确保源文件中包含 Meowool 组织 URL: https://github.com/meowool
 */
package de.fayard.refreshVersions.core

import de.fayard.refreshVersions.core.internal.OutputFile
import de.fayard.refreshVersions.core.internal.RefreshVersionsConfigHolder
import de.fayard.refreshVersions.core.internal.RefreshVersionsConfigHolder.settings
import de.fayard.refreshVersions.core.internal.SettingsPluginsUpdater
import de.fayard.refreshVersions.core.internal.configureLintIfRunningOnAnAndroidProject
import de.fayard.refreshVersions.core.internal.legacy.LegacyBootstrapUpdater
import de.fayard.refreshVersions.core.internal.lookupVersionCandidates
import de.fayard.refreshVersions.core.internal.problems.log
import de.fayard.refreshVersions.core.internal.versions.VersionsPropertiesModel
import de.fayard.refreshVersions.core.internal.versions.writeWithNewVersions
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.util.GradleVersion

/**
 *
 * $ ./gradlew help --task refreshVersions
 * Description
 * Search for new dependencies versions and update versions.properties
 *
 * OPTIONS
 *     --enable <FEATURE_FLAG>
 *     --disable <FEATURE_FLAG>
 */
open class RefreshVersionsTask : DefaultTask() {

  @Input @Optional
  @Option(option = "enable", description = "Enable a feature flag")
  var enableFlag: FeatureFlag? = null
    set(value) {
      field = value
      if (value != null) FeatureFlag.userSettings.put(value, true)
    }

  @Input @Optional
  @Option(option = "disable", description = "Disable a feature flag")
  var disableFlag: FeatureFlag? = null
    set(value) {
      field = value
      if (value != null) FeatureFlag.userSettings.put(value, false)
    }

  @TaskAction
  fun taskActionRefreshVersions() {
    OutputFile.checkWhichFilesExist(project.rootDir)

    if (FeatureFlag.userSettings.isNotEmpty()) {
      logger.lifecycle("Feature flags: " + FeatureFlag.userSettings)
    }
    // TODO: Filter using known grouping strategies to only use the main artifact to resolve latest version, this
    // will reduce the number of repositories lookups, improving performance a little more.

    runBlocking {
      val lintUpdatingProblemsAsync = async {
        configureLintIfRunningOnAnAndroidProject(settings, RefreshVersionsConfigHolder.readVersionsMap())
      }
      val result = lookupVersionCandidates(
        httpClient = RefreshVersionsConfigHolder.httpClient,
        project = project,
        versionMap = RefreshVersionsConfigHolder.readVersionsMap(),
        versionKeyReader = RefreshVersionsConfigHolder.versionKeyReader
      )
      VersionsPropertiesModel.writeWithNewVersions(result.dependenciesUpdates)
      SettingsPluginsUpdater.updateGradleSettingsWithAvailablePluginsUpdates(
        rootProject = project,
        settingsPluginsUpdates = result.settingsPluginsUpdates,
        buildSrcSettingsPluginsUpdates = result.buildSrcSettingsPluginsUpdates
      )
      result.selfUpdatesForLegacyBootstrap?.let {
        LegacyBootstrapUpdater.updateGradleSettingsWithUpdates(
          rootProject = project,
          selfUpdates = it
        )
      }

      warnAboutRefreshVersionsIfSettingIfAny()
      warnAboutHardcodedVersionsIfAny(result.dependenciesWithHardcodedVersions)
      warnAboutDynamicVersionsIfAny(result.dependenciesWithDynamicVersions)
      warnAboutGradleUpdateAvailableIfAny(result.gradleUpdates)
      lintUpdatingProblemsAsync.await().forEach { problem ->
        logger.log(problem)
      }
      OutputFile.VERSIONS_PROPERTIES.logFileWasModified()
    }
  }

  private fun warnAboutRefreshVersionsIfSettingIfAny() {
    if (RefreshVersionsConfigHolder.isUsingVersionRejection) {
      logger.warn("NOTE: Some versions are filtered by the rejectVersionsIf predicate. See the settings.gradle.kts file.")
    }
  }

  private fun warnAboutGradleUpdateAvailableIfAny(gradleUpdates: List<Version>) {
    if (gradleUpdates.isEmpty()) return
    val currentGradleVersion = GradleVersion.current()
    val message = buildString {
      appendln("The Gradle version used in this project is not up to date.")
      append("To update from version ${currentGradleVersion.version}, run ")
      if (gradleUpdates.size == 1) {
        appendln("this command:")
      } else {
        appendln("one of these commands:")
      }
      gradleUpdates.forEach { version ->
        appendln("./gradlew wrapper --gradle-version ${version.value}")
      }
      appendln()
      appendln("Be sure to read the migration guides to have a smooth upgrade process.")
      appendln("Note that you can replace with a specific intermediate version if needed.")
    }
    logger.warn(message)
  }

  private fun warnAboutDynamicVersionsIfAny(dependenciesWithDynamicVersions: List<Dependency>) {
    if (dependenciesWithDynamicVersions.isNotEmpty()) {
      // TODO: Suggest running a diagnosis task to list the dynamic versions.
      logger.error(
        """Found ${dependenciesWithDynamicVersions.count()} dynamic dependencies versions!
                    |This makes builds unreproducible, and can make you use unstable versions unknowingly.
                    |
                    |The simple fix is to replace the dynamic version by the version placeholder, i.e. the underscore (_)
                    |
                    |Another solution if that fits your needs is to specify a non dynamic strict or preferred version constraint.
                    |
                    |If you're not convinced to stop using dynamic versions, here's an article for you:
                    |https://blog.danlew.net/2015/09/09/dont-use-dynamic-versions-for-your-dependencies/
                    """.trimMargin()
      )
    }
  }

  private fun warnAboutHardcodedVersionsIfAny(dependenciesWithHardcodedVersions: List<Dependency>) {
    if (dependenciesWithHardcodedVersions.isNotEmpty()) {
      // TODO: Suggest running a diagnosis task to list the hardcoded versions.
      val warnFor = (dependenciesWithHardcodedVersions).take(3).map {
        "${it.group}:${it.name}:${it.version}"
      }
      val versionsFileName = RefreshVersionsConfigHolder.versionsPropertiesFile.name
      logger.warn(
        """Found ${dependenciesWithHardcodedVersions.count()} hardcoded dependencies versions.
                    |
                    |$warnFor...
                    |
                    |To ensure single source of truth, refreshVersions only works with version placeholders,
                    |that is the explicit way of marking the version is not there (but in the $versionsFileName file).
                    |
                    |To migrate your project, run
                    |   ./gradlew refreshVersionsMigrate
                    |
                    |See https://jmfayard.github.io/refreshVersions/migrate/""".trimMargin()
      )
      // TODO: Replace issue link above with stable link to explanation in documentation.
    }
  }
}
