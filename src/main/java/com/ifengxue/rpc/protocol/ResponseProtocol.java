package com.ifengxue.rpc.protocol;

import java.rmi.RemoteException;
import java.util.Objects;

/**
 * 响应协议
 *
 * Created by LiuKeFeng on 2017-04-20.
 */
public class ResponseProtocol {
    /** 客户端请求时的sessionID */
    private String sessionID;
    /** 服务端抛出的异常 */
    private ExceptionProtocol exceptionProtocol;
    /** 调用结果 */
    private Object invokeResult;
    private ResponseProtocol() {}

    public String getSessionID() {
        return sessionID;
    }

    public ExceptionProtocol getExceptionProtocol() {
        return exceptionProtocol;
    }

    public Object getInvokeResult() {
        return invokeResult;
    }

    @Override
    public String toString() {
        return "ResponseProtocol{" +
                "sessionID='" + sessionID + '\'' +
                ", exceptionProtocol=" + exceptionProtocol +
                ", invokeResult=" + invokeResult +
                '}';
    }

    public static class  Builder {
        private ResponseProtocol responseProtocol;
        private Builder() {
            responseProtocol = new ResponseProtocol();
        }
        public static Builder newBuilder(String sessionID) {
            Objects.requireNonNull(sessionID);
            Builder builder = new Builder();
            builder.responseProtocol.sessionID = sessionID;
            return builder;
        }

        public Builder setExceptionProtocol(ExceptionProtocol exceptionProtocol) {
            responseProtocol.exceptionProtocol = exceptionProtocol;
            return this;
        }

        public Builder setInvokeResult(Object invokeResult) {
            responseProtocol.invokeResult = invokeResult;
            return this;
        }

        public ResponseProtocol build() {
            return responseProtocol;
        }
    }
}
