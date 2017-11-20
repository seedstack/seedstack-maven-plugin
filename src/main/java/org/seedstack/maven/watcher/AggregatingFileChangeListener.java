/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.watcher;

import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;

public abstract class AggregatingFileChangeListener implements FileChangeListener {
    private final ArrayBlockingQueue<FileEvent> pending = new ArrayBlockingQueue<>(10000);
    private final Timer timer = new Timer();
    private boolean stop;

    protected AggregatingFileChangeListener() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Set<FileEvent> aggregatedChangedFiles = new HashSet<>();
                pending.drainTo(aggregatedChangedFiles);
                if (!aggregatedChangedFiles.isEmpty()) {
                    onAggregatedChanges(aggregatedChangedFiles);
                }
            }
        }, 0, 250);
    }

    @Override
    public void onChange(Set<FileEvent> fileEvents) {
        if (!stop) {
            pending.addAll(fileEvents);
        }
    }

    public void stop() {
        stop = true;
        timer.cancel();
    }

    protected abstract void onAggregatedChanges(Set<FileEvent> fileEvents);
}
