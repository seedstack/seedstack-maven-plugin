/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.fusesource.jansi.Ansi;
import org.seedstack.maven.components.prompter.PromptException;
import org.seedstack.maven.components.prompter.Prompter;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component(role = Inquirer.class)
public class ConsoleUIInquirer implements Inquirer {
    @Requirement
    private Prompter prompter;
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

    private Map<String, Object> processQuestionGroup(QuestionGroup questionGroup) throws InquirerException {
        Map<String, Object> answers = new HashMap<>();
        for (Question question : questionGroup.getQuestions()) {
            boolean shouldAsk = true;
            for (Condition condition : question.getConditions()) {
                if (!isSatisfied(condition, answers)) {
                    shouldAsk = false;
                }
            }
            if (shouldAsk) {
                Object answer = ask(question);
                if (answer != null) {
                    answers.put(question.getName(), answer);
                }
            }
        }
        return answers;
    }

    @Override
    public Object ask(Question question) throws InquirerException {
        do {
            try {
                String message = question.getMessage();
                String defaultValue = question.getDefaultValue();
                Question.Type type = question.getType();
                switch (question.getStyle()) {
                    case CONFIRM:
                        return prompter.promptConfirmation(message, defaultValue);
                    case INPUT:
                        return coerce(prompter.promptInput(message, defaultValue), type);
                    case CHECKBOX:
                        return coerce(prompter.promptCheckbox(message, question.getValues()), type);
                    case LIST:
                        return coerce(prompter.promptList(message, question.getValues()), type);
                    case CHOICE:
                        return coerce(prompter.promptChoice(message, question.getValues()), type);
                    default:
                        return null;
                }
            } catch (AnswerValidationException e) {
                System.out.println(Ansi.ansi().fgBright(Ansi.Color.RED).a("X " + e.getMessage()).reset().toString());
            } catch (PromptException e) {
                throw new InquirerException("Unable to ask question", e);
            }

        } while (true);
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
}
