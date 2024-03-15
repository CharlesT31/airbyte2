/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.redshift.typing_deduping;

import static io.airbyte.integrations.destination.redshift.typing_deduping.RedshiftSuperLimitationTransformer.DEFAULT_VARCHAR_SIZE_LIMIT_PREDICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.resources.MoreResources;
import io.airbyte.integrations.base.destination.typing_deduping.AirbyteProtocolType;
import io.airbyte.integrations.base.destination.typing_deduping.AirbyteType;
import io.airbyte.integrations.base.destination.typing_deduping.Array;
import io.airbyte.integrations.base.destination.typing_deduping.ColumnId;
import io.airbyte.integrations.base.destination.typing_deduping.ParsedCatalog;
import io.airbyte.integrations.base.destination.typing_deduping.StreamConfig;
import io.airbyte.integrations.base.destination.typing_deduping.StreamId;
import io.airbyte.integrations.base.destination.typing_deduping.Struct;
import io.airbyte.integrations.destination.redshift.RedshiftSQLNameTransformer;
import io.airbyte.integrations.destination.redshift.typing_deduping.RedshiftSuperLimitationTransformer.TransformationInfo;
import io.airbyte.protocol.models.v0.DestinationSyncMode;
import io.airbyte.protocol.models.v0.SyncMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RedshiftSuperLimitationTransformerTest {

  private RedshiftSuperLimitationTransformer transformer;
  private static final RedshiftSqlGenerator redshiftSqlGenerator = new RedshiftSqlGenerator(new RedshiftSQLNameTransformer());

  @BeforeEach
  public void setup() {
    StreamId streamId = new StreamId("test_schema", "users_final", "test_schema", "users_raw", "test_schema", "users_final");
    final ColumnId id1 = redshiftSqlGenerator.buildColumnId("id1");
    final ColumnId id2 = redshiftSqlGenerator.buildColumnId("id2");
    final List<ColumnId> primaryKey = List.of(id1, id2);
    final ColumnId cursor = redshiftSqlGenerator.buildColumnId("updated_at");

    final LinkedHashMap<ColumnId, AirbyteType> columns = new LinkedHashMap<>();
    columns.put(id1, AirbyteProtocolType.INTEGER);
    columns.put(id2, AirbyteProtocolType.INTEGER);
    columns.put(cursor, AirbyteProtocolType.TIMESTAMP_WITH_TIMEZONE);
    columns.put(redshiftSqlGenerator.buildColumnId("struct"), new Struct(new LinkedHashMap<>()));
    columns.put(redshiftSqlGenerator.buildColumnId("array"), new Array(AirbyteProtocolType.UNKNOWN));
    columns.put(redshiftSqlGenerator.buildColumnId("string"), AirbyteProtocolType.STRING);
    columns.put(redshiftSqlGenerator.buildColumnId("number"), AirbyteProtocolType.NUMBER);
    columns.put(redshiftSqlGenerator.buildColumnId("integer"), AirbyteProtocolType.INTEGER);
    columns.put(redshiftSqlGenerator.buildColumnId("boolean"), AirbyteProtocolType.BOOLEAN);
    columns.put(redshiftSqlGenerator.buildColumnId("timestamp_with_timezone"), AirbyteProtocolType.TIMESTAMP_WITH_TIMEZONE);
    columns.put(redshiftSqlGenerator.buildColumnId("timestamp_without_timezone"), AirbyteProtocolType.TIMESTAMP_WITHOUT_TIMEZONE);
    columns.put(redshiftSqlGenerator.buildColumnId("time_with_timezone"), AirbyteProtocolType.TIME_WITH_TIMEZONE);
    columns.put(redshiftSqlGenerator.buildColumnId("time_without_timezone"), AirbyteProtocolType.TIME_WITHOUT_TIMEZONE);
    columns.put(redshiftSqlGenerator.buildColumnId("date"), AirbyteProtocolType.DATE);
    columns.put(redshiftSqlGenerator.buildColumnId("unknown"), AirbyteProtocolType.UNKNOWN);
    columns.put(redshiftSqlGenerator.buildColumnId("_ab_cdc_deleted_at"), AirbyteProtocolType.TIMESTAMP_WITH_TIMEZONE);
    StreamConfig streamConfig = new StreamConfig(
        streamId,
        SyncMode.INCREMENTAL,
        DestinationSyncMode.APPEND_DEDUP,
        primaryKey,
        Optional.of(cursor),
        columns);
    ParsedCatalog parsedCatalog = new ParsedCatalog(List.of(streamConfig));

    transformer = new RedshiftSuperLimitationTransformer(parsedCatalog);
  }

  @Test
  public void testVarcharNulling() throws IOException {
    final String jsonString = MoreResources.readResource("test.json");
    final JsonNode jsonNode = Jsons.deserializeExact(jsonString);
    // Calculate the size of the json before transformation, note that the original JsonNode is altered
    // so
    // serializing after transformation will return modified size.
    final int jacksonDeserializationSize = Jsons.serialize(jsonNode).getBytes(StandardCharsets.UTF_8).length;
    // Add a short length as predicate.
    final TransformationInfo transformationInfo =
        transformer.transformNodes(jsonNode, text -> text.length() > 10);
    // Calculate the size of the json after transformation
    final int jacksonDeserializeSizeAfterTransform = Jsons.serialize(jsonNode).getBytes(StandardCharsets.UTF_8).length;
    assertEquals(jacksonDeserializationSize, transformationInfo.originalBytes());
    assertEquals(jacksonDeserializeSizeAfterTransform, transformationInfo.originalBytes() - transformationInfo.removedBytes());
    System.out.println(transformationInfo.meta());
  }

  @Test
  public void testRedshiftVarcharLimitNulling() throws IOException {
    final String jsonString = MoreResources.readResource("test.json");
    final JsonNode jsonNode = Jsons.deserializeExact(jsonString);
    final TransformationInfo transformationInfo =
        transformer.transformNodes(jsonNode, DEFAULT_VARCHAR_SIZE_LIMIT_PREDICATE);
  }

}
