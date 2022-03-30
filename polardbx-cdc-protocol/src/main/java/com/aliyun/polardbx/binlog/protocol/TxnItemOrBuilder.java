// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TxnStream.proto

package com.aliyun.polardbx.binlog.protocol;

public interface TxnItemOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.aliyun.polardbx.binlog.protocol.TxnItem)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   **
   *1.traceId是分布式事务内，合并多个物理分片的事件时，进行排序的依据&lt;/br&gt;
   *2.为了便于统一处理，不管是单机事务还是分布式事务都要求传入，并且要保证同一事务在同一物理分片的traceID单调递增 &lt;/br&gt;
   *3.如果物理binlog中带有traceId标识，直接使用即可；如果物理binlog中不带有traceId标识，通过"时间戳+自增序列"手动生成 &lt;/br&gt;
   * </pre>
   *
   * <code>string traceId = 1;</code>
   * @return The traceId.
   */
  java.lang.String getTraceId();
  /**
   * <pre>
   **
   *1.traceId是分布式事务内，合并多个物理分片的事件时，进行排序的依据&lt;/br&gt;
   *2.为了便于统一处理，不管是单机事务还是分布式事务都要求传入，并且要保证同一事务在同一物理分片的traceID单调递增 &lt;/br&gt;
   *3.如果物理binlog中带有traceId标识，直接使用即可；如果物理binlog中不带有traceId标识，通过"时间戳+自增序列"手动生成 &lt;/br&gt;
   * </pre>
   *
   * <code>string traceId = 1;</code>
   * @return The bytes for traceId.
   */
  com.google.protobuf.ByteString
      getTraceIdBytes();

  /**
   * <pre>
   **
   *1.对应mysql的EventType
   * </pre>
   *
   * <code>int32 eventType = 2;</code>
   * @return The eventType.
   */
  int getEventType();

  /**
   * <pre>
   **
   *1.二进制数据
   * </pre>
   *
   * <code>bytes payload = 3;</code>
   * @return The payload.
   */
  com.google.protobuf.ByteString getPayload();

  /**
   * <pre>
   **
   *1.保存RowsQuery信息
   * </pre>
   *
   * <code>string rowsQuery = 4;</code>
   * @return The rowsQuery.
   */
  java.lang.String getRowsQuery();
  /**
   * <pre>
   **
   *1.保存RowsQuery信息
   * </pre>
   *
   * <code>string rowsQuery = 4;</code>
   * @return The bytes for rowsQuery.
   */
  com.google.protobuf.ByteString
      getRowsQueryBytes();

  /**
   * <pre>
   **
   *对应的库名
   * </pre>
   *
   * <code>string schema = 5;</code>
   * @return The schema.
   */
  java.lang.String getSchema();
  /**
   * <pre>
   **
   *对应的库名
   * </pre>
   *
   * <code>string schema = 5;</code>
   * @return The bytes for schema.
   */
  com.google.protobuf.ByteString
      getSchemaBytes();

  /**
   * <pre>
   **
   *对应的表名
   * </pre>
   *
   * <code>string table = 6;</code>
   * @return The table.
   */
  java.lang.String getTable();
  /**
   * <pre>
   **
   *对应的表名
   * </pre>
   *
   * <code>string table = 6;</code>
   * @return The bytes for table.
   */
  com.google.protobuf.ByteString
      getTableBytes();
}