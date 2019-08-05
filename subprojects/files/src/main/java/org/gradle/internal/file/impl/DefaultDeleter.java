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
package org.gradle.internal.file.impl;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("Since15")
public class DefaultDeleter implements Deleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDeleter.class);

    private final Supplier<Long> timeProvider;
    private final Predicate<? super File> isSymlink;
    private final boolean runGcOnFailedDelete;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;

    @VisibleForTesting
    static final int MAX_REPORTED_PATHS = 16;

    @VisibleForTesting
    static final String HELP_FAILED_DELETE_CHILDREN = "Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory.";
    @VisibleForTesting
    static final String HELP_NEW_CHILDREN = "New files were found. This might happen because a process is still writing to the target directory.";

    public DefaultDeleter(Supplier<Long> timeProvider, Predicate<? super File> isSymlink, boolean runGcOnFailedDelete) {
        this.timeProvider = timeProvider;
        this.isSymlink = isSymlink;
        this.runGcOnFailedDelete = runGcOnFailedDelete;
    }

    @Override
    public boolean deleteRecursively(File root, boolean followSymlinks) throws IOException {
        if (root.exists()) {
            LOGGER.debug("Deleting {}", root);
            long startTime = timeProvider.get();
            Collection<String> failedPaths = new ArrayList<String>();
            deleteRecursively(startTime, root, root, followSymlinks, failedPaths);
            if (!failedPaths.isEmpty()) {
                throwWithHelpMessage(startTime, root, followSymlinks, failedPaths, false);
            }
            return true;
        } else {
            return false;
        }
    }

    private void deleteRecursively(long startTime, File baseDir, File file, boolean followSymlinks, Collection<String> failedPaths) throws IOException {

        if (shouldFollow(file, followSymlinks)) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return;
            }

            for (File item : contents) {
                deleteRecursively(startTime, baseDir, item, followSymlinks, failedPaths);
            }
        }

        try {
            delete(file);
        } catch (IOException ex) {
            failedPaths.add(file.getAbsolutePath());

            // Fail fast
            if (failedPaths.size() == MAX_REPORTED_PATHS) {
                throwWithHelpMessage(startTime, baseDir, followSymlinks, failedPaths, true);
            }
        }
    }

    private boolean shouldFollow(File file, boolean followSymlinks) {
        return file.isDirectory() && (followSymlinks || !isSymlink.test(file));
    }

    protected boolean deleteFile(File file) throws IOException {
        return Files.deleteIfExists(file.toPath());
    }

    @Override
    public boolean delete(File file) throws IOException {
        try {
            return deleteFile(file);
        } catch (IOException ex) {
            LOGGER.debug("Retrying removal of {} after exception", file.getAbsolutePath(), ex);
            prayBeforeRetry();
            return deleteFile(file);
        }
    }

    private void prayBeforeRetry() {
        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // It mentions that there is a bug in the Windows JDK implementations that this is a valid
        // workaround for. I've been unable to find a definitive reference to this bug.
        // The thinking is that if this is good enough for Ant, it's good enough for us.
        if (runGcOnFailedDelete) {
            System.gc();
        }
        try {
            Thread.sleep(DELETE_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void throwWithHelpMessage(long startTime, File file, boolean followSymlinks, Collection<String> failedPaths, boolean more) throws IOException {
        throw new IOException(buildHelpMessageForFailedDelete(startTime, file, followSymlinks, failedPaths, more));
    }

    private String buildHelpMessageForFailedDelete(long startTime, File file, boolean followSymlinks, Collection<String> failedPaths, boolean more) {

        StringBuilder help = new StringBuilder("Unable to delete ");
        if (isSymlink.test(file)) {
            help.append("symlink to ");
        }
        if (file.isDirectory()) {
            help.append("directory ");
        } else {
            help.append("file ");
        }
        help.append('\'').append(file).append('\'');

        if (shouldFollow(file, followSymlinks)) {
            String absolutePath = file.getAbsolutePath();
            failedPaths.remove(absolutePath);
            if (!failedPaths.isEmpty()) {
                help.append("\n  ").append(HELP_FAILED_DELETE_CHILDREN);
                for (String failed : failedPaths) {
                    help.append("\n  - ").append(failed);
                }
                if (more) {
                    help.append("\n  - and more ...");
                }
            }

            Collection<String> newPaths = listNewPaths(startTime, file, failedPaths);
            if (!newPaths.isEmpty()) {
                help.append("\n  ").append(HELP_NEW_CHILDREN);
                for (String newPath : newPaths) {
                    help.append("\n  - ").append(newPath);
                }
                if (newPaths.size() == MAX_REPORTED_PATHS) {
                    help.append("\n  - and more ...");
                }
            }
        }
        return help.toString();
    }

    private static Collection<String> listNewPaths(long startTime, File directory, Collection<String> failedPaths) {
        List<String> paths = new ArrayList<String>(MAX_REPORTED_PATHS);
        Deque<File> stack = new ArrayDeque<File>();
        stack.push(directory);
        while (!stack.isEmpty() && paths.size() < MAX_REPORTED_PATHS) {
            File current = stack.pop();
            String absolutePath = current.getAbsolutePath();
            if (!current.equals(directory) && !failedPaths.contains(absolutePath) && current.lastModified() >= startTime) {
                paths.add(absolutePath);
            }
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children != null) {
                    for (File child : children) {
                        stack.push(child);
                    }
                }
            }
        }
        return paths;
    }
}
