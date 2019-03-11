/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.components.inquirer;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Condition {
    private String ref;
    private Operator op;
    private Object val;

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Operator getOp() {
        return op;
    }

    public void setOp(Operator op) {
        this.op = op;
    }

    public Object getVal() {
        return val;
    }

    public void setVal(Object val) {
        this.val = val;
    }

    public enum Operator {
        EQ,
        NEQ,
        ABSENT,
        PRESENT;

        @JsonCreator
        public static Operator fromString(String key) {
            for (Operator type : Operator.values()) {
                if (type.name().equalsIgnoreCase(key)) {
                    return type;
                }
            }
            return null;
        }
    }
}
