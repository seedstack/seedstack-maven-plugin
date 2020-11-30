/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.livereload;

import java.io.IOException;
import java.net.MalformedURLException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.resource.Resource;

/**
 * Modified from: https://github.com/davidB/livereload-jvm
 */
public class LRServer {
    private final int _port;
    private Server _server;
    private LRWebSocketHandler _wsHandler;

    public LRServer(int port) {
        this._port = port;
    }

    private void init() throws Exception {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(_port);

        ResourceHandler rHandler = new ResourceHandler() {
            @Override
            public Resource getResource(String path) throws MalformedURLException {
                if ("/livereload.js".equals(path)) {
                    try {
                        return Resource.newResource(LRServer.class.getResource(path));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                return null;
            }
        };

        _wsHandler = new LRWebSocketHandler();
        _wsHandler.setHandler(rHandler);

        _server = new Server();
        _server.setHandler(_wsHandler);
        _server.addConnector(connector);
    }

    public void start() throws Exception {
        this.init();
        _server.start();
    }

    public void stop() throws Exception {
        _server.stop();
    }

    public void notifyChange(String path) throws Exception {
        _wsHandler.notifyChange(path);
    }
}
