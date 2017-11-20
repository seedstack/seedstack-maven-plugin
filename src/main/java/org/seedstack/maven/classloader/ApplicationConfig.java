/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.classloader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationConfig {
    private static final String BASE_PACKAGES = "basePackages";
    private Map<String, Object> application;

    public Map<String, Object> getApplication() {
        return application;
    }

    public void setApplication(Map<String, Object> application) {
        this.application = application;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getBasePackages() {
        Object basePackages = application.get(BASE_PACKAGES);
        if (basePackages instanceof Collection) {
            return new HashSet<>((Collection<String>) basePackages);
        } else if (basePackages instanceof String) {
            Set<String> result = new HashSet<>();
            result.add(((String) basePackages));
            return result;
        } else {
            return new HashSet<>();
        }
    }
}
