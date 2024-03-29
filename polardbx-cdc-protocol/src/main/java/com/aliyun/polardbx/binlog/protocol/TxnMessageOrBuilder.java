/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TxnStream.proto

package com.aliyun.polardbx.binlog.protocol;

public interface TxnMessageOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.aliyun.polardbx.binlog.protocol.TxnMessage)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.MessageType type = 1;</code>
   * @return The enum numeric value on the wire for type.
   */
  int getTypeValue();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.MessageType type = 1;</code>
   * @return The type.
   */
  com.aliyun.polardbx.binlog.protocol.MessageType getType();

  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnBegin txnBegin = 2;</code>
   * @return Whether the txnBegin field is set.
   */
  boolean hasTxnBegin();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnBegin txnBegin = 2;</code>
   * @return The txnBegin.
   */
  com.aliyun.polardbx.binlog.protocol.TxnBegin getTxnBegin();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnBegin txnBegin = 2;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnBeginOrBuilder getTxnBeginOrBuilder();

  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnData txnData = 3;</code>
   * @return Whether the txnData field is set.
   */
  boolean hasTxnData();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnData txnData = 3;</code>
   * @return The txnData.
   */
  com.aliyun.polardbx.binlog.protocol.TxnData getTxnData();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnData txnData = 3;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnDataOrBuilder getTxnDataOrBuilder();

  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnEnd txnEnd = 4;</code>
   * @return Whether the txnEnd field is set.
   */
  boolean hasTxnEnd();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnEnd txnEnd = 4;</code>
   * @return The txnEnd.
   */
  com.aliyun.polardbx.binlog.protocol.TxnEnd getTxnEnd();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnEnd txnEnd = 4;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnEndOrBuilder getTxnEndOrBuilder();

  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnTag txnTag = 5;</code>
   * @return Whether the txnTag field is set.
   */
  boolean hasTxnTag();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnTag txnTag = 5;</code>
   * @return The txnTag.
   */
  com.aliyun.polardbx.binlog.protocol.TxnTag getTxnTag();
  /**
   * <code>.com.aliyun.polardbx.binlog.protocol.TxnTag txnTag = 5;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnTagOrBuilder getTxnTagOrBuilder();
}
