package com.ifengxue.rpc.client;

import com.ifengxue.rpc.protocol.ResponseProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.BlockingQueue;

/**
 * 客户端响应处理器
 *
 * Created by LiuKeFeng on 2017-04-22.
 */
public class ClientResponseHandler extends SimpleChannelInboundHandler<ResponseProtocol> {
    private Logger logger = LoggerFactory.getLogger(getClass());
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ResponseProtocol responseProtocol) throws Exception {
        String sessionID = responseProtocol.getSessionID();
        logger.info("收到服务端对客户端{}的响应", sessionID);
        BlockingQueue<ResponseProtocol> blockingQueue = RpcContext.CACHED_RESPONSE_PROTOCOL_MAP.get(sessionID);
        if (blockingQueue != null) {
            blockingQueue.put(responseProtocol);
        } else {
            if (RpcContext.CACHED_NOT_NEED_RETURN_RESULT_SET.contains(sessionID)) {
                RpcContext.CACHED_NOT_NEED_RETURN_RESULT_SET.remove(sessionID);
                RpcContext.CACHED_RESPONSE_PROTOCOL_MAP.remove(sessionID);
                return;
            }
            logger.warn("客户端[" + sessionID + "]接收到服务端响应，但是客户端已经超时退出。");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        if (cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        logger.error("客户端接收服务端响应出错:" + cause.getMessage(), cause);
    }
}
