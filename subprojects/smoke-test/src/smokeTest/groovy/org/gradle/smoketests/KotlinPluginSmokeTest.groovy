/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.smoketests

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class KotlinPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    public static final String NO_CONFIGURATION_CACHE_ITERATION_MATCHER = ".*kotlin=1\\.3\\.[2-6].*"
    private static final VersionNumber KOTLIN_VERSION_USING_NEW_TRANSFORMS_API = VersionNumber.parse('1.4.20')
    private static final VersionNumber KOTLIN_VERSION_USING_NEW_WORKERS_API = VersionNumber.parse('1.5.0')
    private static final String ARTIFACT_TRANSFORM_DEPRECATION_WARNING =
        "Registering artifact transforms extending ArtifactTransform has been deprecated. " +
            "This is scheduled to be removed in Gradle 8.0. Implement TransformAction instead. " +
            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/artifact_transforms.html for more details."


    // TODO:configuration-cache remove once fixed upstream
    @Override
    protected int maxConfigurationCacheProblems() {
        return 200
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm (kotlin=#version, workers=#workers)'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = runner(workers, 'run')
            .expectLegacyDeprecationWarningIf(workers && VersionNumber.parse(version) < KOTLIN_VERSION_USING_NEW_WORKERS_API,
                "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
            )
            .expectLegacyDeprecationWarningIf(VersionNumber.parse(version) < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
        assert result.output.contains("Hello world!")

        when:
        result = runner(workers, 'run')
            .expectLegacyDeprecationWarningIf(!GradleContextualExecuter.configCache && VersionNumber.parse(version) < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .build()


        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE
        assert result.output.contains("Hello world!")

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin javascript (kotlin=#version, workers=#workers)'() {
        given:
        useSample("kotlin-js-sample")
        withKotlinBuildFile()
        replaceVariablesInBuildFile(kotlinVersion: version)

        when:
        def result = runner(workers, 'compileKotlin2Js')
            .expectLegacyDeprecationWarningIf(workers && VersionNumber.parse(version) < KOTLIN_VERSION_USING_NEW_WORKERS_API,
                "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
            )
            .expectLegacyDeprecationWarningIf(VersionNumber.parse(version) >= VersionNumber.parse('1.4.0'),
                "The `kotlin2js` Gradle plugin has been deprecated.")
            .build()

        then:
        result.task(':compileKotlin2Js').outcome == SUCCESS

        where:
        [version, workers] << [
            TestedVersions.kotlin.versions,
            [true, false]
        ].combinations()
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm and groovy plugins combined (kotlin=#kotlinVersion)'() {
        given:
        buildFile << """
            plugins {
                id 'groovy'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
            }

            repositories {
                mavenCentral()
            }

            tasks.named('compileGroovy') {
                classpath = sourceSets.main.compileClasspath
            }
            tasks.named('compileKotlin') {
                classpath += files(sourceSets.main.groovy.classesDirectory)
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
                implementation localGroovy()
            }
        """
        file("src/main/groovy/Groovy.groovy") << "class Groovy { }"
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { val groovy = Groovy() }"
        file("src/main/java/Java.java") << "class Java { private Kotlin kotlin = new Kotlin(); }" // dependency to compileJava->compileKotlin is added by Kotlin plugin

        when:
        def result = runner(false, 'compileJava')
            .expectLegacyDeprecationWarningIf(VersionNumber.parse(kotlinVersion) < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .build()


        then:
        result.task(':compileJava').outcome == SUCCESS
        result.tasks.collect { it.path } == [':compileGroovy', ':compileKotlin', ':compileJava']

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = NO_CONFIGURATION_CACHE_ITERATION_MATCHER)
    def 'kotlin jvm and java-gradle-plugin plugins combined (kotlin=#kotlinVersion)'() {
        given:
        buildFile << """
            plugins {
                id 'java-gradle-plugin'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
            }
        """
        file("src/main/kotlin/Kotlin.kt") << "class Kotlin { }"

        when:
        def result = runner(false, 'build')
            .expectLegacyDeprecationWarningIf(VersionNumber.parse(kotlinVersion) < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS

        when:
        result = runner(false, 'build')
            .expectLegacyDeprecationWarningIf(!GradleContextualExecuter.configCache && VersionNumber.parse(kotlinVersion) < KOTLIN_VERSION_USING_NEW_TRANSFORMS_API, ARTIFACT_TRANSFORM_DEPRECATION_WARNING)
            .build()

        then:
        result.task(':compileKotlin').outcome == UP_TO_DATE

        where:
        kotlinVersion << TestedVersions.kotlin.versions
    }

    private BuildResult build(boolean workers, String... tasks) {
        return runner(workers, *tasks).build()
    }

    private SmokeTestGradleRunner runner(boolean workers, String... tasks) {
        return runner(tasks + ["--parallel", "-Pkotlin.parallel.tasks.in.project=$workers"] as String[])
            .forwardOutput()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.jetbrains.kotlin.jvm': TestedVersions.kotlin,
            'org.jetbrains.kotlin.js': TestedVersions.kotlin,
            'org.jetbrains.kotlin.multiplatform': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android': TestedVersions.kotlin,
            'org.jetbrains.kotlin.android.extensions': TestedVersions.kotlin,
            'org.jetbrains.kotlin.kapt': TestedVersions.kotlin,
            'org.jetbrains.kotlin.plugin.scripting': TestedVersions.kotlin,
            'org.jetbrains.kotlin.native.cocoapods': TestedVersions.kotlin,
        ]
    }

    @Override
    Map<String, String> getExtraPluginsRequiredForValidation(String testedPluginId, String version) {
        def androidVersion = TestedVersions.androidGradle.latest()
        if (testedPluginId == 'org.jetbrains.kotlin.kapt') {
            return ['org.jetbrains.kotlin.jvm': version]
        }
        if (isAndroidKotlinPlugin(testedPluginId)) {
            AGP_VERSIONS.assumeCurrentJavaVersionIsSupportedBy(androidVersion)
            def extraPlugins = ['com.android.application': androidVersion]
            if (testedPluginId == 'org.jetbrains.kotlin.android.extensions') {
                extraPlugins.put('org.jetbrains.kotlin.android', version)
            }
            return extraPlugins
        }
        return [:]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        validatePlugins {
            if (isAndroidKotlinPlugin(testedPluginId)) {
                buildFile << """
                    android {
                        compileSdkVersion 24
                        buildToolsVersion '${TestedVersions.androidTools}'
                    }
                """
            }
            alwaysPasses()
            if (testedPluginId == 'org.jetbrains.kotlin.js' && version != '1.3.72') {
                buildFile << """
                    kotlin { js { browser() } }
                """
            }
            if (testedPluginId == 'org.jetbrains.kotlin.multiplatform') {
                buildFile << """
                    kotlin {
                        jvm()
                        js { browser() }
                    }
                """
            }
            settingsFile << """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                    }
                }
            """
        }
    }

    private static boolean isAndroidKotlinPlugin(String pluginId) {
        return pluginId.contains('android')
    }
}
