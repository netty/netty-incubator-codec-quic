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

import java.util.Map;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

/**
 * A QUIC {@link ChannelConfig}.
 */
public class DefaultQuicChannelConfig extends DefaultChannelConfig implements QuicChannelConfig {

    private String peerCertServerName;

    public DefaultQuicChannelConfig(Channel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), QuicChannelConfig.QUIC_PEER_CERT_SERVER_NAME);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == QuicChannelConfig.QUIC_PEER_CERT_SERVER_NAME) {
            return (T) String.valueOf(getPeerCertServerName());
        }

        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == QuicChannelConfig.QUIC_PEER_CERT_SERVER_NAME) {
            setPeerCertServerName((String) value);
        } else {
            return super.setOption(option, value);
        }

        return true;
    }

    public String getPeerCertServerName() {
        return peerCertServerName;
    }

    public DefaultQuicChannelConfig setPeerCertServerName(String peerCertServerName) {
        this.peerCertServerName = peerCertServerName;
        return this;
    }
}
