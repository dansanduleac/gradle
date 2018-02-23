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

package org.gradle.internal.operations.logging;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.RenderableOutputEvent;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationProgressEvent;

public class LoggingBuildOperationNotificationBridge implements Stoppable, OutputEventListener {

    private final LoggingManagerInternal loggingManagerInternal;
    private final BuildOperationListener buildOperationListenerBroadcaster;

    public LoggingBuildOperationNotificationBridge(LoggingManagerInternal loggingManagerInternal, BuildOperationListener buildOperationListener) {
        this.loggingManagerInternal = loggingManagerInternal;
        this.buildOperationListenerBroadcaster = buildOperationListener;
        loggingManagerInternal.addOutputEventListener(this);
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof RenderableOutputEvent) {
            RenderableOutputEvent renderableOutputEvent = (RenderableOutputEvent) event;

            // FIXME Needs to be a carefully change controlled type describing the output.
            // Also needs to convey the styling structure if styled.
            Object details = OutputDetailsFactory.from(renderableOutputEvent);
            if (renderableOutputEvent.getBuildOperationId() != null) {
                buildOperationListenerBroadcaster.progress(Long.valueOf(((OperationIdentifier)
                        renderableOutputEvent.getBuildOperationId()).getId()),
                    new OperationProgressEvent(renderableOutputEvent.getTimestamp(), details)
                );
            }
        }
    }

    @Override
    public void stop() {
        loggingManagerInternal.removeOutputEventListener(this);
    }
}
