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

import io.netty.util.internal.StringUtil;

public class QuicheQuicConnectionStats implements QuicConnectionStats {

    private final long recv;
    private final long sent;
    private final long lost;
    private final long rttNanos;
    private final long congestionWindow;
    private final long deliveryRate;

    protected QuicheQuicConnectionStats(long recv, long sent, long lost, long rttNanos, long cwnd, long deliveryRate) {
        this.recv = recv;
        this.sent = sent;
        this.lost = lost;
        this.rttNanos = rttNanos;
        this.congestionWindow = cwnd;
        this.deliveryRate = deliveryRate;
    }

    @Override
    public long recv() {
        return this.recv;
    }

    @Override
    public long sent() {
        return this.sent;
    }

    @Override
    public long lost() {
        return this.lost;
    }

    @Override
    public long rttNanos() {
        return this.rttNanos;
    }

    @Override
    public long congestionWindow() {
        return this.congestionWindow;
    }

    @Override
    public long deliveryRate() {
        return this.deliveryRate;
    }

    /**
     * Returns the {@link String} representation of stats.
     */
    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append("[")
            .append("recv=").append(this.recv)
            .append(", sent=").append(this.sent)
            .append(", lost=").append(this.lost)
            .append(", rttNanos=").append(this.rttNanos)
            .append(", congestionWindow=").append(this.congestionWindow)
            .append(", deliveryRate=").append(this.deliveryRate)
            .append("]")
            .toString();
    }

}
