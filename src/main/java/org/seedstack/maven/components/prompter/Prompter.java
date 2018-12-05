/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.prompter;

import java.util.List;
import java.util.Set;

public interface Prompter {
    String promptChoice(String message, List<Value> values) throws PromptException;

    String promptList(String message, List<Value> values, String defaultValue) throws PromptException;

    Set<String> promptCheckbox(String message, List<Value> values) throws PromptException;

    String promptInput(String message, String defaultValue) throws PromptException;

    boolean promptConfirmation(String message, String defaultValue) throws PromptException;
}
