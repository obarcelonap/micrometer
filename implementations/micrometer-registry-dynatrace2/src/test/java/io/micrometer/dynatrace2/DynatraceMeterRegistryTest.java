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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Oriol Barcelona
 */
@ExtendWith(WiremockResolver.class)
class DynatraceMeterRegistryTest implements WithAssertions {

    private static final String API_TOKEN = "DT-API-TOKEN";
    private static final UrlPattern METRICS_INGESTION_URL = urlEqualTo(DynatraceMeterRegistry.METRICS_INGESTION_URL);
    private static final StringValuePattern TEXT_PLAIN_CONTENT_TYPE = equalTo("text/plain");

    WireMockServer dtApiServer;
    Clock clock;
    DynatraceMeterRegistry meterRegistry;

    @BeforeEach
    void setupServerAndConfig(@Wiremock WireMockServer server) {
        this.dtApiServer = server;
        DynatraceConfig config = new DynatraceConfig() {
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
        clock = new MockClock();
        meterRegistry = DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .build();
        dtApiServer.stubFor(post(METRICS_INGESTION_URL)
                .willReturn(aResponse().withStatus(202)));
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

        assertThatThrownBy(() -> DynatraceMeterRegistry.builder(config).build())
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

        assertThatThrownBy(() -> DynatraceMeterRegistry.builder(config).build())
                .isExactlyInstanceOf(ValidationException.class);
    }

    @Test
    void shouldIngestAMetricThroughTheApi() {
        meterRegistry.gauge("cpu.temperature", 55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu.temperature 55"))
        );
    }

    @Test
    void shouldIngestAMetricThroughTheApi_whenHasDimensions() {
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("dt.entity.host", "HOST-06F288EE2A930951", "cpu", "1"),
                55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu.temperature,cpu=1,dt.entity.host=HOST-06F288EE2A930951 55"))
        );
    }

    @Test
    void shouldIngestMultipleMetricsThroughTheApi_whenSameMetricButDifferentDimensions() {
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("dt.entity.host", "HOST-06F288EE2A930951", "cpu", "1"),
                55);
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("dt.entity.host", "HOST-06F288EE2A930951", "cpu", "2"),
                50);


        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines(
                        "cpu.temperature,cpu=2,dt.entity.host=HOST-06F288EE2A930951 50",
                        "cpu.temperature,cpu=1,dt.entity.host=HOST-06F288EE2A930951 55"))
        );
    }

    private StringValuePattern equalToMetricLines(String... lines) {
        return equalToMetricLines(clock.wallTime(), lines);
    }

    private StringValuePattern equalToMetricLines(long time, String... lines) {
        return equalTo(
                Stream.of(lines)
                        .map(line -> line + " " + time)
                        .collect(Collectors.joining(System.lineSeparator())));
    }
}
