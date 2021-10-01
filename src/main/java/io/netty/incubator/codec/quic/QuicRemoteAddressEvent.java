package io.netty.incubator.codec.quic;

import java.net.SocketAddress;

/**
 * {@link QuicEvent} which is fired when an QUIC connection remote address was initialized.
 */
public class QuicRemoteAddressEvent implements QuicEvent {

    private final SocketAddress remoteAddress;

    QuicRemoteAddressEvent(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /**
     * The remote {@link SocketAddress} of the connection.
     *
     * @return the remote {@link SocketAddress} of the connection.
     */
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }
}
