package com.example.demo.order.it.harness;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.example.demo.common.json.Json;

/**
 * Kafka plumbing for the tests: produce a record, or read a topic from the beginning with a fresh
 * consumer group. All polling loops live here so the tests stay linear.
 */
public final class KafkaProbe {

    private static final Duration POLL = Duration.ofMillis(200);

    private final String bootstrapServers;

    public KafkaProbe(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** Publishes {@code event} serialized with the project's {@link Json}, as the real producers do. */
    public void send(String topic, String key, Object event) {
        try (Producer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, Json.write(event)));
            producer.flush();
        }
    }

    /** Waits up to {@code atMost} for the first record on {@code topic} with {@code key}; returns its value. */
    public String awaitValue(String topic, String key, Duration atMost) {
        List<String> found = new ArrayList<>();
        try (Consumer<String, String> consumer = consumer(topic)) {
            await().atMost(atMost).pollInterval(Duration.ZERO).until(() -> {
                collect(consumer, key, found);
                return !found.isEmpty();
            });
        }
        return found.getFirst();
    }

    /**
     * Reads {@code topic} from the beginning for the whole {@code window} and returns every value whose
     * key equals {@code keyOrText} or whose payload contains it (to prove nothing was published).
     */
    public List<String> valuesWithin(String topic, String keyOrText, Duration window) {
        List<String> found = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        try (Consumer<String, String> consumer = consumer(topic)) {
            while (System.nanoTime() < deadline) {
                collect(consumer, keyOrText, found);
            }
        }
        return found;
    }

    private static void collect(Consumer<String, String> consumer, String keyOrText, List<String> found) {
        for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
            if (keyOrText.equals(record.key()) || record.value().contains(keyOrText)) {
                found.add(record.value());
            }
        }
    }

    private Consumer<String, String> consumer(String topic) {
        Consumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "order-it-probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
