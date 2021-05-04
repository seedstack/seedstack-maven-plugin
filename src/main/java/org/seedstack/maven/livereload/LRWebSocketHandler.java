/*
 * Copyright © 2013-2021, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.maven.livereload;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketHandler;

/**
 * Modified from: https://github.com/davidB/livereload-jvm
 */
class LRWebSocketHandler extends WebSocketHandler {
    private final ConcurrentLinkedQueue<LRWebSocket> _broadcast = new ConcurrentLinkedQueue<>();
    private final LRProtocol _protocol = new LRProtocol();

    @Override
    public WebSocket doWebSocketConnect(HttpServletRequest request, String protocol) {
        if ("/livereload".equals(request.getPathInfo())) {
            return new LRWebSocket();
        }
        return new WebSocket() {
            @Override
            public void onOpen(Connection connection) {
                connection.close();
            }

            @Override
            public void onClose(int code, String msg) {
            }
        };
    }

    void notifyChange(String path) throws Exception {
        String msg = _protocol.reload(path);
        for (LRWebSocket ws : _broadcast) {
            try {
                ws._connection.sendMessage(msg);
            } catch (IOException e) {
                _broadcast.remove(ws);
                e.printStackTrace();
            }
        }
    }

    class LRWebSocket implements WebSocket.OnTextMessage {
        Connection _connection;

        @Override
        public void onOpen(Connection connection) {
            _connection = connection;
            _broadcast.add(this);
        }

        @Override
        public void onClose(int code, String message) {
            _broadcast.remove(this);
        }

        @Override
        public void onMessage(final String data) {
            try {
                if (_protocol.isHello(data)) {
                    _connection.sendMessage(_protocol.hello());
                }
            } catch (Exception exc) {
                exc.printStackTrace();
            }
        }
    }
}
