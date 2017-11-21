/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.maven.livereload;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import org.json.simple.JSONValue;

/**
 * Modified from: https://github.com/davidB/livereload-jvm
 * @author dwayne
 */
class LRProtocol {
    String hello() {
        LinkedList<String> protocols = new LinkedList<String>();
        protocols.add("http://livereload.com/protocols/official-7");

        LinkedHashMap<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("command", "hello");
        obj.put("protocols", protocols);
        obj.put("serverName", "livereload-jvm");
        return JSONValue.toJSONString(obj);
    }

    String alert(String msg) throws Exception {
        LinkedHashMap<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("command", "alert");
        obj.put("message", msg);
        return JSONValue.toJSONString(obj);
    }

    String reload(String path) throws Exception {
        LinkedHashMap<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("command", "reload");
        obj.put("path", path);
        obj.put("liveCSS", true);
        return JSONValue.toJSONString(obj);
    }

    @SuppressWarnings("unchecked")
    boolean isHello(String data) throws Exception {
        Object obj = JSONValue.parse(data);
        boolean back = obj instanceof Map;
        back = back && "hello".equals(((Map<Object, Object>) obj).get("command"));
        return back;
    }
}
