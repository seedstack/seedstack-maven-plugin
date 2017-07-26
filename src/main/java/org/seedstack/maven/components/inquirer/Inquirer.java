/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import java.net.URL;
import java.util.Map;

public interface Inquirer {
    Map<String, Object> inquire(URL inquiryURL) throws InquirerException;

    Object ask(Question question) throws InquirerException;
}
