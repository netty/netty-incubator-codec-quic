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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

/**
 * A QUIC {@link ChannelConfig}.
 */
public interface QuicChannelConfig extends ChannelConfig {

    @Override
    @Deprecated
    QuicChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    QuicChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    QuicChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    QuicChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    QuicChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    QuicChannelConfig setAutoRead(boolean autoRead);

    @Override
    QuicChannelConfig setAutoClose(boolean autoClose);

    @Override
    QuicChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    QuicChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);

    @Override
    QuicChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    QuicChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    /**
     * Return the server name parameter used to verify peer's certificate.
     *
     * @return the server name parameter.
     */
    String getPeerCertServerName();

    /**
     * Set set server name for peer's certificate verification.
     *
     * <strong>Be aware this config setting can only be adjusted before the
     * connection is established.</strong>
     *
     * @param peerCertServerName    the server name parameter.
     * @return                      the instance itself.
     */
    QuicChannelConfig setPeerCertServerName(String peerCertServerName);
}
