/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.source.e2e_test;

import com.google.common.collect.AbstractIterator;
import com.google.protobuf.Timestamp;
import io.airbyte.protocol.protos.AirbyteMessage;
import io.airbyte.protocol.protos.AirbyteRecordMessage;
import io.airbyte.protocol.protos.ObjectData;
import io.airbyte.protocol.protos.Value;
import java.time.Instant;
import javax.annotation.CheckForNull;

/**
 * This iterator generates test data to be used in speed benchmarking at airbyte. It is
 * deterministic--if called with the same constructor values twice, it will return the same data.
 * The goal is for it to go fast.
 */
class ProtobufSpeedBenchmarkGeneratorIterator extends AbstractIterator<AirbyteMessage> {

  private static final String FIELD_BASE = "field";
  private static final String VALUE_BASE = "valuevaluevaluevaluevalue";
  private static final String STREAM_NAME = "stream1";
  private static final Timestamp EMITTED_AT = Timestamp.newBuilder()
      .setSeconds(Instant.EPOCH.getEpochSecond())
      .setNanos(Instant.EPOCH.getNano())
      .build();

  private final long maxRecords;
  private long numRecordsEmitted;

  public ProtobufSpeedBenchmarkGeneratorIterator(final long maxRecords) {
    this.maxRecords = maxRecords;
    numRecordsEmitted = 0;
  }

  @CheckForNull
  @Override
  protected AirbyteMessage computeNext() {
    if (numRecordsEmitted == maxRecords) {
      return endOfData();
    }

    numRecordsEmitted++;

    final int entryCount = 5;

    ObjectData.Builder data = ObjectData.newBuilder();

    for (int j = 1; j <= entryCount; ++j) {
      // do % 10 so that all records are same length.
      data.putData(FIELD_BASE + j, Value.newBuilder().setStringValue(VALUE_BASE + numRecordsEmitted % 10).build());
    }

    AirbyteRecordMessage.Builder record = AirbyteRecordMessage.newBuilder()
        .setStream(STREAM_NAME)
        .setData(data)
        .setEmittedAt(EMITTED_AT);

    return AirbyteMessage.newBuilder()
        .setRecord(record)
        .build();
  }

  public static void main(String[] args) {
    System.out.println("poop");

    ProtobufSpeedBenchmarkGeneratorIterator it = new ProtobufSpeedBenchmarkGeneratorIterator(10);
    while (it.hasNext()) {
      AirbyteMessage next = it.next();
      System.out.println(next);
    }
  }

}
