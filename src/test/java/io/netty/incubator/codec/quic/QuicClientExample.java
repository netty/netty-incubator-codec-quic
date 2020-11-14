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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;

public final class QuicClientExample {

    private QuicClientExample() { }

    public static void main(String[] args) throws Exception {
        byte[] proto = new byte[] {
                0x05, 'h', 'q', '-', '2', '9',
                0x05, 'h', 'q', '-', '2', '8',
                0x05, 'h', 'q', '-', '2', '7',
                0x08, 'h', 't', 't', 'p', '/', '0', '.', '9'
        };

        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    // We don't want any special handling of the channel so just use a dummy handler.
                    .handler(new ChannelHandlerAdapter() { })
                    .bind(0).sync().channel();

            Bootstrap quicClientBootstrap = new QuicClientBuilder()
                    .certificateChain("./src/test/resources/cert.crt")
                    .privateKey("./src/test/resources/cert.key")
                    .applicationProtocols(proto)
                    .maxIdleTimeout(5000)
                    .maxUdpPayloadSize(Quic.MAX_DATAGRAM_SIZE)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100)
                    .initialMaxStreamsUnidirectional(100)
                    .disableActiveMigration(true)
                    .enableEarlyData().buildBootstrap(channel);

            QuicChannel quicChannel = (QuicChannel) quicClientBootstrap
                            .handler(new QuicChannelInitializer(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    // We don't want to handle streams created by the server side, just close the
                                    // stream and so send a fin.
                                    ctx.close();
                                }
                            })).connect(QuicConnectionAddress.random(
                                    new InetSocketAddress(NetUtil.LOCALHOST4, 9999))).sync().channel();

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf byteBuf = (ByteBuf) msg;
                            System.err.println(byteBuf.toString(CharsetUtil.US_ASCII));
                            byteBuf.release();
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            // Close the connection once the remote peer did close this stream.
                            ctx.channel().parent().close();
                        }
                    }).sync().getNow();
            ByteBuf buffer = Unpooled.directBuffer();
            buffer.writeCharSequence("GET /\r\n", CharsetUtil.US_ASCII);
            streamChannel.writeAndFlush(buffer);

            streamChannel.closeFuture().sync();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
