package io.netty.incubator.codec.quic.perf;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerfUdp {

    public static void main(String[] args) throws Exception {
        String transport = args.length >= 1 ? args[0] : "nio";

        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 9090);
        Bootstrap bootstrap = new Bootstrap();

        final byte[] bytes = new byte[512];
        final AtomicBoolean done = new AtomicBoolean();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    while (!done.get()) {
                        socket.send(new java.net.DatagramPacket(bytes, bytes.length, address));
                    }
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread t = new Thread(runnable);
            t.start();
            threads.add(t);
        }

         bootstrap.handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    private int i = 0;
                    private long startTime;

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        startTime = System.nanoTime();
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                        i++;

                        if (i % 100000 == 0) {
                            System.err.println("Received " + i + " messages");
                        }
                        if (i == 5000000) {
                            System.err.println(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime) +"s");
                            ctx.channel().config().setAutoRead(false);
                            ctx.close();
                            done.set(true);
                        }
                    }
                });
        switch (transport) {
            case "nio":
                bootstrap.channel(NioDatagramChannel.class)
                        .group(new NioEventLoopGroup(1))
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(512));
                break;
            case "epoll":
                bootstrap.channel(EpollDatagramChannel.class)
                        .group(new EpollEventLoopGroup(1))
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(32 * 512))
                        .option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 512);
                break;
            case "io_uring":
                bootstrap.channel(IOUringDatagramChannel.class)
                        .group(new IOUringEventLoopGroup(1))
                        .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(32 * 512))
                        .option(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 512);
                break;
            default:
                throw new IllegalArgumentException();
        }
        Channel channel = bootstrap.bind(address).syncUninterruptibly().channel();

        channel.closeFuture().syncUninterruptibly();

        for (Thread t: threads) {
            t.join();
        }
        bootstrap.config().group().shutdownGracefully();
    }
}
