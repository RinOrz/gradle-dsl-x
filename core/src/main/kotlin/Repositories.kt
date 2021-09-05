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
@file:Suppress("SpellCheckingInspection")

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.maven

/**
 * Mirrors of maven repositories
 *
 * @author 凛 (https://github.com/RinOrz)
 */
sealed class MavenMirrors(val url: String) {

  /* More see https://maven.aliyun.com/repository */
  sealed class Aliyun(type: String) : MavenMirrors("https://maven.aliyun.com/repository/$type") {
    object Google : Aliyun("google")
    object Public : Aliyun("public")
    object Spring : Aliyun("spring")
    object Central : Aliyun("central")
    object JCenter : Aliyun("jcenter")
    object GrailsCore : Aliyun("grails-core")
    object SpringPlugin : Aliyun("spring-plugin")
    object GradlePlugin : Aliyun("gradle-plugin")
    object ApacheSnapshots : Aliyun("apache-snapshots")
  }

  /* More see https://mirrors.huaweicloud.com/ */
  object Huawei : MavenMirrors("https://repo.huaweicloud.com/repository/maven/")

  /* More see https://mirrors.cloud.tencent.com/ */
  object Tencent : MavenMirrors("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
}

/**
 * Adds and configures a Maven repository.
 *
 * The provided [url] value is evaluated as per [org.gradle.api.Project.uri]. This means, for example, you can pass in a `File` object, or a relative path to be evaluated relative
 * to the project directory.
 *
 * @param name the name for this repository.
 * @param url The base URL of this repository. This URL is used to find both POMs and artifact files.
 * @param action The action to use to configure the repository.
 * @return The added repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 */
fun RepositoryHandler.maven(name: String, url: Any, action: MavenArtifactRepository.() -> Unit = {}) =
  maven {
    setName(name)
    setUrl(url)
    action()
  }

/**
 * Adds and configures a Maven mirror repository.
 */
fun RepositoryHandler.mavenMirror(
  mirror: MavenMirrors,
  action: MavenArtifactRepository.() -> Unit = {},
) = maven(name = mirror.javaClass.simpleName, mirror.url, action)

/**
 * Adds and configures a Sonatype repository.
 */
fun RepositoryHandler.sonatype(action: MavenArtifactRepository.() -> Unit = {}) {
  maven(name = "Sonatype OSS S01", url = "https://s01.oss.sonatype.org/content/repositories/public", action)
  maven(name = "Sonatype OSS", url = "https://oss.sonatype.org/content/repositories/public", action)
}

/**
 * Adds and configures a Sonatype snapshots repository.
 */
fun RepositoryHandler.sonatypeSnapshots(action: MavenArtifactRepository.() -> Unit = {}) {
  maven(name = "Sonatype OSS S01 Snapshots", url = "https://s01.oss.sonatype.org/content/repositories/snapshots") {
    mavenContent { snapshotsOnly() }
    action()
  }
  maven(name = "Sonatype OSS Snapshots", url = "https://oss.sonatype.org/content/repositories/snapshots") {
    mavenContent { snapshotsOnly() }
    action()
  }
}

/**
 * Adds a repository which looks in Bintray's JCenter repository for [Project.dependencies].
 */
@Deprecated(
  message = "JCenter sunset in February 2021, it is recommended to use mavenCentral, but you can also use mirrors.",
  replaceWith = ReplaceWith("mavenMirror(MavenMirrors.Aliyun.JCenter)")
)
fun RepositoryHandler.jCenter(action: MavenArtifactRepository.() -> Unit = {}) =
  mavenMirror(MavenMirrors.Aliyun.JCenter, action)

/**
 * Adds a repository which looks in Jitpack repository for [Project.dependencies].
 */
fun RepositoryHandler.jitpack(action: MavenArtifactRepository.() -> Unit = {}) =
  maven(name = "Jitpack", url = "https://jitpack.io", action)