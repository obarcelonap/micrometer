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

import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Metric line factory which maps from micrometer domain to expected format in line protocol
 *
 * @author Oriol Barcelona
 */
class LineProtocolFormatterFactory {
    private final Clock clock;

    private final Logger logger = LoggerFactory.getLogger(LineProtocolFormatterFactory.class);

    LineProtocolFormatterFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Creates the formatted metric lines for the corresponding meter. A meter will have multiple
     * metric lines considering the measurements within.
     * @param meter to extract the measurements
     * @return a stream of formatted metric lines
     */
    Optional<Stream<String>> toMetricLines(Meter meter) {
        return metricLineFormatter(meter, clock.wallTime())
                .map(formatter -> Streams.of(meter.measure()).map(formatter));
    }

    private Optional<Function<Measurement, String>> metricLineFormatter(Meter meter, long wallTime) {
        if (meter instanceof Gauge) {
            return Optional.of(m -> formatGaugeMetricLine(meter, m, wallTime));
        } else if (meter instanceof Counter) {
            return Optional.of(m -> formatCounterMetricLine(meter, m, wallTime));
        }
        return Optional.empty();
    }

    private String formatGaugeMetricLine(Meter meter, Measurement measurement, long wallTime) {
        return LineProtocolFormatters.formatGaugeMetricLine(
                metricName(meter, measurement),
                meter.getId().getTags(),
                measurement.getValue(),
                wallTime);
    }

    private String formatCounterMetricLine(Meter meter, Measurement measurement, long wallTime) {
        return LineProtocolFormatters.formatCounterMetricLine(
                metricName(meter, measurement),
                meter.getId().getTags(),
                measurement.getValue(),
                wallTime);
    }

    private String metricName(Meter meter, Measurement measurement) {
        String meterName = meter.getId().getName();
        if (measurement.getStatistic() == Statistic.VALUE) {
            return meterName;
        }
        return String.format("%s.%s", meterName, measurement.getStatistic().getTagValueRepresentation());
    }
}
