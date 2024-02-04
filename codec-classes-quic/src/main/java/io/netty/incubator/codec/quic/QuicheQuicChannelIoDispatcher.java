/*
 * Copyright 2024 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

final class QuicheQuicChannelIoDispatcher {
    private final Map<ByteBuffer, QuicheQuicChannel> connectionIdToChannel = new HashMap<>();
    private final Map<QuicheQuicChannel, Set<ByteBuffer>> channelToConnectionIds = new HashMap<>();
    private final Queue<QuicheQuicChannel> channelRecvComplete = new ArrayDeque<>();
    private final Channel channel;

    private static final AttributeKey<QuicheQuicChannelIoDispatcher> IO_DISPATCHER =
            AttributeKey.newInstance(QuicheQuicChannelIoDispatcher.class.getName());

    // TODO: Handle this smarter without the need to move through all channels.
    private final Runnable timeoutTask = () -> {
        // null out the future as we will schedule another timeout if needed.
        timeoutFuture = null;
        long nextTimeout = Long.MAX_VALUE;
        Iterator<Map.Entry<QuicheQuicChannel, Set<ByteBuffer>>> iterator =
                channelToConnectionIds.entrySet().iterator();
        try {
            while (iterator.hasNext()) {
                Map.Entry<QuicheQuicChannel, Set<ByteBuffer>> entry = iterator.next();
                QuicheQuicChannel quicChannel = entry.getKey();
                quicChannel.onTimeout();

                long timeout = closeOrNextTimeout(quicChannel);
                if (timeout > 0) {
                    nextTimeout = Math.min(nextTimeout, timeout);
                }
            }
        } finally {
            scheduleTimeoutIfNeeded(nextTimeout);
        }
    };

    private final BiConsumer<QuicheQuicChannel, ByteBuffer> removeIdConsumer = this::removeMapping;
    private final BiConsumer<QuicheQuicChannel, ByteBuffer> addIdConsumer = this::addMapping;

    private ScheduledFuture<?> timeoutFuture;

    static QuicheQuicChannelIoDispatcher forChannel(Channel channel) {
        return channel.attr(IO_DISPATCHER).get();
    }

    QuicheQuicChannelIoDispatcher(Channel channel) {
        this.channel = channel;
        // Store in the Channel as attribute so we can retrieve it later.
        this.channel.attr(IO_DISPATCHER).set(this);
    }

    QuicheQuicChannel get(ByteBuffer key) {
        return connectionIdToChannel.get(key);
    }

    void add(QuicheQuicChannel quicChannel, ByteBuffer id) {
        Set<ByteBuffer> ids = new HashSet<>();
        if (id != null) {
            ids.add(id);
        }
        Set<ByteBuffer> oldIds = channelToConnectionIds.put(quicChannel, ids);
        assert oldIds == null;
        if (id != null) {
            QuicheQuicChannel oldChannel = connectionIdToChannel.put(id, quicChannel);
            assert oldChannel == null;
        }
    }
    
    void remove(QuicheQuicChannel quicChannel) {
        Set<ByteBuffer> ids = channelToConnectionIds.remove(quicChannel);
        if (ids != null) {
            for (ByteBuffer id : ids) {
                Channel ch = connectionIdToChannel.remove(id);
                assert ch == quicChannel;
            }
        }
    }

    void recv(QuicheQuicChannel quicChannel, InetSocketAddress sender,
              InetSocketAddress recipient, ByteBuf buffer) {
        assert channelToConnectionIds.containsKey(quicChannel);
        if (quicChannel.recv(sender, recipient, buffer)) {
            // We need to call recvComplete() later.
            channelRecvComplete.add(quicChannel);
        }
        quicChannel.processRetiredSourceConnectionId(removeIdConsumer);
        quicChannel.processNewSourceConnectionIds(addIdConsumer);
    }

    void recvCompleteAll() {
        long nextTimeout = Long.MAX_VALUE;
        try {
            for (; ; ) {
                QuicheQuicChannel quicChannel = channelRecvComplete.poll();
                if (quicChannel == null) {
                    break;
                }
                quicChannel.recvComplete();
                // Also call onTimeout() just to be sure we handled it if needed.
                quicChannel.onTimeout();
                long timeout = closeOrNextTimeout(quicChannel);
                if (timeout > 0) {
                    nextTimeout = Math.min(nextTimeout, timeout);
                }
            }
        } finally {
            scheduleTimeoutIfNeeded(nextTimeout);
        }
    }

    void writableAll() {
        long nextTimeout = Long.MAX_VALUE;
        try {
            Iterator<Map.Entry<QuicheQuicChannel, Set<ByteBuffer>>> iterator =
                    channelToConnectionIds.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<QuicheQuicChannel, Set<ByteBuffer>> entry = iterator.next();
                QuicheQuicChannel quicChannel = entry.getKey();
                quicChannel.writable();
                long timeout = closeOrNextTimeout(quicChannel);
                if (timeout > 0) {
                    nextTimeout = Math.min(nextTimeout, timeout);
                }
            }
        } finally {
            scheduleTimeoutIfNeeded(nextTimeout);
        }
    }

    void closeAll() {
        // Use a copy of the array as closing the channel may cause an unwritable event that could also
        // remove channels.
        for (QuicheQuicChannel ch : channelToConnectionIds.keySet().toArray(new QuicheQuicChannel[0])) {
            ch.forceClose();
        }
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
            timeoutFuture = null;
        }
        channelToConnectionIds.clear();
        connectionIdToChannel.clear();
        channelRecvComplete.clear();
    }

    void flushAll() {
        channel.flush();
    }

    ChannelFuture connect(@SuppressWarnings("unused") QuicheQuicChannel quicChannel, QuicheQuicChannelAddress addr) {
        return channel.connect(addr);
    }

    ChannelFuture write(@SuppressWarnings("unused") QuicheQuicChannel quicChannel, Object msg) {
        return channel.write(msg);
    }

    void scheduleTimeoutIfNeeded(long nextTimeout) {
        if (nextTimeout != Long.MAX_VALUE && nextTimeout > 0) {
            if (timeoutFuture == null) {
                timeoutFuture = channel.eventLoop().schedule(timeoutTask, nextTimeout, TimeUnit.NANOSECONDS);
            } else if (timeoutFuture.getDelay(TimeUnit.NANOSECONDS) > nextTimeout) {
                // Cancel the old timeout and schedule an earlier one.
                timeoutFuture.cancel(false);
                timeoutFuture = channel.eventLoop().schedule(timeoutTask, nextTimeout, TimeUnit.NANOSECONDS);
            }
        }
    }

    private long closeOrNextTimeout(QuicheQuicChannel quicChannel) {
        boolean closed = quicChannel.freeIfClosed();
        if (closed) {
            // If this is closed we also should have it removed from the mappings.
            assert !channelToConnectionIds.containsKey(quicChannel);
            return -1;
        }
        return quicChannel.nextTimeout();
    }

    private void addMapping(QuicheQuicChannel quicChannel, ByteBuffer id) {
        QuicheQuicChannel oldChannel = connectionIdToChannel.put(id, quicChannel);
        assert oldChannel == null;
        Set<ByteBuffer> ids = channelToConnectionIds.get(quicChannel);
        boolean added = ids.add(id);
        assert added;
    }

    private void removeMapping(QuicheQuicChannel quicChannel, ByteBuffer id) {
        QuicheQuicChannel ch = connectionIdToChannel.remove(id);
        assert ch == quicChannel;
        Set<ByteBuffer> ids = channelToConnectionIds.get(ch);
        boolean removed = ids.remove(id);
        assert removed;
    }
}
