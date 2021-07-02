/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.build;

import java.util.function.Consumer;

/**
 * A build that is a child of some other build, and whose lifetime is bounded by the lifetime of that containing build.
 */
public interface NestedBuildState extends BuildState {
    /**
     * Runs any user build finished hooks and other user code cleanup for this build, if not already. Does not stop the services for this build.
     */
    void finishBuild(Consumer<? super Throwable> collector);
}
