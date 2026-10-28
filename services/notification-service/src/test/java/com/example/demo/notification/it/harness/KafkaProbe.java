package com.example.demo.notification.it.harness;

import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/** Kafka plumbing for the notification ITs: the tests play the producers (market-data, matcher). */
public final class KafkaProbe {

    private KafkaProbe() {
    }

    /** Sends one String record and flushes, so it is on the broker when this returns. */
    public static void produce(String bootstrapServers, String topic, String key, String payload) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, payload));
            producer.flush();
        }
    }
}
