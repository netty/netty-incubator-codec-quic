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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class QuicReadableTest {

    @Test
    public void testCorrectlyHandleReadableStreams() throws Exception  {
        int numOfStreams = 1024;
        int readStreams = 512;
        int expectedDataRead = readStreams * Integer.BYTES;
        final CountDownLatch latch = new CountDownLatch(numOfStreams);
        final AtomicInteger bytesRead = new AtomicInteger();
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder().initialMaxStreamsBidirectional(5000),
                InsecureQuicTokenHandler.INSTANCE,
                new QuicChannelInitializer(new ChannelInboundHandlerAdapter() {
                    private int counter;
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) {
                        // Ensure we dont read from the streams so all of these will be reported as readable
                        ctx.channel().config().setAutoRead(false);
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        counter++;
                        latch.countDown();
                        if (counter > readStreams) {
                            // Now set it to readable again for some channels
                            ctx.channel().config().setAutoRead(true);
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf buffer = (ByteBuf) msg;
                        bytesRead.addAndGet(buffer.readableBytes());
                        buffer.release();
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                }));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        ChannelFuture future = null;
        try {
            Bootstrap bootstrap = QuicTestUtils.newClientBootstrap();
            future = bootstrap
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());
            QuicChannel channel = (QuicChannel) future.channel();

            ByteBuf data = Unpooled.directBuffer().writeLong(8);
            List<Channel> streams = new ArrayList<>();
            for (int i = 0; i < numOfStreams; i++) {
                QuicStreamChannel stream = channel.createStream(
                        QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter()).get();
                streams.add(stream.writeAndFlush(data.retainedSlice()).sync().channel());
            }
            data.release();
            latch.await();
            while (bytesRead.get() < expectedDataRead) {
                Thread.sleep(50);
            }
            for (Channel stream: streams) {
                stream.close().sync();
            }
            channel.close().sync();
        } finally {
            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }
}
