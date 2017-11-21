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
    private Timer timer;
    private boolean stop;

    @Override
    public void onChange(final Set<FileEvent> fileEvents) {
        if (!stop) {
            if (this.timer != null) {
                this.timer.cancel();
            }

            pending.addAll(fileEvents);

            this.timer = new Timer();
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    HashSet<FileEvent> aggregatedFileEvents = new HashSet<>();
                    pending.drainTo(aggregatedFileEvents);
                    onAggregatedChanges(aggregatedFileEvents);
                }
            }, 500);
        }
    }

    public void stop() {
        stop = true;
        if (timer != null) {
            timer.cancel();
        }
    }

    protected abstract void onAggregatedChanges(Set<FileEvent> fileEvents);
}
