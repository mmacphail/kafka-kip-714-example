package eu.macphail.metrics_producer;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.IntStream;

@RestController
public class SimpleKafkaProducer {

    private Logger log = LoggerFactory.getLogger(SimpleKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.name:default-topic}")
    private String topicName;

    public SimpleKafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/kafka")
    public void sendMessages() {
        IntStream.range(0, 100).forEach(i -> {
            String message = "hello world " + UUID.randomUUID();
            kafkaTemplate.send(topicName, message);
            log.info("Sent: {}", message);
        });
    }
}
