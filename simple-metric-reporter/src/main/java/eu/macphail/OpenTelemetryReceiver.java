package eu.macphail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.telemetry.ClientTelemetryPayload;
import org.apache.kafka.server.telemetry.ClientTelemetryReceiver;
import org.apache.kafka.shaded.com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.AnyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.common.v1.KeyValue;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.Metric;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.MetricsData;
import org.apache.kafka.shaded.io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import org.apache.kafka.shaded.io.opentelemetry.proto.resource.v1.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryReceiver implements ClientTelemetryReceiver {

    private final Logger log = LoggerFactory.getLogger(OpenTelemetryReceiver.class);

    private final String otlpMetricsEndpoint;
    private final HttpClient httpClient;

    public OpenTelemetryReceiver(String otlpMetricsEndpoint, HttpClient httpClient) {
        this.otlpMetricsEndpoint = Objects.requireNonNull(otlpMetricsEndpoint);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public void exportMetrics(AuthorizableRequestContext context, ClientTelemetryPayload payload) {
        ByteBuffer byteBuffer = payload.data();
        byte[] bytes;
        byte[] metricsDataWithKeyValues = null;

        if (byteBuffer.hasArray()) {
            bytes = byteBuffer.array();
        } else {
            bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
        }

        var clientIdAttribute = KeyValue.newBuilder()
                .setKey("clientId")
                .setValue(AnyValue.newBuilder().setStringValue(context.clientId()).build())
                .build();
        var clientAddressAttribute = KeyValue.newBuilder()
                .setKey("clientAddress")
                .setValue(AnyValue.newBuilder().setStringValue(context.clientAddress().toString()).build())
                .build(); 
        var clientInstanceIdAttribute = KeyValue.newBuilder()
                .setKey("clientInstanceId")
                .setValue(AnyValue.newBuilder().setStringValue(payload.clientInstanceId().toString()).build())
                .build(); 
        
        // MetricsData
        try {
            // Deserialize the byte array into a MetricsData object
            MetricsData metricsData = MetricsData.parseFrom(bytes);

            var metricsDataBuilder = metricsData.toBuilder();
            for (int i = 0; i < metricsDataBuilder.getResourceMetricsCount(); i++) {
                var resourceMetricsBuilder = metricsDataBuilder.getResourceMetricsBuilder(i);

                for(int j = 0; j < resourceMetricsBuilder.getScopeMetricsCount(); j++) {
                    var scopeMetricsBuilder = resourceMetricsBuilder.getScopeMetricsBuilder(j);

                    for(int k = 0; k < scopeMetricsBuilder.getMetricsCount(); k++) {
                        var metricsBuilder = scopeMetricsBuilder.getMetricsBuilder(k);

                        var metricsCase = metricsBuilder.getDataCase();
                        if(metricsCase == Metric.DataCase.SUM) {
                            var sumBuilder = metricsBuilder.getSumBuilder();
                            for(int l = 0; l < sumBuilder.getDataPointsCount(); l++) {
                                var dataPoints = sumBuilder.getDataPointsBuilder(l)
                                        .addAttributes(clientIdAttribute)
                                        .addAttributes(clientAddressAttribute)
                                        .addAttributes(clientInstanceIdAttribute)
                                        .build();
                                sumBuilder.setDataPoints(l, dataPoints);
                            }
                            metricsBuilder.setSum(sumBuilder.build());
                        } else if(metricsCase == Metric.DataCase.GAUGE) {
                            var gaugeBuilder = metricsBuilder.getGaugeBuilder();
                            for(int l = 0; l < gaugeBuilder.getDataPointsCount(); l++) {
                                var dataPoints = gaugeBuilder.getDataPointsBuilder(l)
                                        .addAttributes(clientIdAttribute)
                                        .addAttributes(clientAddressAttribute)
                                        .addAttributes(clientInstanceIdAttribute)
                                        .build();
                                gaugeBuilder.setDataPoints(l, dataPoints);
                            }
                            metricsBuilder.setGauge(gaugeBuilder.build());
                        } else if(metricsCase == Metric.DataCase.EXPONENTIAL_HISTOGRAM) {
                            var expoHistogram = metricsBuilder.getExponentialHistogramBuilder();
                            for(int l = 0; l < expoHistogram.getDataPointsCount(); l++) {
                                var dataPoints = expoHistogram.getDataPointsBuilder(l)
                                        .addAttributes(clientIdAttribute)
                                        .addAttributes(clientAddressAttribute)
                                        .addAttributes(clientInstanceIdAttribute)
                                        .build();
                                expoHistogram.setDataPoints(l, dataPoints);
                            }
                            metricsBuilder.setExponentialHistogram(expoHistogram.build());
                        } else if(metricsCase == Metric.DataCase.HISTOGRAM) {
                            var histogram = metricsBuilder.getHistogramBuilder();
                            for(int l = 0; l < histogram.getDataPointsCount(); l++) {
                                var dataPoints = histogram.getDataPointsBuilder(l)
                                        .addAttributes(clientIdAttribute)
                                        .addAttributes(clientAddressAttribute)
                                        .addAttributes(clientInstanceIdAttribute)
                                        .build();
                                histogram.setDataPoints(l, dataPoints);
                            }
                            metricsBuilder.setHistogram(histogram.build());
                        } else if(metricsCase == Metric.DataCase.SUMMARY) {
                            var summary = metricsBuilder.getSummaryBuilder();
                            for(int l = 0; l < summary.getDataPointsCount(); l++) {
                                var dataPoints = summary.getDataPointsBuilder(l)
                                        .addAttributes(clientIdAttribute)
                                        .addAttributes(clientAddressAttribute)
                                        .addAttributes(clientInstanceIdAttribute)
                                        .build();
                                summary.setDataPoints(l, dataPoints);
                            }
                            metricsBuilder.setSummary(summary.build());
                        } else {
                            // do nothing
                        }

                        var metrics = metricsBuilder.build();
                        scopeMetricsBuilder.setMetrics(k, metrics);
                    }

                    var scopeMetrics = scopeMetricsBuilder.build();
                    resourceMetricsBuilder.setScopeMetrics(j, scopeMetrics);
                }

                var resourceMetrics = resourceMetricsBuilder.build();
                metricsDataBuilder.setResourceMetrics(i, resourceMetrics);
            }


            var newMetricsData = metricsDataBuilder.build();

            metricsDataWithKeyValues = newMetricsData.toByteArray();

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse MetricsData from byte array", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(otlpMetricsEndpoint))
                .header("Content-Type", "application/x-protobuf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(metricsDataWithKeyValues))
                .build();

        log.debug("Exporting metrics to OLT endpoint");
        httpClient.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    log.debug("OTLP metrics endpoint response status code: {}", response.statusCode());
                    log.debug("OTLP metrics endpoint response: {}", response.body());
                    return response;
                })
                .thenApply(HttpResponse::body)
                .exceptionally(ex -> {
                    log.error("Error invoking the OTLP metrics endpoint", ex);
                    return null;
                });
    }

}