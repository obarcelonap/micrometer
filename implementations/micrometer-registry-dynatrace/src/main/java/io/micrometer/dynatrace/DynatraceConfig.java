package io.micrometer.dynatrace;

import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

/**
 * Configuration for {@link DynatraceMeterRegistry}
 *
 * @author Oriol Barcelona
 */
public interface DynatraceConfig extends StepRegistryConfig {

    DynatraceConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "dynatrace";
    }

    @Nullable
    default String tenant() {
        return get(prefix() + ".tenant");
    }

    default String apiKey() {
        String v = get(prefix() + ".apiKey");
        if (v == null)
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to Dynatrace");
        return v;
    }

    default String uri() {
        String uri = get(prefix() + ".uri");
        if (uri != null)
            return uri;

        String tenant = tenant();
        if (tenant != null)
            return String.format("https://%s.live.dynatrace.com", tenant);

        throw new MissingRequiredConfigurationException("either the tenant or the uri must be set to report metrics to Dynatrace");
    }

    default String deviceId() {
        String v = get(prefix() + ".deviceId");
        if (v == null)
            throw new MissingRequiredConfigurationException("deviceId must be set to report metrics to Dynatrace");
        return v;
    }
}
