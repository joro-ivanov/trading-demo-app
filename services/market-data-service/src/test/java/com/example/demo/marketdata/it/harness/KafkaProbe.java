package com.example.demo.marketdata.it.harness;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.example.demo.common.json.Json;

/**
 * Kafka plumbing for the integration tests, so tests stay linear. Events are sent and read as records
 * (serialized with {@code Json}), never as hand-written JSON. Reads are group-less (manual partition
 * assignment from the beginning up to the end offsets at call time), so probes never share
 * consumer-group state and a read is a deterministic snapshot of the topic.
 */
public final class KafkaProbe {

    private final String bootstrapServers;

    KafkaProbe(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** Produces {@code event} serialized with {@code Json} and waits until the broker acknowledged it. */
    public void send(String topic, String key, Object event) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, Json.write(event))).get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not produce to " + topic, e);
        }
    }

    /**
     * Waits until the topic holds at least {@code expectedCount} {@code type} records whose
     * {@code field} equals {@code value}, then returns all such records currently on the topic.
     */
    public <T> List<T> awaitRecordsWhere(String topic, Class<T> type, Function<T, String> field, String value,
                                         int expectedCount, Duration atMost) {
        return await().atMost(atMost).pollInterval(Duration.ofMillis(250))
                .until(() -> currentRecordsWhere(topic, type, field, value), records -> records.size() >= expectedCount);
    }

    /** Every record currently on the topic, read as {@code type}, whose {@code field} equals {@code value}. */
    public <T> List<T> currentRecordsWhere(String topic, Class<T> type, Function<T, String> field, String value) {
        List<T> matches = new ArrayList<>();
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
                    T event = Json.read(record.value(), type);
                    if (value.equals(field.apply(event))) {
                        matches.add(event);
                    }
                }
            }
        }
        return matches;
    }
}
