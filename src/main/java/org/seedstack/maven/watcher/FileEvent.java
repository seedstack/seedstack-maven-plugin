/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.watcher;

import java.io.File;

public class FileEvent {
    private final Kind kind;
    private final File file;

    public FileEvent(Kind kind, File file) {
        this.kind = kind;
        this.file = file;
    }

    public Kind getKind() {
        return kind;
    }

    public File getFile() {
        return file;
    }

    public enum Kind {
        CREATE,
        MODIFY,
        DELETE
    }
}
