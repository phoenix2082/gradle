/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ExternalModuleDependency;

/**
 * This DSL element exists to contain any dependencies needed to compile and run a {@link JvmTestSuite}.
 *
 * @since 7.3
 */
@Incubating
public interface ComponentDependencies {
    void implementation(Object dependencyNotation);
    void implementation(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration);

    void runtimeOnly(Object dependencyNotation);
    void runtimeOnly(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration);

    void compileOnly(Object dependencyNotation);
    void compileOnly(Object dependencyNotation, Action<? super ExternalModuleDependency> configuration);
}
