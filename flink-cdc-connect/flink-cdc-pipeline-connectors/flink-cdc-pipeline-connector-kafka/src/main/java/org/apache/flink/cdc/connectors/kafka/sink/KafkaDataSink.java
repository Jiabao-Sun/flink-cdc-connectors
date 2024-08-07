/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.kafka.sink;

import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.SchemaChangeEventType;
import org.apache.flink.cdc.common.event.SchemaChangeEventTypeFamily;
import org.apache.flink.cdc.common.sink.DataSink;
import org.apache.flink.cdc.common.sink.EventSinkProvider;
import org.apache.flink.cdc.common.sink.FlinkSinkProvider;
import org.apache.flink.cdc.common.sink.MetadataApplier;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaSinkBuilder;
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner;

import org.apache.kafka.clients.producer.ProducerConfig;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** A {@link DataSink} for "Kafka" connector. */
public class KafkaDataSink implements DataSink {

    final Properties kafkaProperties;

    final DeliveryGuarantee deliveryGuarantee;

    final FlinkKafkaPartitioner<Event> partitioner;

    final ZoneId zoneId;

    final SerializationSchema<Event> valueSerialization;

    final String topic;

    final boolean addTableToHeaderEnabled;

    final String customHeaders;

    public KafkaDataSink(
            DeliveryGuarantee deliveryGuarantee,
            Properties kafkaProperties,
            FlinkKafkaPartitioner<Event> partitioner,
            ZoneId zoneId,
            SerializationSchema<Event> valueSerialization,
            String topic,
            boolean addTableToHeaderEnabled,
            String customHeaders) {
        this.deliveryGuarantee = deliveryGuarantee;
        this.kafkaProperties = kafkaProperties;
        this.partitioner = partitioner;
        this.zoneId = zoneId;
        this.valueSerialization = valueSerialization;
        this.topic = topic;
        this.addTableToHeaderEnabled = addTableToHeaderEnabled;
        this.customHeaders = customHeaders;
    }

    @Override
    public EventSinkProvider getEventSinkProvider() {
        final KafkaSinkBuilder<Event> sinkBuilder = KafkaSink.builder();
        return FlinkSinkProvider.of(
                sinkBuilder
                        .setDeliveryGuarantee(deliveryGuarantee)
                        .setBootstrapServers(
                                kafkaProperties
                                        .get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)
                                        .toString())
                        .setKafkaProducerConfig(kafkaProperties)
                        .setRecordSerializer(
                                new PipelineKafkaRecordSerializationSchema(
                                        partitioner,
                                        valueSerialization,
                                        topic,
                                        addTableToHeaderEnabled,
                                        customHeaders))
                        .build());
    }

    @Override
    public MetadataApplier getMetadataApplier() {
        return new MetadataApplier() {

            private Set<SchemaChangeEventType> enabledEventTypes =
                    Arrays.stream(SchemaChangeEventTypeFamily.ALL).collect(Collectors.toSet());

            @Override
            public MetadataApplier setAcceptedSchemaEvolutionTypes(
                    Set<SchemaChangeEventType> schemaEvolutionTypes) {
                enabledEventTypes = schemaEvolutionTypes;
                return this;
            }

            @Override
            public boolean acceptsSchemaEvolutionType(SchemaChangeEventType schemaChangeEventType) {
                return enabledEventTypes.contains(schemaChangeEventType);
            }

            @Override
            public Set<SchemaChangeEventType> getSupportedSchemaEvolutionTypes() {
                // All schema change events are supported.
                return Arrays.stream(SchemaChangeEventTypeFamily.ALL).collect(Collectors.toSet());
            }

            @Override
            public void applySchemaChange(SchemaChangeEvent schemaChangeEvent) {
                // simply do nothing here because Kafka do not maintain the schemas.
            }
        };
    }
}
