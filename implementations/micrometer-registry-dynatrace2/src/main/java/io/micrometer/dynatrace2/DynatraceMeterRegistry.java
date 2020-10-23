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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link StepMeterRegistry} for Dynatrace metric API v2
 * https://dev-wiki.dynatrace.org/display/MET/MINT+Specification#MINTSpecification-IngestFormat
 *
 * @see <a href="https://www.dynatrace.com/support/help/dynatrace-api/environment-api/metric-v2/post-ingest-metrics/">Dynatrace metric ingestion v2</a>
 * @author Oriol Barcelona
 * @since ?
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {
    public static final String METRICS_INGESTION_URL = "/api/v2/metrics/ingest";

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace2-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;
    private final HttpSender httpClient;
    private final LineProtocolFormatterFactory lineProtocolFormatterFactory;

    private final Set<String> discardedMetrics = new HashSet<>();

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        this.config = config;
        this.httpClient = httpClient;

        config().namingConvention(new LineProtocolNamingConvention());
        start(threadFactory);
        lineProtocolFormatterFactory = new LineProtocolFormatterFactory(clock);
    }

    @Override
    protected void publish() {
        logger.info("publish DT2");
        Stream<String> metricLines = toMetricLines(getMeters());

        String body = metricLines.collect(Collectors.joining(System.lineSeparator()));
        logger.info("dynatrace v2 metrics to report \n" + body);
        try {
           httpClient.post(config.uri() + METRICS_INGESTION_URL)
                    .withHeader("Authorization", "Api-Token " + config.apiToken())
                    .withPlainText(body)
                    .send()
                   // TODO: improve response handling
           .onSuccess((r) -> logger.debug("Ingested {} metric lines into dynatrace", 0))
           .onError((r) -> logger.error("Failed metric ingestion. code={} body={}", r.code(), r.body()));
        } catch (Throwable throwable) {
            logger.error("Failed metric ingestion", throwable);
        }
    }

    private Stream<String> toMetricLines(List<Meter> meters) {
        return meters.stream()
                .filter(this::isNotDiscarded)
                .flatMap(meter -> lineProtocolFormatterFactory.toMetricLines(meter)
                        .orElseGet(discardMeter(meter.getId().getName())));
    }

    private boolean isNotDiscarded(Meter meter) {
        return !discardedMetrics.contains(meter.getId().getName());
    }

    private Supplier<Stream<String>> discardMeter(String meterName) {
        return () -> {
            discardedMetrics.add(meterName);
            logger.warn("Meter '{}' has been discarded because is not supported in Dynatrace metric API v2", meterName);
            return Stream.empty();
        };
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(DynatraceConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final DynatraceConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DynatraceConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public DynatraceMeterRegistry build() {
            return new DynatraceMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}

