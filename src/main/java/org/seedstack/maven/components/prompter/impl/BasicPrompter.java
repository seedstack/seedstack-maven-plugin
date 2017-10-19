/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.components.prompter.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.seedstack.maven.components.prompter.PromptException;
import org.seedstack.maven.components.prompter.Prompter;
import org.seedstack.maven.components.prompter.Value;

@Component(role = Prompter.class, hint = "basic")
public class BasicPrompter implements Prompter {
    @Requirement
    private OutputHandler outputHandler;

    @Requirement
    private InputHandler inputHandler;

    @Override
    public String promptChoice(String message, List<Value> groups) throws PromptException {
        throw new UnsupportedOperationException("Basic prompter cannot handle complex choices");
    }

    @Override
    public String promptList(String message, List<Value> values) throws PromptException {
        return promptList(message, values, null);
    }

    private String promptList(String message, List<Value> values, String defaultValue) throws PromptException {
        List<String> strings = Value.convertValues(values);
        String formattedMessage = formatMessage(message, strings, null);
        String line;
        do {
            writePrompt(formattedMessage);
            line = readLine();
            if (line == null && defaultValue != null) {
                line = defaultValue;
            }
        } while (line == null || !strings.contains(line));
        return line;
    }

    @Override
    public Set<String> promptCheckbox(String message, List<Value> values) throws PromptException {
        throw new UnsupportedOperationException("Basic prompter cannot handle checkbox choices");
    }

    @Override
    public String promptInput(String message, String defaultValue) throws PromptException {
        writePrompt(formatMessage(message, null, defaultValue));
        String line = readLine();
        return (line == null || line.isEmpty()) ? defaultValue : line;
    }

    @Override
    public boolean promptConfirmation(String message, String defaultValue) throws PromptException {
        List<Value> values = new ArrayList<>();
        values.add(new Value("yes"));
        values.add(new Value("no"));
        return promptList(message, values, defaultValue).equals("yes");
    }

    private String formatMessage(String message, List<String> possibleValues, String defaultReply) {
        StringBuilder formatted = new StringBuilder();

        formatted.append(message);

        if (possibleValues != null && !possibleValues.isEmpty()) {
            formatted.append(" (");
            for (int i = 1; i <= possibleValues.size(); i++) {
                String possibleValue = possibleValues.get(i - 1);
                if (possibleValue.equals(defaultReply)) {
                    formatted.append("*");
                }
                formatted.append(possibleValue);
                if (i < possibleValues.size()) {
                    formatted.append("/");
                }
            }
            formatted.append(")");
        } else if (defaultReply != null && !defaultReply.isEmpty()){
            formatted.append(" (").append(defaultReply).append(")");
        }

        return formatted.toString();
    }

    private void writePrompt(String message) throws PromptException {
        showMessage(message + ": ");
    }

    private String readLine() throws PromptException {
        try {
            return inputHandler.readLine();
        } catch (IOException e) {
            throw new PromptException("Failed to read user response", e);
        }
    }

    private void showMessage(String message) throws PromptException {
        try {
            outputHandler.write(message);
        } catch (IOException e) {
            throw new PromptException("Failed to show message", e);
        }
    }
}
