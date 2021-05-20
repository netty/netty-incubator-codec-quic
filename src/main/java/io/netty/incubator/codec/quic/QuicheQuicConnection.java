/*
 * Copyright 2021 The Netty Project
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
import io.netty.util.ReferenceCounted;

import java.util.function.Supplier;

final class QuicheQuicConnection {
    private final ReferenceCounted refCnt;
    private final QuicheQuicSslEngine engine;
    private long connection;
    private final ByteBuf recvInfo;
    private final ByteBuf sendInfo;

    QuicheQuicConnection(long connection, QuicheQuicSslEngine engine, ReferenceCounted refCnt) {
        this.connection = connection;
        this.engine = engine;
        this.refCnt = refCnt;
        // TODO: Maybe cache these per thread as we only use them temporary within a limited scope.
        recvInfo = Quiche.allocateNativeOrder(Quiche.SIZEOF_QUICHE_RECV_INFO + Quiche.SIZEOF_SOCKADDR_STORAGE);
        sendInfo = Quiche.allocateNativeOrder(Quiche.SIZEOF_QUICHE_SEND_INFO);
    }

    void free() {
        boolean release = false;
        synchronized (this) {
            if (connection != -1) {
                try {
                    Quiche.quiche_conn_free(connection);
                    release = true;
                } finally {
                    connection = -1;
                }
            }
        }
        if (release) {
            refCnt.release();
            recvInfo.release();
            sendInfo.release();
        }
    }

    QuicConnectionAddress sourceId() {
        return connectionId(() -> Quiche.quiche_conn_source_id(connection));
    }

    QuicConnectionAddress destinationId() {
        return connectionId(() -> Quiche.quiche_conn_destination_id(connection));
    }

    QuicConnectionAddress connectionId(Supplier<byte[]> idSupplier) {
        final byte[] id;
        synchronized (this) {
            if (connection == -1) {
                return null;
            }
            id = idSupplier.get();
        }
        return id == null ? null : new QuicConnectionAddress(id);
    }

    QuicheQuicSslEngine engine() {
        return engine;
    }

    long address() {
        assert connection != -1;
        return connection;
    }

    long recvInfoAddress() {
        return recvInfo.memoryAddress();
    }

    long sendInfoAddress() {
        return sendInfo.memoryAddress();
    }

    boolean isClosed() {
        assert connection != -1;
        return Quiche.quiche_conn_is_closed(connection);
    }

    // Let's override finalize() as we want to ensure we never leak memory even if the user will miss to close
    // Channel that uses this connection and just let it get GC'ed
    @Override
    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }
}
