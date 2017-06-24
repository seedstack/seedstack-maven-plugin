/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components;

public class PrompterException extends Exception {
    public PrompterException() {
    }

    public PrompterException(String message) {
        super(message);
    }

    public PrompterException(String message, Throwable cause) {
        super(message, cause);
    }

    public PrompterException(Throwable cause) {
        super(cause);
    }

    public PrompterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
