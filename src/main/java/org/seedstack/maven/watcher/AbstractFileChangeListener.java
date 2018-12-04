/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.watcher;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import org.seedstack.maven.Context;
import org.seedstack.maven.WatchMojo;

abstract class AbstractFileChangeListener implements FileChangeListener {
    private final Semaphore semaphore = new Semaphore(1);
    private final ArrayBlockingQueue<FileEvent> pending = new ArrayBlockingQueue<>(10000);
    protected final WatchMojo watchMojo;
    protected final Context context;

    AbstractFileChangeListener(WatchMojo watchMojo, Context context) {
        this.watchMojo = watchMojo;
        this.context = context;
    }

    @Override
    public void onChange(Set<FileEvent> fileEvents) {
        pending.addAll(fileEvents);
        while (!pending.isEmpty()) {
            boolean permit = false;
            try {
                permit = semaphore.tryAcquire();
                if (permit) {
                    HashSet<FileEvent> fileEventsToProcess = new HashSet<>();
                    pending.drainTo(fileEventsToProcess);
                    refresh(fileEventsToProcess);
                } else {
                    pending.addAll(fileEvents);
                }
            } finally {
                if (permit) {
                    semaphore.release();
                }
            }
        }
    }

    protected abstract void refresh(Set<FileEvent> fileEventsToProcess);
}
