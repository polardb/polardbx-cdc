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
package com.aliyun.polardbx.binlog.protocol;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.30.0)",
    comments = "Source: TxnStream.proto")
public final class TxnServiceGrpc {

  private TxnServiceGrpc() {}

  public static final String SERVICE_NAME = "com.aliyun.polardbx.binlog.protocol.TxnService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.aliyun.polardbx.binlog.protocol.DumpRequest,
      com.aliyun.polardbx.binlog.protocol.DumpReply> getDumpMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "dump",
      requestType = com.aliyun.polardbx.binlog.protocol.DumpRequest.class,
      responseType = com.aliyun.polardbx.binlog.protocol.DumpReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.aliyun.polardbx.binlog.protocol.DumpRequest,
      com.aliyun.polardbx.binlog.protocol.DumpReply> getDumpMethod() {
    io.grpc.MethodDescriptor<com.aliyun.polardbx.binlog.protocol.DumpRequest, com.aliyun.polardbx.binlog.protocol.DumpReply> getDumpMethod;
    if ((getDumpMethod = TxnServiceGrpc.getDumpMethod) == null) {
      synchronized (TxnServiceGrpc.class) {
        if ((getDumpMethod = TxnServiceGrpc.getDumpMethod) == null) {
          TxnServiceGrpc.getDumpMethod = getDumpMethod =
              io.grpc.MethodDescriptor.<com.aliyun.polardbx.binlog.protocol.DumpRequest, com.aliyun.polardbx.binlog.protocol.DumpReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "dump"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.aliyun.polardbx.binlog.protocol.DumpRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.aliyun.polardbx.binlog.protocol.DumpReply.getDefaultInstance()))
              .setSchemaDescriptor(new TxnServiceMethodDescriptorSupplier("dump"))
              .build();
        }
      }
    }
    return getDumpMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TxnServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TxnServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TxnServiceStub>() {
        @java.lang.Override
        public TxnServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TxnServiceStub(channel, callOptions);
        }
      };
    return TxnServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TxnServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TxnServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TxnServiceBlockingStub>() {
        @java.lang.Override
        public TxnServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TxnServiceBlockingStub(channel, callOptions);
        }
      };
    return TxnServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TxnServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TxnServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TxnServiceFutureStub>() {
        @java.lang.Override
        public TxnServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TxnServiceFutureStub(channel, callOptions);
        }
      };
    return TxnServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class TxnServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public void dump(com.aliyun.polardbx.binlog.protocol.DumpRequest request,
        io.grpc.stub.StreamObserver<com.aliyun.polardbx.binlog.protocol.DumpReply> responseObserver) {
      asyncUnimplementedUnaryCall(getDumpMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getDumpMethod(),
            asyncServerStreamingCall(
              new MethodHandlers<
                com.aliyun.polardbx.binlog.protocol.DumpRequest,
                com.aliyun.polardbx.binlog.protocol.DumpReply>(
                  this, METHODID_DUMP)))
          .build();
    }
  }

  /**
   */
  public static final class TxnServiceStub extends io.grpc.stub.AbstractAsyncStub<TxnServiceStub> {
    private TxnServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TxnServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TxnServiceStub(channel, callOptions);
    }

    /**
     */
    public void dump(com.aliyun.polardbx.binlog.protocol.DumpRequest request,
        io.grpc.stub.StreamObserver<com.aliyun.polardbx.binlog.protocol.DumpReply> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(getDumpMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class TxnServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<TxnServiceBlockingStub> {
    private TxnServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TxnServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TxnServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public java.util.Iterator<com.aliyun.polardbx.binlog.protocol.DumpReply> dump(
        com.aliyun.polardbx.binlog.protocol.DumpRequest request) {
      return blockingServerStreamingCall(
          getChannel(), getDumpMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class TxnServiceFutureStub extends io.grpc.stub.AbstractFutureStub<TxnServiceFutureStub> {
    private TxnServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TxnServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TxnServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_DUMP = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final TxnServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(TxnServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_DUMP:
          serviceImpl.dump((com.aliyun.polardbx.binlog.protocol.DumpRequest) request,
              (io.grpc.stub.StreamObserver<com.aliyun.polardbx.binlog.protocol.DumpReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class TxnServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TxnServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.aliyun.polardbx.binlog.protocol.TxnStream.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TxnService");
    }
  }

  private static final class TxnServiceFileDescriptorSupplier
      extends TxnServiceBaseDescriptorSupplier {
    TxnServiceFileDescriptorSupplier() {}
  }

  private static final class TxnServiceMethodDescriptorSupplier
      extends TxnServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    TxnServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (TxnServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TxnServiceFileDescriptorSupplier())
              .addMethod(getDumpMethod())
              .build();
        }
      }
    }
    return result;
  }
}
