package io.micrometer.dynatrace2;

import io.micrometer.core.ipc.http.*;
import io.micrometer.core.ipc.http.HttpSender.*;
import org.assertj.core.api.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import wiremock.com.google.common.collect.*;

import java.nio.charset.*;
import java.util.*;
import java.util.stream.*;

import static io.micrometer.dynatrace2.MetricsApiIngestion.METRICS_INGESTION_URL;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetricsApiIngestionTest implements WithAssertions {

    HttpSender httpSender;
    DynatraceConfig config;
    MetricsApiIngestion metricsApiIngestion;

    @BeforeEach
    void setUp() throws Throwable {
        httpSender = spy(HttpSender.class);
        when(httpSender.send(any())).thenReturn(new HttpSender.Response(202, null));

        config = mock(DynatraceConfig.class);
        when(config.batchSize()).thenReturn(2);
        when(config.uri()).thenReturn("https://micrometer.dynatrace.com");
        when(config.apiToken()).thenReturn("my-token");

        metricsApiIngestion = new MetricsApiIngestion(httpSender, config);
    }

    @Test
    void shouldNotSendRequests_whenNoMetricLines() {
        List<String> metricLines = emptyList();

        metricsApiIngestion.sendInBatches(metricLines);

        verifyNoInteractions(httpSender);
    }

    @Test
    void shouldSendOneRequest_whenLessOrEqualToBatchSize() {
        List<String> metricLines = asList("first", "second");

        metricsApiIngestion.sendInBatches(metricLines);

        verify(httpSender).post(any());
    }

    @Test
    void shouldSendMultipleRequests_whenGreaterThanBatchSize() {
        List<String> metricLines = IntStream.range(0, 20)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        metricsApiIngestion.sendInBatches(metricLines);

        verify(httpSender, times(10)).post(any());
    }

    @Test
    void shouldFulfillApiSpec() throws Throwable {
        List<String> metricLines = asList("first", "second");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);

        metricsApiIngestion.sendInBatches(metricLines);
        verify(httpSender).send(requestCaptor.capture());

        assertThat(requestCaptor.getValue())
                .extracting(
                        Request::getMethod,
                        r -> r.getUrl().toString(),
                        Request::getRequestHeaders,
                        r -> new String(r.getEntity(), StandardCharsets.UTF_8))
                .contains(
                        Method.POST,
                        config.uri() + METRICS_INGESTION_URL,
                        ImmutableMap.of(
                                "Authorization", "Api-Token " + config.apiToken(),
                                "Content-Type", "text/plain"),
                        "first" + System.lineSeparator() + "second");
    }
}
