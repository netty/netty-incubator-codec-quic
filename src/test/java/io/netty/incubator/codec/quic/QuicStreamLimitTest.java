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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class QuicStreamLimitTest extends AbstractQuicTest {

    @Test
    public void testStreamLimitEnforcedWhenCreatingViaClientBidirectional() throws Throwable {
        testStreamLimitEnforcedWhenCreatingViaClient(QuicStreamType.BIDIRECTIONAL);
    }

    @Test
    public void testStreamLimitEnforcedWhenCreatingViaClientUnidirectional() throws Throwable {
        testStreamLimitEnforcedWhenCreatingViaClient(QuicStreamType.UNIDIRECTIONAL);
    }

    private static void testStreamLimitEnforcedWhenCreatingViaClient(QuicStreamType type) throws Throwable {
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder().initialMaxStreamsBidirectional(1)
                        .initialMaxStreamsUnidirectional(1),
                InsecureQuicTokenHandler.INSTANCE,
                null, new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                            ctx.close();
                        }
                    }
                });
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel).handler(
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            if (evt instanceof QuicStreamLimitChangedEvent) {
                                if (latch.getCount() == 0) {
                                    latch2.countDown();
                                } else {
                                    latch.countDown();
                                }
                            }
                        }
                    }).streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect().get();
            latch.await();
            assertEquals(1, quicChannel.peerAllowedStreams(QuicStreamType.UNIDIRECTIONAL));
            assertEquals(1, quicChannel.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL));
            QuicStreamChannel stream = quicChannel.createStream(
                    type, new ChannelInboundHandlerAdapter()).get();

            assertEquals(0, quicChannel.peerAllowedStreams(type));

            // Second stream creation should fail.
            Throwable cause = quicChannel.createStream(
                    type, new ChannelInboundHandlerAdapter()).await().cause();
            assertThat(cause, CoreMatchers.instanceOf(IOException.class));
            stream.close().sync();
            latch2.await();

            assertEquals(1, quicChannel.peerAllowedStreams(type));
            quicChannel.close().sync();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testStreamLimitEnforcedWhenCreatingViaServerBidirectional() throws Throwable {
        testStreamLimitEnforcedWhenCreatingViaServer(QuicStreamType.BIDIRECTIONAL);
    }

    @Test
    public void testStreamLimitEnforcedWhenCreatingViaServerUnidirectional() throws Throwable {
        testStreamLimitEnforcedWhenCreatingViaServer(QuicStreamType.UNIDIRECTIONAL);
    }

    private static void testStreamLimitEnforcedWhenCreatingViaServer(QuicStreamType type) throws Throwable {
        Promise<Void> streamPromise = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Throwable> stream2Promise = ImmediateEventExecutor.INSTANCE.newPromise();
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder(),
                InsecureQuicTokenHandler.INSTANCE,
                new ChannelInboundHandlerAdapter() {

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        channel.createStream(type, new ChannelInboundHandlerAdapter())
                                .addListener((Future<QuicStreamChannel> future) -> {
                                    if (future.isSuccess()) {
                                        QuicStreamChannel stream = future.getNow();
                                        streamPromise.setSuccess(null);
                                        channel.createStream(type, new ChannelInboundHandlerAdapter())
                                                .addListener((Future<QuicStreamChannel> f) -> {
                                                    stream.close();
                                                    stream2Promise.setSuccess(f.cause());
                                                });
                                    } else {
                                        streamPromise.setFailure(future.cause());
                                    }
                                });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }, new ChannelInboundHandlerAdapter() {
                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                });
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder()
                .initialMaxStreamsBidirectional(1).initialMaxStreamsUnidirectional(1));
        try {
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(address)
                    .connect().get();
            streamPromise.sync();
            // Second stream creation should fail.
            assertThat(stream2Promise.get(), CoreMatchers.instanceOf(IOException.class));
            quicChannel.close().sync();
        } finally {
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }
}
