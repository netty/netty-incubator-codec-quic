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

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;

import static io.netty.util.internal.PlatformDependent.BIG_ENDIAN_NATIVE_ORDER;

final class QuicheSendInfo {

    private static final FastThreadLocal<byte[]> IPV4_ARRAYS = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[SockaddrIn.IPV4_ADDRESS_LENGTH];
        }
    };

    private static final FastThreadLocal<byte[]> IPV6_ARRAYS = new FastThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[SockaddrIn.IPV6_ADDRESS_LENGTH];
        }
    };

    private QuicheSendInfo() { }

    static InetSocketAddress read(long memory) {
        long to = memory + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO;
        long len = readLen(memory + Quiche.QUICHE_SEND_INFO_OFFSETOF_TO_LEN);
        if (len == Quiche.SIZEOF_SOCKADDR_IN) {
            return SockaddrIn.readIPv4(to, IPV4_ARRAYS.get());
        }
        assert len == Quiche.SIZEOF_SOCKADDR_IN6;
        return SockaddrIn.readIPv6(to, IPV6_ARRAYS.get(), IPV4_ARRAYS.get());
    }

    private static long readLen(long address) {
        switch (Quiche.SIZEOF_SOCKLEN_T) {
            case 1:
                return PlatformDependent.getByte(address);
            case 2:
                return PlatformDependent.getShort(address);
            case 4:
                return PlatformDependent.getInt(address);
            case 8:
                return PlatformDependent.getLong(address);
            default:
                throw new IllegalStateException();
        }
    }
}
