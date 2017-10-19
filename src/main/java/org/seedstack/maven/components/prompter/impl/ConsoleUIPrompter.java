/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.components.prompter.impl;

import de.codeshelf.consoleui.elements.ConfirmChoice;
import de.codeshelf.consoleui.prompt.CheckboxResult;
import de.codeshelf.consoleui.prompt.ConfirmResult;
import de.codeshelf.consoleui.prompt.ConsolePrompt;
import de.codeshelf.consoleui.prompt.ExpandableChoiceResult;
import de.codeshelf.consoleui.prompt.InputResult;
import de.codeshelf.consoleui.prompt.ListResult;
import de.codeshelf.consoleui.prompt.PromtResultItemIF;
import de.codeshelf.consoleui.prompt.builder.CheckboxPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.ConfirmPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.ExpandableChoicePromptBuilder;
import de.codeshelf.consoleui.prompt.builder.InputValueBuilder;
import de.codeshelf.consoleui.prompt.builder.ListPromptBuilder;
import de.codeshelf.consoleui.prompt.builder.PromptBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.codehaus.plexus.component.annotations.Component;
import org.seedstack.maven.components.prompter.PromptException;
import org.seedstack.maven.components.prompter.Prompter;
import org.seedstack.maven.components.prompter.Value;

@Component(role = Prompter.class, hint = "fancy")
public class ConsoleUIPrompter implements Prompter {
    @Override
    public String promptChoice(String message, List<Value> values) throws PromptException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        ExpandableChoicePromptBuilder choicePromptBuilder = promptBuilder.createChoicePrompt()
                .name("dummy")
                .message(message);
        for (Value value : values) {
            if (value.isSeparator()) {
                choicePromptBuilder.newSeparator(value.getLabel())
                        .add();
            } else {
                choicePromptBuilder.newItem()
                        .name(value.getName())
                        .message(value.getLabel())
                        .key(value.getKey())
                        .add();
            }
        }
        choicePromptBuilder.addPrompt();
        return ((ExpandableChoiceResult) extractAnswer(consolePrompt, promptBuilder)).getSelectedId();
    }

    @Override
    public String promptList(String message, List<Value> values, String defaultValue) throws PromptException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        ListPromptBuilder listPromptBuilder = promptBuilder.createListPrompt()
                .name("dummy")
                .message(message);

        // Put default value first
        Value defaultValueObject = null;
        if (defaultValue != null && !defaultValue.isEmpty()) {
            for (Value value : values) {
                if (value.getName().equals(defaultValue)) {
                    defaultValueObject = value;
                    listPromptBuilder.newItem()
                            .name(value.getName())
                            .text(value.getLabel())
                            .add();
                    break;
                }
            }
        }

        // Put other values
        for (Value value : values) {
            if (!value.equals(defaultValueObject)) {
                listPromptBuilder.newItem()
                        .name(value.getName())
                        .text(value.getLabel())
                        .add();
            }
        }

        listPromptBuilder.addPrompt();
        return ((ListResult) extractAnswer(consolePrompt, promptBuilder)).getSelectedId();
    }

    @Override
    public Set<String> promptCheckbox(String message, List<Value> values) throws PromptException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        CheckboxPromptBuilder checkboxPromptBuilder = promptBuilder.createCheckboxPrompt()
                .name("dummy")
                .message(message);
        for (Value value : values) {
            checkboxPromptBuilder.newItem()
                    .name(value.getName())
                    .text(value.getLabel())
                    .add();
        }
        checkboxPromptBuilder.addPrompt();
        return ((CheckboxResult) extractAnswer(consolePrompt, promptBuilder)).getSelectedIds();
    }

    @Override
    public String promptInput(String message, String defaultValue) throws PromptException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        InputValueBuilder inputValueBuilder = promptBuilder.createInputPrompt()
                .name("dummy")
                .message(message);
        if (defaultValue != null) {
            inputValueBuilder.defaultValue(defaultValue);
        }
        inputValueBuilder.addPrompt();
        return ((InputResult) extractAnswer(consolePrompt, promptBuilder)).getInput();
    }

    @Override
    public boolean promptConfirmation(String message, String defaultValue) throws PromptException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        ConfirmPromptBuilder confirmPromptBuilder = promptBuilder.createConfirmPromp()
                .name("dummy")
                .message(message);
        if (defaultValue != null) {
            confirmPromptBuilder.defaultValue(ConfirmChoice.ConfirmationValue.valueOf(
                    defaultValue.toUpperCase(Locale.ENGLISH)));
        }
        confirmPromptBuilder.addPrompt();
        return ((ConfirmResult) extractAnswer(consolePrompt,
                promptBuilder)).getConfirmed() == ConfirmChoice.ConfirmationValue.YES;
    }

    @SuppressWarnings("unchecked")
    private <T extends PromtResultItemIF> T extractAnswer(ConsolePrompt consolePrompt,
            PromptBuilder promptBuilder) throws PromptException {
        try {
            return (T) consolePrompt.prompt(promptBuilder.build()).values().iterator().next();
        } catch (IOException e) {
            throw new PromptException("Unable to prompt question", e);
        }
    }
}
