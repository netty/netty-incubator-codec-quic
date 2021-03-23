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
package io.netty.incubator.codec.quic.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.Quic;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicChannelOption;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.SegmentedDatagramPacketAllocator;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class QuicServerExample {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicServerExample.class);

    private QuicServerExample() { }

    public static void main(String[] args) throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("http/0.9").build();
        EventLoopGroup group = null;
        Class<? extends DatagramChannel> channelType;
        try {
            if (Epoll.isAvailable()) {
                group = new EpollEventLoopGroup(1);
                channelType = EpollDatagramChannel.class;
            } else {
                group = new NioEventLoopGroup(1);
                channelType = NioDatagramChannel.class;
            }
            // Use GSO if possible.
            final SegmentedDatagramPacketAllocator segmentedAllocator = Quic.newSegmentedAllocator(channelType);

            ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100)
                    .initialMaxStreamsUnidirectional(100)

                    // Setup a token handler. In a production system you would want to implement and provide your custom
                    // one.
                    .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    // ChannelHandler that is added into QuicChannel pipeline.
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            QuicChannel channel = (QuicChannel) ctx.channel();
                            // Create streams etc..
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                                if (f.isSuccess()) {
                                    LOGGER.info("Connection closed: {}", f.getNow());
                                }
                            });
                        }

                        @Override
                        public boolean isSharable() {
                            return true;
                        }
                    })
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch)  {
                            // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                            ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf byteBuf = (ByteBuf) msg;
                                    try {
                                        if (byteBuf.toString(CharsetUtil.US_ASCII).trim().equals("GET /")) {
                                            ByteBuf buffer = ctx.alloc().directBuffer();
                                            buffer.writeCharSequence("Hello World!\r\n", CharsetUtil.US_ASCII);
                                            // Write the buffer and shutdown the output by writing a FIN.
                                            ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                        }
                                    } finally {
                                        byteBuf.release();
                                    }
                                }
                            });
                        }
                    })
                    .option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR, segmentedAllocator)
                    .build();

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(channelType)
                    .handler(codec)
                    .bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            if (group != null) {
                group.shutdownGracefully();
            }
        }
    }
}
