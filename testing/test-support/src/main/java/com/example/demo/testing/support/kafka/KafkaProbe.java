package com.example.demo.testing.support.kafka;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

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
import com.example.demo.testing.RawJsonPayload;

/**
 * Kafka plumbing for the tests, so they stay linear: produce records (serialized with {@code Json}, as the
 * real producers do) and read topics. Every send waits for the broker's acknowledgement, so records keyed
 * alike arrive in the order the test sent them. Reads are group-less (manual assignment of every partition
 * from the beginning up to the end offsets at call time), so probes never share consumer-group state and a
 * read is a deterministic snapshot of the topic.
 */
public final class KafkaProbe {

    private static final Duration POLL = Duration.ofMillis(200);
    private static final Duration AWAIT_POLL = Duration.ofMillis(250);
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(10);

    private final String bootstrapServers;

    public KafkaProbe(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /** Publishes {@code event} serialized with the project's {@link Json}. */
    public void send(String topic, String key, Object event) {
        produce(topic, key, Json.write(event));
    }

    /** Publishes {@code payload} verbatim: only for malformed messages no event record can produce. */
    @RawJsonPayload(reason = "raw sender for malformed Kafka messages")
    public void sendRaw(String topic, String key, String payload) {
        produce(topic, key, payload);
    }

    /** Waits up to {@code atMost} for a record on {@code topic} with {@code key}; returns the first one's value. */
    public String awaitValue(String topic, String key, Duration atMost) {
        return await().atMost(atMost).pollInterval(AWAIT_POLL)
                .until(() -> values(topic, record -> key.equals(record.key())), values -> !values.isEmpty())
                .getFirst();
    }

    /**
     * Watches {@code topic} for the whole {@code window} and returns every value whose key equals
     * {@code keyOrText} or whose payload contains it (to prove nothing was published).
     */
    public List<String> valuesWithin(String topic, String keyOrText, Duration window) {
        await().pollDelay(window).atMost(window.plus(METADATA_TIMEOUT)).until(() -> true);
        return values(topic, record -> keyOrText.equals(record.key()) || record.value().contains(keyOrText));
    }

    /**
     * Waits until the topic holds at least {@code expectedCount} {@code type} records whose
     * {@code field} equals {@code value}, then returns all such records currently on the topic.
     */
    public <T> List<T> awaitRecordsWhere(String topic, Class<T> type, Function<T, String> field, String value,
                                         int expectedCount, Duration atMost) {
        return await().atMost(atMost).pollInterval(AWAIT_POLL)
                .until(() -> currentRecordsWhere(topic, type, field, value), records -> records.size() >= expectedCount);
    }

    /** Every record currently on the topic, read as {@code type}, whose {@code field} equals {@code value}. */
    public <T> List<T> currentRecordsWhere(String topic, Class<T> type, Function<T, String> field, String value) {
        return values(topic, record -> true).stream()
                .map(json -> Json.read(json, type))
                .filter(event -> value.equals(field.apply(event)))
                .toList();
    }

    private void produce(String topic, String key, String payload) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted producing to " + topic, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not produce to " + topic, e);
        }
    }

    /** The values of every record now on {@code topic} that match {@code filter}, in partition order. */
    private List<String> values(String topic, Predicate<ConsumerRecord<String, String>> filter) {
        List<String> matches = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            List<TopicPartition> partitions = consumer.partitionsFor(topic, METADATA_TIMEOUT).stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);
            while (partitions.stream().anyMatch(partition -> consumer.position(partition) < end.get(partition))) {
                for (ConsumerRecord<String, String> record : consumer.poll(POLL)) {
                    if (filter.test(record)) {
                        matches.add(record.value());
                    }
                }
            }
        }
        return matches;
    }
}
