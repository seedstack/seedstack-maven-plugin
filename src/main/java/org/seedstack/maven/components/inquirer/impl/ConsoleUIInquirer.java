/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer.impl;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.seedstack.maven.components.inquirer.Inquirer;
import org.seedstack.maven.components.prompter.Prompter;

@Component(role = Inquirer.class, hint = "fancy")
public class ConsoleUIInquirer extends AbstractInquirer {
    @Requirement(hint = "fancy")
    private Prompter prompter;

    @Override
    protected Prompter getPrompter() {
        return prompter;
    }
}
