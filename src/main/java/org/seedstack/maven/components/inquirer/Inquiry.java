/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Inquiry {
    private List<QuestionGroup> questionGroups = new ArrayList<>();

    public Inquiry() {
    }

    public List<QuestionGroup> getQuestionGroups() {
        return Collections.unmodifiableList(questionGroups);
    }

    @JsonAnySetter
    public void addQuestionGroup(String name, QuestionGroup questionGroup) {
        questionGroup.setName(name);
        this.questionGroups.add(questionGroup);
        Collections.sort(this.questionGroups, new Comparator<QuestionGroup>() {
            @Override
            public int compare(QuestionGroup o1, QuestionGroup o2) {
                return Integer.compare(o1.getOrder(), o2.getOrder());
            }
        });
    }
}
