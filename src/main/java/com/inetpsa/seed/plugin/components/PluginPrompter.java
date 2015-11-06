/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.inetpsa.seed.plugin.components;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.InputHandler;
import org.codehaus.plexus.components.interactivity.OutputHandler;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Component(role = Prompter.class)
public class PluginPrompter implements Prompter {
    @Requirement
    private OutputHandler outputHandler;

    @Requirement
    private InputHandler inputHandler;

    public String prompt(String message)
            throws PrompterException {
        writePrompt(message);

        return readLine();
    }

    public String prompt(String message, String defaultReply) throws PrompterException {
        writePrompt(formatMessage(message, null, defaultReply));

        String line = readLine();

        if (StringUtils.isEmpty(line)) {
            line = defaultReply;
        }

        return line;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public String prompt(String message, List possibleValues, String defaultReply) throws PrompterException {
        String formattedMessage = formatMessage(message, possibleValues, defaultReply);

        String line;

        do {
            writePrompt(formattedMessage);

            line = readLine();

            if (StringUtils.isEmpty(line)) {
                line = defaultReply;
            }

            if (line != null && !possibleValues.contains(line)) {
                try {
                    outputHandler.writeLine("Invalid selection");
                } catch (IOException e) {
                    throw new PrompterException("Failed to output feedback", e);
                }
            }
        }
        while (line == null || !possibleValues.contains(line));

        return line;
    }

    public String prompt(String message, List possibleValues) throws PrompterException {
        return prompt(message, possibleValues, null);
    }

    public String promptForPassword(String message) throws PrompterException {
        writePrompt(message);

        try {
            return inputHandler.readPassword();
        } catch (IOException e) {
            throw new PrompterException("Failed to read user response", e);
        }
    }

    private String formatMessage(String message, List<String> possibleValues, String defaultReply) {
        StringBuilder formatted = new StringBuilder();

        formatted.append(message);

        if (possibleValues != null && !possibleValues.isEmpty()) {
            formatted.append(" (");
            for (int i = 1; i <= possibleValues.size(); i++) {
                String possibleValue = possibleValues.get(i - 1);
                formatted.append(possibleValue);
                if (i < possibleValues.size()) {
                    formatted.append("/");
                }
            }
            formatted.append(")");
        }

        if (defaultReply != null) {
            formatted.append(defaultReply);
        }

        return formatted.toString();
    }

    private void writePrompt(String message) throws PrompterException {
        showMessage(message + ": ");
    }

    private String readLine() throws PrompterException {
        try {
            return inputHandler.readLine();
        } catch (IOException e) {
            throw new PrompterException("Failed to read user response", e);
        }
    }

    public void showMessage(String message) throws PrompterException {
        try {
            outputHandler.write(message);
        } catch (IOException e) {
            throw new PrompterException("Failed to show message", e);
        }
    }

}