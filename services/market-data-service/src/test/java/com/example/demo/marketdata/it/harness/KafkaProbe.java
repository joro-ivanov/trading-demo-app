package com.example.demo.marketdata.it.harness;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Kafka plumbing for the integration tests, so tests stay linear. Reads are group-less (manual
 * partition assignment from the beginning up to the end offsets at call time), so probes never
 * share consumer-group state and a read is a deterministic snapshot of the topic.
 */
public final class KafkaProbe {

    private final String bootstrapServers;

    KafkaProbe(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** Produces one record and waits until the broker acknowledged it. */
    public void send(String topic, String key, String value) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not produce to " + topic, e);
        }
    }

    /**
     * Waits until the topic holds at least {@code expectedCount} values whose JSON has the string
     * field {@code "field":"value"}, then returns all such values currently on the topic.
     */
    public List<String> awaitValuesWhere(String topic, String field, String value, int expectedCount,
                                         Duration atMost) {
        return await().atMost(atMost).pollInterval(Duration.ofMillis(250))
                .until(() -> currentValuesWhere(topic, field, value), values -> values.size() >= expectedCount);
    }

    /** Every value currently on the topic whose JSON has the string field {@code "field":"value"}. */
    public List<String> currentValuesWhere(String topic, String field, String value) {
        String marker = "\"" + field + "\":\"" + value + "\"";
        List<String> matches = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, Duration.ofSeconds(10)).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(p -> consumer.position(p) < end.get(p))) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.value().contains(marker)) {
                        matches.add(record.value());
                    }
                }
            }
        }
        return matches;
    }
}
