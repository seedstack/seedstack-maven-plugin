/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import org.seedstack.maven.components.inquirer.InquirerException;

public class AnswerValidationException extends InquirerException {
    public AnswerValidationException() {
    }

    public AnswerValidationException(String message) {
        super(message);
    }

    public AnswerValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnswerValidationException(Throwable cause) {
        super(cause);
    }

    public AnswerValidationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
