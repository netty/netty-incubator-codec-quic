/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic;


import io.netty.util.AsciiString;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class QuicClientSessionCache {
    private final Map<HostPort, byte[]> sessions = new ConcurrentHashMap<>();

    void saveSession(String host, int port, byte[] session) {
        HostPort hostPort = keyFor(host, port);
        if (hostPort != null) {
            sessions.put(hostPort, session);
        }
    }

    byte[] getSession(String host, int port) {
        HostPort hostPort = keyFor(host, port);
        if (hostPort != null) {
            return sessions.get(hostPort);
        }
        return null;
    }

    private static HostPort keyFor(String host, int port) {
        if (host == null && port < 1) {
            return null;
        }
        return new HostPort(host, port);
    }

    /**
     * Host / Port tuple used to find a session in the cache.
     */
    private static final class HostPort {
        private final int hash;
        private final String host;
        private final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
            // Calculate a hashCode that does ignore case.
            this.hash = 31 * AsciiString.hashCode(host) + port;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof HostPort)) {
                return false;
            }
            HostPort other = (HostPort) obj;
            return port == other.port && host.equalsIgnoreCase(other.host);
        }

        @Override
        public String toString() {
            return "HostPort{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }
}
