/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TxnStream.proto

package com.aliyun.polardbx.binlog.protocol;

public interface DumpRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.aliyun.polardbx.binlog.protocol.DumpRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string tso = 1;</code>
   * @return The tso.
   */
  java.lang.String getTso();
  /**
   * <code>string tso = 1;</code>
   * @return The bytes for tso.
   */
  com.google.protobuf.ByteString
      getTsoBytes();

  /**
   * <code>string dumperName = 2;</code>
   * @return The dumperName.
   */
  java.lang.String getDumperName();
  /**
   * <code>string dumperName = 2;</code>
   * @return The bytes for dumperName.
   */
  com.google.protobuf.ByteString
      getDumperNameBytes();

  /**
   * <code>int32 streamSeq = 3;</code>
   * @return The streamSeq.
   */
  int getStreamSeq();

  /**
   * <code>int64 version = 4;</code>
   * @return The version.
   */
  long getVersion();
}
