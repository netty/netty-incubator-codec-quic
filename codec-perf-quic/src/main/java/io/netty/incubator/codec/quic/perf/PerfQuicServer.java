package io.netty.incubator.codec.quic.perf;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.unix.SegmentedDatagramPacket;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.channel.uring.IOUringChannelOption;
import io.netty.incubator.channel.uring.IOUringDatagramChannel;
import io.netty.incubator.channel.uring.IOUringEventLoopGroup;
import io.netty.incubator.codec.quic.EpollQuicUtils;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicChannelOption;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.SegmentedDatagramPacketAllocator;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PerfQuicServer {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(PerfQuicServer.class);

    private PerfQuicServer() { }

    public static void main(String[] args) throws Exception {
        String transport = args.length >= 1 ? args[0] : "nio";
        int chunkSize = args.length >= 2 ? Integer.parseInt(args[1]) : 2048;
        int segments = args.length >= 3 ? Integer.parseInt(args[2]) : 16;

        LOGGER.info("Using transport: " + transport + " chunkSize: " + chunkSize + " segments: " + segments);
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                        selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("perf", "http/0.9").earlyData(true).build();

        QuicServerCodecBuilder codecBuilder = new QuicServerCodecBuilder().sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                // Configure some limits for the maximal number of streams (and the data) that we want to handle.
                .initialMaxData(100000)
                .initialMaxStreamDataBidirectionalLocal(10000)
                .initialMaxStreamDataBidirectionalRemote(10000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .maxRecvUdpPayloadSize(1350)
                .maxSendUdpPayloadSize(1350)
                .activeMigration(false)
                // Setup a token handler. In a production system you would want to implement and provide your custom
                // one.
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                // ChannelHandler that is added into QuicChannel pipeline.
                .handler(new ChannelInboundHandlerAdapter() {
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
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new ByteToMessageDecoder() {
                            private long numBytesRequested = -1;

                            @Override
                            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                                if (in.readableBytes() < 8) {
                                    return;
                                }
                                if (numBytesRequested == -1) {
                                    numBytesRequested = in.readLong();
                                    writeData(ctx);
                                } else {
                                    in.skipBytes(in.readableBytes());
                                }
                            }

                            private void writeData(ChannelHandlerContext ctx) {
                                if (numBytesRequested <= 0) {
                                    return;
                                }
                                ByteBuf buffer = ctx.alloc().directBuffer(chunkSize).writeZero(chunkSize);
                                do {
                                    long size = Math.min(numBytesRequested, chunkSize);
                                    ByteBuf slice = buffer.retainedSlice(0, (int) size).writerIndex((int) size);

                                    ChannelFuture f = ctx.write(slice);
                                    numBytesRequested -= chunkSize;
                                    if (numBytesRequested <= 0) {
                                        f.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                        break;
                                    }
                                } while (ctx.channel().isWritable());
                                buffer.release();
                                ctx.flush();
                            }

                            @Override
                            public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                                if (ctx.channel().isWritable()) {
                                    writeData(ctx);
                                }
                            }
                        });
                    }
                });
        Bootstrap bs = new Bootstrap();
        try {
            switch (transport) {
                case "epoll":
                    bs.group(new EpollEventLoopGroup(1))
                            .channel(EpollDatagramChannel.class)
                            .handler(codecBuilder.option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                                    EpollQuicUtils.newSegmentedAllocator(segments)).build())
                            .option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1500)
                            .option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE)
                            .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(1500 * 16))
                            .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                    new WriteBufferWaterMark(Integer.MAX_VALUE, Integer.MAX_VALUE));
                    break;
                case "nio":
                    bs.group(new NioEventLoopGroup(1))
                            .channel(NioDatagramChannel.class)
                            .handler(codecBuilder.build())
                            .option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE)
                            .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(2048))
                            .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                    new WriteBufferWaterMark(Integer.MAX_VALUE, Integer.MAX_VALUE));
                    break;
                case "io_uring":
                    bs.group(new IOUringEventLoopGroup(1))
                            .channel(IOUringDatagramChannel.class)
                            .handler(codecBuilder.option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                                    new SegmentedDatagramPacketAllocator() {
                                        @Override
                                        public DatagramPacket newPacket(ByteBuf buffer, int segmentSize,
                                                                        InetSocketAddress remoteAddress) {
                                            return new SegmentedDatagramPacket(buffer, segmentSize,remoteAddress);
                                        }

                                        @Override
                                        public int maxNumSegments() {
                                            return segments;
                                        }
                                    }).build())
                            .option(IOUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, 1500)
                            .option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE)
                            .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(1500 * 16))
                            .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                                    new WriteBufferWaterMark(Integer.MAX_VALUE, Integer.MAX_VALUE));
                    break;
                default:
                    throw new IllegalArgumentException("Transport " + transport + " unknown");

            }
            Channel channel = bs.bind(new InetSocketAddress(9999)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            bs.config().group().shutdownGracefully();
        }
    }
}
