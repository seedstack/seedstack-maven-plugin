/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.codeshelf.consoleui.elements.ConfirmChoice;
import de.codeshelf.consoleui.prompt.*;
import de.codeshelf.consoleui.prompt.builder.*;
import org.codehaus.plexus.component.annotations.Component;
import org.fusesource.jansi.Ansi;
import org.seedstack.maven.components.AnswerValidationException;
import org.seedstack.maven.components.Inquirer;
import org.seedstack.maven.components.InquirerException;

import java.io.IOException;
import java.net.URL;
import java.util.*;

@Component(role = Inquirer.class)
public class ConsoleUIInquirer implements Inquirer {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> inquire(URL inquiryURL) throws InquirerException {
        Inquiry inquiry;
        try {
            inquiry = objectMapper.readValue(inquiryURL, Inquiry.class);
        } catch (IOException e) {
            throw new InquirerException("Cannot execute inquiry at " + inquiryURL.toExternalForm(), e);
        }

        Map<String, Object> answers = new HashMap<>();
        for (QuestionGroup questionGroup : inquiry.getQuestionGroups()) {
            System.out.println();
            String groupLabel = questionGroup.getLabel();
            if (groupLabel != null) {
                System.out.println(Ansi.ansi().fg(Ansi.Color.YELLOW).a(groupLabel).reset());
            }
            answers.put(questionGroup.getName(), processQuestionGroup(questionGroup));
        }
        System.out.println();
        return answers;
    }

    @Override
    public String ask(String message) throws InquirerException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        InputValueBuilder input = consolePrompt
                .getPromptBuilder()
                .createInputPrompt()
                .name("input")
                .message(message);
        try {
            return ((InputResult) consolePrompt.prompt(input.addPrompt().build()).get("input")).getInput();
        } catch (IOException e) {
            throw new InquirerException(e);
        }
    }

    @Override
    public String ask(String message, List<String> possibleValues) throws InquirerException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        ListPromptBuilder list = consolePrompt
                .getPromptBuilder()
                .createListPrompt()
                .name("list")
                .message(message);
        for (String possibleValue : possibleValues) {
            list.newItem(possibleValue).text(possibleValue).add();
        }
        try {
            return ((ListResult) consolePrompt.prompt(list.addPrompt().build()).get("list")).getSelectedId();
        } catch (IOException e) {
            throw new InquirerException(e);
        }
    }

    private Map<String, Object> processQuestionGroup(QuestionGroup questionGroup) throws InquirerException {
        ConsolePrompt consolePrompt = new ConsolePrompt();
        Map<String, Object> answers = new HashMap<>();
        for (Question question : questionGroup.getQuestions()) {
            boolean shouldAsk = true;
            for (Condition condition : question.getConditions()) {
                if (!isSatisfied(condition, answers)) {
                    shouldAsk = false;
                }
            }
            if (shouldAsk) {
                boolean askAgain;
                do {
                    try {
                        Object answer = askQuestion(consolePrompt, question);
                        if (answer != null) {
                            answers.put(question.getName(), answer);
                        }
                        askAgain = false;
                    } catch (AnswerValidationException e) {
                        System.out.println(Ansi.ansi().fgBright(Ansi.Color.RED).a("X " + e.getMessage()).reset().toString());
                        askAgain = true;
                    }

                } while (askAgain);
            }
        }
        return answers;
    }

    private boolean isSatisfied(Condition condition, Map<String, Object> answers) {
        String ref = condition.getRef();
        switch (condition.getOp()) {
            case EQ:
                return answers.containsKey(ref) && answers.get(ref).equals(condition.getVal());
            case NEQ:
                return answers.containsKey(ref) && !answers.get(ref).equals(condition.getVal());
            case PRESENT:
                return answers.containsKey(ref);
            case ABSENT:
                return !answers.containsKey(ref);
            default:
                return false;
        }
    }

    private Object askQuestion(ConsolePrompt consolePrompt, Question question) throws InquirerException {
        PromptBuilder promptBuilder = consolePrompt.getPromptBuilder();
        String message = question.getMessage();
        String name = question.getName();
        String defaultValue = question.getDefaultValue();
        switch (question.getStyle()) {
            case CONFIRM:
                addConfirmation(promptBuilder, name, message, defaultValue);
                break;
            case INPUT:
                addInput(promptBuilder, name, message, defaultValue);
                break;
            case CHECKBOX:
                addCheckbox(promptBuilder, name, message, question.getValues());
                break;
            case LIST:
                addList(promptBuilder, name, message, question.getValues());
                break;
            case CHOICE:
                addChoice(promptBuilder, name, message, question.getValues());
                break;
        }

        PromtResultItemIF result;
        try {
            result = consolePrompt.prompt(promptBuilder.build()).values().iterator().next();
        } catch (IOException e) {
            throw new InquirerException("Unable to prompt question", e);
        }

        if (result instanceof ConfirmResult) {
            return ((ConfirmResult) result).getConfirmed() == ConfirmChoice.ConfirmationValue.YES;
        } else if (result instanceof ExpandableChoiceResult) {
            return coerce(((ExpandableChoiceResult) result).getSelectedId(), question.getType());
        } else if (result instanceof CheckboxResult) {
            return coerce(((CheckboxResult) result).getSelectedIds(), question.getType());
        } else if (result instanceof InputResult) {
            return coerce(((InputResult) result).getInput(), question.getType());
        } else if (result instanceof ListResult) {
            return coerce(((ListResult) result).getSelectedId(), question.getType());
        } else {
            return null;
        }
    }

    private Set<Object> coerce(Set<String> values, Question.Type type) throws AnswerValidationException {
        Set<Object> result = new HashSet<>();
        for (String value : values) {
            result.add(coerce(value, type));
        }
        return result;
    }

    private Object coerce(String value, Question.Type type) throws AnswerValidationException {
        switch (type) {
            case STRING:
                return value;
            case DOUBLE:
                try {
                    return Double.parseDouble(value);
                } catch (Exception e) {
                    throw new AnswerValidationException("Not an valid floating number");
                }
            case INTEGER:
                try {
                    return Integer.parseInt(value);
                } catch (Exception e) {
                    throw new AnswerValidationException("Not an valid integer");
                }
            case BOOLEAN:
                return Boolean.parseBoolean(value);
            default:
                return value;
        }
    }

    private void addChoice(PromptBuilder promptBuilder, String name, String message, List<Value> values) {
        ExpandableChoicePromptBuilder choicePromptBuilder = promptBuilder.createChoicePrompt()
                .name(name)
                .message(message);
        for (Value value : values) {
            choicePromptBuilder.newItem()
                    .name(value.getName())
                    .message(value.getLabel())
                    .key(value.getKey())
                    .add();
        }
        choicePromptBuilder.addPrompt();
    }

    private void addList(PromptBuilder promptBuilder, String name, String message, List<Value> values) {
        ListPromptBuilder listPromptBuilder = promptBuilder.createListPrompt()
                .name(name)
                .message(message);
        for (Value value : values) {
            listPromptBuilder.newItem()
                    .name(value.getName())
                    .text(value.getLabel())
                    .add();
        }
        listPromptBuilder.addPrompt();
    }

    private void addCheckbox(PromptBuilder promptBuilder, String name, String message, List<Value> values) {
        CheckboxPromptBuilder checkboxPromptBuilder = promptBuilder.createCheckboxPrompt()
                .name(name)
                .message(message);
        for (Value value : values) {
            checkboxPromptBuilder.newItem()
                    .name(value.getName())
                    .text(value.getLabel())
                    .add();
        }
        checkboxPromptBuilder.addPrompt();
    }

    private void addInput(PromptBuilder promptBuilder, String name, String message, String defaultValue) {
        InputValueBuilder inputValueBuilder = promptBuilder.createInputPrompt()
                .name(name)
                .message(message);
        if (defaultValue != null) {
            inputValueBuilder.defaultValue(defaultValue);
        }
        inputValueBuilder.addPrompt();
    }

    private void addConfirmation(PromptBuilder promptBuilder, String name, String message, String defaultValue) {
        ConfirmPromptBuilder confirmPromptBuilder = promptBuilder.createConfirmPromp()
                .name(name)
                .message(message);
        if (defaultValue != null) {
            confirmPromptBuilder.defaultValue(ConfirmChoice.ConfirmationValue.valueOf(defaultValue.toUpperCase(Locale.ENGLISH)));
        }
        confirmPromptBuilder.addPrompt();
    }
}
