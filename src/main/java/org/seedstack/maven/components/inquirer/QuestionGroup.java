/*
 * Copyright © 2013-2018, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionGroup {
    private String name;
    private String label;
    private int order;
    private List<Question> questions = new ArrayList<>();

    public QuestionGroup() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public void setQuestions(List<Question> questions) {
        this.questions = new ArrayList<>(questions);
    }
}
