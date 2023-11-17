/*
 * Copyright 2023 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * A {@link QuicResetTokenGenerator} which creates new reset token by using the connection id by signing the given input
 * using <a href="https://www.ietf.org/archive/id/draft-ietf-quic-transport-29.html#section-10.4.2">HMAC algorithms</a>.
 */
final class HmacSignQuicResetTokenGenerator implements QuicResetTokenGenerator {
    static final QuicResetTokenGenerator INSTANCE = new HmacSignQuicResetTokenGenerator();

    private static final String ALGORITM = "HmacSHA256";
    private static final byte[] randomKey = new byte[Quic.RESET_TOKEN_LEN];

    static {
        new SecureRandom().nextBytes(randomKey);
    }

    private HmacSignQuicResetTokenGenerator() {
    }

    private static Mac newMac() {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(randomKey, ALGORITM);
            Mac mac = Mac.getInstance(ALGORITM);
            mac.init(keySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public ByteBuffer newResetToken(ByteBuffer cid) {
        ObjectUtil.checkNotNull(cid, "buffer");
        ObjectUtil.checkPositive(cid.remaining(), "buffer");

        // TODO: Consider using a ThreadLocal.
        Mac mac = newMac();
        mac.update(cid);

        byte[] signBytes = mac.doFinal();
        if (signBytes.length != Quic.RESET_TOKEN_LEN) {
            signBytes = Arrays.copyOf(signBytes, Quic.RESET_TOKEN_LEN);
        }
        return ByteBuffer.wrap(signBytes);
    }
}
