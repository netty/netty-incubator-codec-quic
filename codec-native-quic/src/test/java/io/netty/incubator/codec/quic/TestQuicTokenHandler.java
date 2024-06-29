package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Assertions;

import java.net.InetSocketAddress;
import java.nio.ByteOrder;

public class TestQuicTokenHandler implements QuicTokenHandler {
    public static final QuicTokenHandler INSTANCE = new TestQuicTokenHandler();

    @Override
    @SuppressWarnings("deprecation")
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        Assertions.assertEquals(ByteOrder.BIG_ENDIAN, out.order());
        return InsecureQuicTokenHandler.INSTANCE.writeToken(out, dcid, address);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        Assertions.assertEquals(ByteOrder.BIG_ENDIAN, token.order());
        return InsecureQuicTokenHandler.INSTANCE.validateToken(token, address);
    }

    @Override
    public int maxTokenLength() {
        return InsecureQuicTokenHandler.INSTANCE.maxTokenLength();
    }
}
