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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class QuicPortReuseTest extends AbstractQuicTest {

    static boolean doesSupportSoReusePort() {
        return QuicTestUtils.soReusePortOption() != null;
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @EnabledIf(value = "doesSupportSoReusePort")
    public void testConnectAndStreamPriority(Executor executor) throws Throwable {
        int numBytes = 1000;
        final AtomicInteger byteCounter = new AtomicInteger();

        int numBinds = 4;
        int numConnects = 16;

        List<Channel> channels = new ArrayList<>();
        Bootstrap serverBootstrap = QuicTestUtils.newServerBootstrap();
        serverBootstrap.option(QuicTestUtils.soReusePortOption(), true).handler(new QuicCodecDispatcher() {
            @Override
            protected void initChannel(Channel channel, int localConnectionIdLength,
                                       QuicConnectionIdGenerator idGenerator) {
                ChannelHandler codec = QuicTestUtils.newQuicServerBuilder(executor)
                        .localConnectionIdLength(localConnectionIdLength)
                        .connectionIdAddressGenerator(idGenerator).streamHandler(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                byteCounter.addAndGet(((ByteBuf) msg).readableBytes());
                                ReferenceCountUtil.release(msg);
                            }
                        }).build();
                channel.pipeline().addLast(codec);
            }
        });

        for (int i = 0; i < numBinds; i++) {
            channels.add(serverBootstrap.bind().sync().channel());
        }

        Channel channel = QuicTestUtils.newClient(executor);
        try {
            List<QuicChannel> clients = new ArrayList<>();
            for (int i = 0; i < numConnects; i++) {
                QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                        .handler(new ChannelInboundHandlerAdapter())
                        .streamHandler(new ChannelInboundHandlerAdapter())
                        .remoteAddress(channels.get(0).localAddress())
                        .connect()
                        .get();
                clients.add(quicChannel);
            }

            for (QuicChannel quicChannel: clients) {
                quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                        new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                ctx.writeAndFlush(Unpooled.directBuffer().writeZero(numBytes))
                                        .addListener(ChannelFutureListener.CLOSE);
                            }
                        });
            }

            while (byteCounter.get() != numConnects * numBytes) {
                Thread.sleep(100);
            }
            for (QuicChannel quicChannel: clients) {
                quicChannel.close().sync();
            }
            for (Channel serverChannel: channels) {
                serverChannel.close().sync();
            }
        } finally {
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }
}
