/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;



import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.ResetSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.RplCommandResponse;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusRequest;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusResponse;
import com.aliyun.polardbx.rpc.cdc.StartSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.StopSlaveRequest;
import com.aliyun.polardbx.rpl.common.RplConstants;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/2/19 10:18
 * @since 5.0.0.0
 */
public class RplServiceManagerV0 {

    //////////////////////////////// For GRPC calls ///// ///////////////////////////
    public static void startSlave(StartSlaveRequest request,
                                  StreamObserver<RplCommandResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.startSlave(params);
        if (result.getCode() == RplConstants.SUCCESS_CODE) {
            setRpcRplCommandResponse(responseObserver, 0, "");
        } else {
            setRpcRplCommandResponse(responseObserver, 1, result.getMsg());
        }
    }

    public static void stopSlave(StopSlaveRequest request,
                                 StreamObserver<RplCommandResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.stopSlave(params);
        if (result.getCode() == RplConstants.SUCCESS_CODE) {
            setRpcRplCommandResponse(responseObserver, 0, "");
        } else {
            setRpcRplCommandResponse(responseObserver, 1, result.getMsg());
        }
    }

    public static void resetSlave(ResetSlaveRequest request,
                                  StreamObserver<RplCommandResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.resetSlave(params);
        if (result.getCode() == RplConstants.SUCCESS_CODE) {
            setRpcRplCommandResponse(responseObserver, 0, "");
        } else {
            setRpcRplCommandResponse(responseObserver, 1, result.getMsg());
        }
    }

    public static void showSlaveStatus(ShowSlaveStatusRequest request,
                                       StreamObserver<ShowSlaveStatusResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.showSlaveStatus(params);
        List<LinkedHashMap<String, String>> responses = JSON.parseObject((String) result.getData(),
            new TypeReference<List<LinkedHashMap<String, String>>>() {
            });
        for (LinkedHashMap<String, String> response : responses) {
            responseObserver.onNext(ShowSlaveStatusResponse.newBuilder().setResponse(JSON.toJSONString(response))
                .build());
        }
        responseObserver.onCompleted();
    }

    public static void changeMaster(ChangeMasterRequest request,
                                    StreamObserver<RplCommandResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.changeMaster(params);
        if (result.getCode() == RplConstants.SUCCESS_CODE) {
            setRpcRplCommandResponse(responseObserver, 0, "");
        } else {
            setRpcRplCommandResponse(responseObserver, 1, result.getMsg());
        }
    }

    public static void changeReplicationFilter(ChangeReplicationFilterRequest request,
                                               StreamObserver<RplCommandResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        ResultCode<?> result = RplServiceManager.changeReplicationFilter(params);
        if (result.getCode() == RplConstants.SUCCESS_CODE) {
            setRpcRplCommandResponse(responseObserver, 0, "");
        } else {
            setRpcRplCommandResponse(responseObserver, 1, result.getMsg());
        }
    }

    private static Map<String, String> parseRequest(String request) {
        return JSON.parseObject(request, new TypeReference<HashMap<String, String>>() {
        });
    }

    private static void setRpcRplCommandResponse(StreamObserver<RplCommandResponse> responseObserver, int resultCode,
                                                 String error) {
        responseObserver.onNext(RplCommandResponse.newBuilder().setResultCode(resultCode).setError(error).build());
        responseObserver.onCompleted();
    }
}
