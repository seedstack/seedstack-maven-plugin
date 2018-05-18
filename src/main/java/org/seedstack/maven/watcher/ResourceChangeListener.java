/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.watcher;

import java.io.File;
import java.util.Set;
import org.seedstack.maven.WatchMojo;

public class ResourceChangeListener implements FileChangeListener {
    private WatchMojo watchMojo;

    public ResourceChangeListener(WatchMojo watchMojo) {
        this.watchMojo = watchMojo;
    }

    @Override
    public void onChange(Set<FileEvent> fileEvents) {
        boolean configChanged = false;
        watchMojo.getLog().info("Resource change(s) detected");

        for (FileEvent fileEvent : fileEvents) {
            if (fileEvent.getKind() == FileEvent.Kind.CREATE) {
                watchMojo.getLog().debug("NEW: " + fileEvent.getFile().getPath());
            } else if (fileEvent.getKind() == FileEvent.Kind.MODIFY) {
                watchMojo.getLog().debug("MODIFIED: " + fileEvent.getFile().getPath());
            } else if (fileEvent.getKind() == FileEvent.Kind.DELETE) {
                watchMojo.getLog().debug("DELETED: " + fileEvent.getFile().getPath());
            }

            if (isConfigFile(fileEvent.getFile())) {
                configChanged = true;
            }
        }

        if (configChanged) {
            // Wait for the application to notice the change
            watchMojo.getLog()
                    .info("A configuration file has changed, waiting for the application to notice the change");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        watchMojo.liveReload();
    }

    private boolean isConfigFile(File file) {
        String fileName = file.getName();
        return fileName.equals("application.yaml")
                || fileName.equals("application.override.yaml")
                || fileName.equals("application.json")
                || fileName.equals("application.override.json")
                || fileName.equals("application.properties")
                || fileName.equals("application.override.properties")
                || file.getParentFile().getPath().endsWith("META-INF" + File.separator + "configuration");
    }
}
