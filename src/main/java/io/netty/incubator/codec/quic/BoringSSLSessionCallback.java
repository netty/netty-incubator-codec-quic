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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

final class BoringSSLSessionCallback {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BoringSSLSessionCallback.class);
    private final QuicSessionHandler sessionHandler;

    BoringSSLSessionCallback(QuicSessionHandler sessionHandler) {
        this.sessionHandler = sessionHandler;
    }

    @SuppressWarnings("unused")
    void newSession(long ssl, byte[] session) {
        byte[] peerParams = BoringSSL.SSL_get_peer_quic_transport_params(ssl);
        logger.debug("ssl: {}, session: {}, peerParams: {}", ssl, Arrays.toString(session),
                Arrays.toString(peerParams));
        if (sessionHandler != null) {
            byte[] quicSession = toQuicSession(session, peerParams);
            if (quicSession != null) {
                try {
                    sessionHandler.handleSession(quicSession);
                } catch (Exception e) {
                    logger.error("handler session error, ssl: {}, session: {}", ssl, Arrays.toString(quicSession), e);
                }
            }
        }
    }

    private static byte[] toQuicSession(byte[] sslSession, byte[] peerParams) {
        if (sslSession != null && peerParams != null) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 DataOutputStream dos = new DataOutputStream(bos)) {
                dos.writeLong(sslSession.length);
                dos.write(sslSession);
                dos.writeLong(peerParams.length);
                dos.write(peerParams);
                return bos.toByteArray();
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
