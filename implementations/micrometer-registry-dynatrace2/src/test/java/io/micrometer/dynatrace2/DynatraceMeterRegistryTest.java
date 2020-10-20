/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.validate.ValidationException;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.dynatrace2.DynatraceMeterRegistry;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Oriol Barcelona
 */
@ExtendWith(WiremockResolver.class)
class DynatraceMeterRegistryTest implements WithAssertions {

    public static final String API_TOKEN = "DT-API-TOKEN";
    DynatraceConfig config;
    WireMockServer server;

    @BeforeEach
    void setupServerAndConfig(@Wiremock WireMockServer server) {
        this.server = server;
        this.config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return API_TOKEN;
            }

            @Override
            public String uri() {
                return server.baseUrl();
            }
        };
    }

    @Test
    void shouldThrowValidationException_whenUriIsMissingInConfig() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }
        };

        assertThatThrownBy(() -> new DynatraceMeterRegistry(config, Clock.SYSTEM))
                .isExactlyInstanceOf(ValidationException.class);
    }

    @Test
    void shouldThrowValidationException_whenApiTokenIsMissingInConfig() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "uri";
            }
        };

        assertThatThrownBy(() -> new DynatraceMeterRegistry(config, Clock.SYSTEM))
                .isExactlyInstanceOf(ValidationException.class);
    }
}
