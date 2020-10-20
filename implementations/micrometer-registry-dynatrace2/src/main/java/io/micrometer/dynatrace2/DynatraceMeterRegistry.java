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
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("dynatrace2-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;
    private final HttpSender httpClient;
    private LineProtocolFormatterFactory lineProtocolFormatterFactory;

    private Set<String> discardedMetrics = new HashSet<>();

    @SuppressWarnings("deprecation")
    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

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

        logger.info("dynatrace v2 metrics to report \n" + metricLines.collect(Collectors.joining(System.lineSeparator())));

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
}

