/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components;

import de.codeshelf.consoleui.prompt.ConsolePrompt;
import de.codeshelf.consoleui.prompt.InputResult;
import de.codeshelf.consoleui.prompt.ListResult;
import de.codeshelf.consoleui.prompt.builder.InputValueBuilder;
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder;
import org.codehaus.plexus.component.annotations.Component;
import org.fusesource.jansi.AnsiConsole;

import java.io.IOException;
import java.util.List;

@Component(role = Prompter.class)
public class PrettyPrompter implements Prompter {
    static {
        AnsiConsole.systemInstall();
    }

    private final ConsolePrompt prompt;

    public PrettyPrompter() {
        prompt = new ConsolePrompt();
    }

    @Override
    public String prompt(String message) throws PrompterException {
        InputValueBuilder input = prompt
                .getPromptBuilder()
                .createInputPrompt()
                .name("input")
                .message(message);
        try {
            return ((InputResult) prompt.prompt(input.addPrompt().build()).get("input")).getInput();
        } catch (IOException e) {
            throw new PrompterException(e);
        }
    }

    @Override
    public String prompt(String message, List<String> possibleValues) throws PrompterException {
        ListPromptBuilder list = prompt
                .getPromptBuilder()
                .createListPrompt()
                .name("list")
                .message(message);
        for (String possibleValue : possibleValues) {
            list.newItem(possibleValue).text(possibleValue).add();
        }
        try {
            return ((ListResult) prompt.prompt(list.addPrompt().build()).get("list")).getSelectedId();
        } catch (IOException e) {
            throw new PrompterException(e);
        }
    }
}