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

import java.util.Objects;

public final class QLogConfiguration {

    private final String path;
    private final String logTitle;
    private final String logDescription;

    public QLogConfiguration(String path, String logTitle, String logDescription) {
        this.path = Objects.requireNonNull(path, "path");
        this.logTitle = Objects.requireNonNull(logTitle, "logTitle");
        this.logDescription = Objects.requireNonNull(logDescription, "logDescription");
    }

    public String path() {
        return path;
    }

    public String logTitle() {
        return logTitle;
    }

    public String logDescription() {
        return logDescription;
    }
}
