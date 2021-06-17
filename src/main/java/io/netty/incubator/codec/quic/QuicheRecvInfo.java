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

import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

final class QuicheRecvInfo {

    private QuicheRecvInfo() { }

    /**
     * Write the {@link InetSocketAddress} into the {@code quiche_recv_info} struct.
     *
     * <pre>
     * typedef struct {
     *     struct sockaddr *from;
     *     socklen_t from_len;
     * } quiche_recv_info;
     * </pre>
     *
     * @param memory the memory address of {@code quiche_recv_info}.
     * @param address the {@link InetSocketAddress} to write into {@code quiche_recv_info}.
     */
    static void setRecvInfo(ByteBuffer memory, InetSocketAddress address) {
        int position = memory.position();
        try {
            int sockaddrPosition = position + Quiche.SIZEOF_QUICHE_RECV_INFO;
            memory.position(position + sockaddrPosition);

            long sockaddrMemoryAddress = Quiche.memoryAddress(memory) + position + sockaddrPosition;
            int len = SockaddrIn.setAddress(memory, address);
            if (Quiche.SIZEOF_SIZE_T == 4) {
                memory.putInt(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM, (int) sockaddrMemoryAddress);
            } else {
                memory.putLong(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM, sockaddrMemoryAddress);
            }
            switch (Quiche.SIZEOF_SOCKLEN_T) {
                case 1:
                    memory.put(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM_LEN, (byte) len);
                    break;
                case 2:
                    memory.putShort(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM_LEN, (short) len);
                    break;
                case 4:
                    memory.putInt(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM_LEN, len);
                    break;
                case 8:
                    memory.putLong(position + Quiche.QUICHE_RECV_INFO_OFFSETOF_FROM_LEN, len);
                    break;
                default:
                    throw new IllegalStateException();
            }
        } finally {
            memory.position(position);
        }
    }

    /**
     * Return the memory address of the {@code sockaddr} that is contained in {@code quiche_recv_info}.
     * @param memory the memory address of {@code quiche_recv_info}.
     * @return the memory address of the {@code sockaddr}.
     */
    static int sockAddressIdx(int position) {
        return position + Quiche.SIZEOF_QUICHE_RECV_INFO;
    }

    static boolean isSameAddress(ByteBuffer memory, ByteBuffer memory2) {
        long address1 = Quiche.memoryAddress(memory) + sockAddressIdx(memory.position());
        long address2 = Quiche.memoryAddress(memory2) + sockAddressIdx(memory2.position());
        return SockaddrIn.cmp(address1, address2) == 0;
    }
}
