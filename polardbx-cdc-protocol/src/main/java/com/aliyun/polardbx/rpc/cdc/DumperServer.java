// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: DumperServer.proto

package com.aliyun.polardbx.rpc.cdc;

public final class DumperServer {
  private DumperServer() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_Request_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_Request_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ShowBinlogEventsRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ShowBinlogEventsRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_DumpRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_DumpRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_BinaryLog_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_BinaryLog_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_MasterStatus_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_MasterStatus_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_BinlogEvent_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_BinlogEvent_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_DumpStream_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_DumpStream_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_RplCommandResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_RplCommandResponse_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ChangeMasterRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ChangeMasterRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_StartSlaveRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_StartSlaveRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_StopSlaveRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_StopSlaveRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ResetSlaveRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ResetSlaveRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ChangeReplicationFilterRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ChangeReplicationFilterRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ShowSlaveStatusRequest_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ShowSlaveStatusRequest_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_dumper_ShowSlaveStatusResponse_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_dumper_ShowSlaveStatusResponse_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\022DumperServer.proto\022\006dumper\"\026\n\007Request\022" +
      "\013\n\003req\030\001 \001(\t\"Y\n\027ShowBinlogEventsRequest\022" +
      "\017\n\007logName\030\001 \001(\t\022\013\n\003pos\030\002 \001(\003\022\016\n\006offset\030" +
      "\003 \001(\003\022\020\n\010rowCount\030\004 \001(\003\"1\n\013DumpRequest\022\020" +
      "\n\010fileName\030\001 \001(\t\022\020\n\010position\030\002 \001(\003\".\n\tBi" +
      "naryLog\022\017\n\007logName\030\001 \001(\t\022\020\n\010fileSize\030\002 \001" +
      "(\003\"s\n\014MasterStatus\022\014\n\004file\030\001 \001(\t\022\020\n\010posi" +
      "tion\030\002 \001(\003\022\022\n\nbinlogDoDB\030\003 \001(\t\022\026\n\016binlog" +
      "IgnoreDB\030\004 \001(\t\022\027\n\017executedGtidSet\030\005 \001(\t\"" +
      "q\n\013BinlogEvent\022\017\n\007logName\030\001 \001(\t\022\013\n\003pos\030\002" +
      " \001(\003\022\021\n\teventType\030\003 \001(\t\022\020\n\010serverId\030\004 \001(" +
      "\003\022\021\n\tendLogPos\030\005 \001(\003\022\014\n\004info\030\006 \001(\t\"\035\n\nDu" +
      "mpStream\022\017\n\007payload\030\001 \001(\014\"7\n\022RplCommandR" +
      "esponse\022\022\n\nresultCode\030\001 \001(\005\022\r\n\005error\030\002 \001" +
      "(\t\"&\n\023ChangeMasterRequest\022\017\n\007request\030\001 \001" +
      "(\t\"$\n\021StartSlaveRequest\022\017\n\007request\030\001 \001(\t" +
      "\"#\n\020StopSlaveRequest\022\017\n\007request\030\001 \001(\t\"$\n" +
      "\021ResetSlaveRequest\022\017\n\007request\030\001 \001(\t\"1\n\036C" +
      "hangeReplicationFilterRequest\022\017\n\007request" +
      "\030\001 \001(\t\")\n\026ShowSlaveStatusRequest\022\017\n\007requ" +
      "est\030\001 \001(\t\"+\n\027ShowSlaveStatusResponse\022\020\n\010" +
      "response\030\001 \001(\t2\222\006\n\nCdcService\0228\n\016ShowBin" +
      "aryLogs\022\017.dumper.Request\032\021.dumper.Binary" +
      "Log\"\0000\001\022;\n\020ShowMasterStatus\022\017.dumper.Req" +
      "uest\032\024.dumper.MasterStatus\"\000\022L\n\020ShowBinl" +
      "ogEvents\022\037.dumper.ShowBinlogEventsReques" +
      "t\032\023.dumper.BinlogEvent\"\0000\001\0223\n\004Dump\022\023.dum" +
      "per.DumpRequest\032\022.dumper.DumpStream\"\0000\001\022" +
      "3\n\004Sync\022\023.dumper.DumpRequest\032\022.dumper.Du" +
      "mpStream\"\0000\001\022I\n\014ChangeMaster\022\033.dumper.Ch" +
      "angeMasterRequest\032\032.dumper.RplCommandRes" +
      "ponse\"\000\022_\n\027ChangeReplicationFilter\022&.dum" +
      "per.ChangeReplicationFilterRequest\032\032.dum" +
      "per.RplCommandResponse\"\000\022E\n\nStartSlave\022\031" +
      ".dumper.StartSlaveRequest\032\032.dumper.RplCo" +
      "mmandResponse\"\000\022C\n\tStopSlave\022\030.dumper.St" +
      "opSlaveRequest\032\032.dumper.RplCommandRespon" +
      "se\"\000\022E\n\nResetSlave\022\031.dumper.ResetSlaveRe" +
      "quest\032\032.dumper.RplCommandResponse\"\000\022V\n\017S" +
      "howSlaveStatus\022\036.dumper.ShowSlaveStatusR" +
      "equest\032\037.dumper.ShowSlaveStatusResponse\"" +
      "\0000\001B!\n\033com.aliyun.polardbx.rpc.cdcH\001P\001b\006" +
      "proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_dumper_Request_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_dumper_Request_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_Request_descriptor,
        new java.lang.String[] { "Req", });
    internal_static_dumper_ShowBinlogEventsRequest_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_dumper_ShowBinlogEventsRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ShowBinlogEventsRequest_descriptor,
        new java.lang.String[] { "LogName", "Pos", "Offset", "RowCount", });
    internal_static_dumper_DumpRequest_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_dumper_DumpRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_DumpRequest_descriptor,
        new java.lang.String[] { "FileName", "Position", });
    internal_static_dumper_BinaryLog_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_dumper_BinaryLog_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_BinaryLog_descriptor,
        new java.lang.String[] { "LogName", "FileSize", });
    internal_static_dumper_MasterStatus_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_dumper_MasterStatus_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_MasterStatus_descriptor,
        new java.lang.String[] { "File", "Position", "BinlogDoDB", "BinlogIgnoreDB", "ExecutedGtidSet", });
    internal_static_dumper_BinlogEvent_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_dumper_BinlogEvent_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_BinlogEvent_descriptor,
        new java.lang.String[] { "LogName", "Pos", "EventType", "ServerId", "EndLogPos", "Info", });
    internal_static_dumper_DumpStream_descriptor =
      getDescriptor().getMessageTypes().get(6);
    internal_static_dumper_DumpStream_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_DumpStream_descriptor,
        new java.lang.String[] { "Payload", });
    internal_static_dumper_RplCommandResponse_descriptor =
      getDescriptor().getMessageTypes().get(7);
    internal_static_dumper_RplCommandResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_RplCommandResponse_descriptor,
        new java.lang.String[] { "ResultCode", "Error", });
    internal_static_dumper_ChangeMasterRequest_descriptor =
      getDescriptor().getMessageTypes().get(8);
    internal_static_dumper_ChangeMasterRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ChangeMasterRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_StartSlaveRequest_descriptor =
      getDescriptor().getMessageTypes().get(9);
    internal_static_dumper_StartSlaveRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_StartSlaveRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_StopSlaveRequest_descriptor =
      getDescriptor().getMessageTypes().get(10);
    internal_static_dumper_StopSlaveRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_StopSlaveRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_ResetSlaveRequest_descriptor =
      getDescriptor().getMessageTypes().get(11);
    internal_static_dumper_ResetSlaveRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ResetSlaveRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_ChangeReplicationFilterRequest_descriptor =
      getDescriptor().getMessageTypes().get(12);
    internal_static_dumper_ChangeReplicationFilterRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ChangeReplicationFilterRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_ShowSlaveStatusRequest_descriptor =
      getDescriptor().getMessageTypes().get(13);
    internal_static_dumper_ShowSlaveStatusRequest_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ShowSlaveStatusRequest_descriptor,
        new java.lang.String[] { "Request", });
    internal_static_dumper_ShowSlaveStatusResponse_descriptor =
      getDescriptor().getMessageTypes().get(14);
    internal_static_dumper_ShowSlaveStatusResponse_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_dumper_ShowSlaveStatusResponse_descriptor,
        new java.lang.String[] { "Response", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}