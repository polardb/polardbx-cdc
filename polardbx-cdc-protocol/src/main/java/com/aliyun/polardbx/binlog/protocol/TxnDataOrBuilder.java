// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: TxnStream.proto

package com.aliyun.polardbx.binlog.protocol;

public interface TxnDataOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.aliyun.polardbx.binlog.protocol.TxnData)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>repeated .com.aliyun.polardbx.binlog.protocol.TxnItem txnItems = 1;</code>
   */
  java.util.List<com.aliyun.polardbx.binlog.protocol.TxnItem> 
      getTxnItemsList();
  /**
   * <code>repeated .com.aliyun.polardbx.binlog.protocol.TxnItem txnItems = 1;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnItem getTxnItems(int index);
  /**
   * <code>repeated .com.aliyun.polardbx.binlog.protocol.TxnItem txnItems = 1;</code>
   */
  int getTxnItemsCount();
  /**
   * <code>repeated .com.aliyun.polardbx.binlog.protocol.TxnItem txnItems = 1;</code>
   */
  java.util.List<? extends com.aliyun.polardbx.binlog.protocol.TxnItemOrBuilder> 
      getTxnItemsOrBuilderList();
  /**
   * <code>repeated .com.aliyun.polardbx.binlog.protocol.TxnItem txnItems = 1;</code>
   */
  com.aliyun.polardbx.binlog.protocol.TxnItemOrBuilder getTxnItemsOrBuilder(
      int index);
}