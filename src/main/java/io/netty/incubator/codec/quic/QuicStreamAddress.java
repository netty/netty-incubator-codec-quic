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

import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * A {@link SocketAddress} for QUIC stream.
 */
public final class QuicStreamAddress extends SocketAddress {

    final QuicStreamType type;
    final ChannelHandler handler;
    final Promise<QuicStreamChannel> promise;

    QuicStreamAddress(QuicStreamType type, ChannelHandler handler, Promise<QuicStreamChannel> promise) {
        this.type = type;
        this.handler = handler;
        this.promise = promise;
    }

    @Override
    public String toString() {
        return "QuicStreamAddress{" +
                "type=" + type +
                '}';
    }
}
