/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.watcher;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import com.sun.nio.file.SensitivityWatchEventModifier;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.apache.maven.plugin.logging.Log;

public class DirectoryWatcher implements Runnable {
    private final Log log;
    private final FileChangeListener listener;
    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final WatchEvent.Modifier modifier;
    private boolean trace = false;
    private boolean stop;

    public DirectoryWatcher(Log log, FileChangeListener listener) throws IOException {
        this.log = log;
        this.listener = listener;
        this.modifier = determineModifier();
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
    }

    private WatchEvent.Modifier determineModifier() {
        try {
            Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
            return SensitivityWatchEventModifier.HIGH;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public void watchRecursively(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                watch(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void watch(Path dir) throws IOException {
        WatchKey key;
        if (modifier != null) {
            key = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}, modifier);
        } else {
            key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        }
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                log.debug("New source directory: " + dir);
            } else {
                if (!dir.equals(prev)) {
                    log.debug("Source directory updated: " + dir);
                }
            }
        }
        keys.put(key, dir);
    }

    public void run() {
        this.trace = true;
        while (!stop) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                continue;
            }

            HashSet<FileEvent> fileEvents = new HashSet<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if (event.kind() == ENTRY_CREATE) {
                    log.debug("New source file: " + child);
                    fileEvents.add(new FileEvent(FileEvent.Kind.CREATE, child.toFile()));
                } else if (event.kind() == ENTRY_MODIFY) {
                    log.debug("Source file modified: " + child);
                    fileEvents.add(new FileEvent(FileEvent.Kind.MODIFY, child.toFile()));
                } else if (event.kind() == ENTRY_DELETE) {
                    log.debug("Source file deleted: " + child);
                    fileEvents.add(new FileEvent(FileEvent.Kind.DELETE, child.toFile()));
                }

                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            watchRecursively(child);
                        }
                    } catch (IOException e) {
                        log.error("Unable to watch " + child, e);
                    }
                }
            }

            if (!fileEvents.isEmpty()) {
                listener.onChange(fileEvents);
            }

            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void stop() {
        stop = true;
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }
}