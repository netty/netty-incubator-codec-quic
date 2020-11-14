/*
 * Copyright 2020 The Netty Project
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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * A {@link QuicConnectionAddress} that can be used to connect too.
 */
public final class QuicConnectionAddress extends SocketAddress {

    // Accessed by QuicheQuicheChannel
    final ByteBuffer connId;
    final InetSocketAddress remote;

    public QuicConnectionAddress(byte[] connId) {
        this(connId, null);
    }

    public QuicConnectionAddress(byte[] connId, InetSocketAddress remote) {
        this(ByteBuffer.wrap(connId.clone()), remote);
    }

    public QuicConnectionAddress(ByteBuffer connId) {
        this(connId, null);
    }

    public QuicConnectionAddress(ByteBuffer connId, InetSocketAddress remote) {
        if (connId.remaining() > Quiche.QUICHE_MAX_CONN_ID_LEN) {
            throw new IllegalArgumentException("Connection ID can only be of max length "
                    + Quiche.QUICHE_MAX_CONN_ID_LEN);
        }
        this.connId = connId;
        this.remote = remote;
    }

    /**
     * Return a random generated {@link QuicConnectionAddress} which can be used on a connected
     * {@link io.netty.channel.Channel}.
     */
    public static QuicConnectionAddress random() {
        return new QuicConnectionAddress(QuicBuilder.randomGenerator().newId());
    }

    /**
     * Return a random generated {@link QuicConnectionAddress} that connects the {@link QuicChannel} to the given
     * remote address.
     */
    public static QuicConnectionAddress random(InetSocketAddress remote) {
        return new QuicConnectionAddress(QuicBuilder.randomGenerator().newId(), remote);
    }
}
