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

/**
 * Exception produced while processing {@code QUIC}.
 */
public final class QuicException extends Exception {

    private final QuicTransportError error;

    public QuicException(QuicTransportError error) {
        super(error.name());
        this.error = error;
    }

    public QuicException(String message, QuicTransportError error) {
        super(message);
        this.error = error;
    }

    public QuicException(Throwable cause, QuicTransportError error) {
        super(cause);
        this.error = error;
    }

    public QuicException(String message, Throwable cause, QuicTransportError error) {
        super(message, cause);
        this.error = error;
    }


    /**
     * Returns the {@link QuicTransportError} which was the cause of the {@link QuicException}.
     *
     * @return  the {@link QuicTransportError} that caused this {@link QuicException}.
     */
    public QuicTransportError error() {
        return error;
    }
}
