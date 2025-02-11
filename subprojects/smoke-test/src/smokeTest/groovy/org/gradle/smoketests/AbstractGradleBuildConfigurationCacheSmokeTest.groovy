/*
 * Copyright 2019 the original author or authors.
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


import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

/**
 * Smoke test building gradle/gradle with configuration cache enabled.
 *
 * gradle/gradle requires Java >=9 and <=11 to build, see {@link AbstractGradleceptionSmokeTest.GradleBuildJvmSpec}.
 */
@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
abstract class AbstractGradleBuildConfigurationCacheSmokeTest extends AbstractGradleceptionSmokeTest {
    def setup() {
        // Generate Kotlin DSL sources once so they are included as :kotlin-dsl:compileKotlin inputs.
        // TODO:configuration-cache handle generated sources better (see gradlebuild.kotlin-dsl-dependencies-embedded.gradle.kts:39)
        run([':kotlin-dsl:generateKotlinDependencyExtensions'])
    }

    @Override
    protected void assertConfigurationCacheStateStored() {
        assert result.output.count("Calculating task graph as no configuration cache is available") == 1
    }

    @Override
    protected void assertConfigurationCacheStateLoaded() {
        assert result.output.count("Reusing configuration cache") == 1
    }

    TestExecutionResult assertTestClassExecutedIn(String subProjectDir, String testClass) {
        new DefaultTestExecutionResult(file(subProjectDir), "build", "", "", "embeddedIntegTest")
            .assertTestClassesExecuted(testClass)
    }

    void configurationCacheRun(List<String> tasks, int daemonId = 0) {
        run(
            tasks + [
                "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
                "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn".toString(), // TODO:configuration-cache remove
                TEST_BUILD_TIMESTAMP
            ],
            // use a unique testKitDir per daemonId other than 0 as 0 means default daemon.
            daemonId != 0 ? file("test-kit/$daemonId") : null
        )
    }
}



