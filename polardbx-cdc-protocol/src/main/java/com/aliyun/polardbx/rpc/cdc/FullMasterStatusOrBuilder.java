/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: DumperServer.proto

package com.aliyun.polardbx.rpc.cdc;

public interface FullMasterStatusOrBuilder extends
    // @@protoc_insertion_point(interface_extends:dumper.FullMasterStatus)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string file = 1;</code>
   * @return The file.
   */
  java.lang.String getFile();
  /**
   * <code>string file = 1;</code>
   * @return The bytes for file.
   */
  com.google.protobuf.ByteString
      getFileBytes();

  /**
   * <code>int64 position = 2;</code>
   * @return The position.
   */
  long getPosition();

  /**
   * <code>string lastTso = 3;</code>
   * @return The lastTso.
   */
  java.lang.String getLastTso();
  /**
   * <code>string lastTso = 3;</code>
   * @return The bytes for lastTso.
   */
  com.google.protobuf.ByteString
      getLastTsoBytes();

  /**
   * <code>int64 delayTime = 4;</code>
   * @return The delayTime.
   */
  long getDelayTime();

  /**
   * <code>int64 avgRevEps = 5;</code>
   * @return The avgRevEps.
   */
  long getAvgRevEps();

  /**
   * <code>int64 avgRevBps = 6;</code>
   * @return The avgRevBps.
   */
  long getAvgRevBps();

  /**
   * <code>int64 avgWriteEps = 7;</code>
   * @return The avgWriteEps.
   */
  long getAvgWriteEps();

  /**
   * <code>int64 avgWriteBps = 8;</code>
   * @return The avgWriteBps.
   */
  long getAvgWriteBps();

  /**
   * <code>int64 avgWriteTps = 9;</code>
   * @return The avgWriteTps.
   */
  long getAvgWriteTps();

  /**
   * <code>int64 avgUploadBps = 10;</code>
   * @return The avgUploadBps.
   */
  long getAvgUploadBps();

  /**
   * <code>int64 avgDumpBps = 11;</code>
   * @return The avgDumpBps.
   */
  long getAvgDumpBps();

  /**
   * <code>string extInfo = 12;</code>
   * @return The extInfo.
   */
  java.lang.String getExtInfo();
  /**
   * <code>string extInfo = 12;</code>
   * @return The bytes for extInfo.
   */
  com.google.protobuf.ByteString
      getExtInfoBytes();
}
