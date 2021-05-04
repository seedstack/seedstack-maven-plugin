/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.prompter;

import java.io.IOException;

public class PromptException extends Exception {
    public PromptException() {
    }

    public PromptException(String message) {
        super(message);
    }

    public PromptException(String message, Throwable cause) {
        super(message, cause);
    }

    public PromptException(Throwable cause) {
        super(cause);
    }

    public PromptException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
