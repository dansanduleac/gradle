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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier;
import org.gradle.tooling.model.gradle.BasicGradleProject;

public class BasicGradleProjectIdentifierMixin {
    private final BasicGradleProject gradleProject;

    public BasicGradleProjectIdentifierMixin(BasicGradleProject gradleProject) {
        this.gradleProject = gradleProject;
    }

    public ProjectIdentifier getIdentifier() {
        return new DefaultProjectIdentifier(gradleProject.getIdentifier().getBuild(), gradleProject.getPath());
    }
}
