/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.server;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.ReportTarget.REST_API;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;

import com.google.inject.Inject;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineServiceGrpcErrorHandler implements ServerInterceptor {
  @Inject ExceptionManager exceptionManager;
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
    ServerCall.Listener<ReqT> listener = next.startCall(call, metadata);
    return new SimpleForwardingServerCallListener<ReqT>(listener) {
      @Override
      public void onMessage(ReqT message) {
        try {
          super.onMessage(message);
        } catch (Exception ex) {
          handleException(ex);
        }
      }

      @Override
      public void onHalfClose() {
        try {
          super.onHalfClose();
        } catch (Exception ex) {
          handleException(ex);
        }
      }

      @Override
      public void onReady() {
        try {
          super.onReady();
        } catch (Exception ex) {
          handleException(ex);
        }
      }

      private void handleException(Exception ex) {
        if (ex instanceof StatusRuntimeException) {
          handleStatusRuntimeException((StatusRuntimeException) ex);
        } else {
          ex = exceptionManager.processException(ex);
          handleWingsException((WingsException) ex);
        }
      }

      private void handleWingsException(WingsException ex) {
        ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
        List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(ex, REST_API);
        Status status = EmptyPredicate.isEmpty(responseMessages)
            ? Status.INTERNAL.withDescription(ex.toString()).withCause(ex)
            : Status.INTERNAL.withDescription(responseMessages.get(0).getMessage()).withCause(ex);
        call.close(status, new Metadata());
      }

      private void handleStatusRuntimeException(StatusRuntimeException ex) {
        Metadata metadata = ex.getTrailers();
        if (metadata == null) {
          metadata = new Metadata();
        }
        call.close(ex.getStatus(), metadata);
      }
    };
  }
}
