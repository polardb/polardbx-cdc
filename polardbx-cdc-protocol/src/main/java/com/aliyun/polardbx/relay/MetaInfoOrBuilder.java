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
// source: RelayData.proto

package com.aliyun.polardbx.relay;

public interface MetaInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:com.aliyun.polardbx.relay.MetaInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string fileName = 1;</code>
   * @return The fileName.
   */
  java.lang.String getFileName();
  /**
   * <code>string fileName = 1;</code>
   * @return The bytes for fileName.
   */
  com.google.protobuf.ByteString
      getFileNameBytes();

  /**
   * <code>int64 filePos = 2;</code>
   * @return The filePos.
   */
  long getFilePos();
}
