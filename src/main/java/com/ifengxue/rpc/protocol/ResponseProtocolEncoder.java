package com.ifengxue.rpc.protocol;

import com.ifengxue.rpc.protocol.enums.CompressTypeEnum;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

/**
 * 响应协议编码器
 *
 * Created by LiuKeFeng on 2017-04-23.
 */
public class ResponseProtocolEncoder extends MessageToByteEncoder<ResponseContext> {
    private Logger logger = LoggerFactory.getLogger(getClass());
    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseContext responseContext, ByteBuf out) throws Exception {
        logger.info("开始发送客户端:{}的响应", responseContext.getRequestSessionID());
        byte[] buffer = responseContext.getRequestSerializerTypeEnum().getSerializer().serialize(ResponseProtocol.Builder
                .newBuilder(responseContext.getRequestSessionID())
                .setExceptionProtocol(Optional.ofNullable(responseContext.getResponseError()).map(ExceptionProtocol::fromThrowable).orElse(null))
                .setInvokeResult(responseContext.getInvokeResult())
                .build());
        if (responseContext.getRequestCompressTypeEnum() != CompressTypeEnum.UNCOMPRESS) {
            buffer = responseContext.getRequestCompressTypeEnum().getCompress().compress(buffer);
        }

        int totalLength = ProtocolConsts.PROTOCOL_PACKAGE_HEADER_LENGTH + buffer.length;
        out.writeInt(totalLength);
        out.writeByte(responseContext.getRequestVersion());
        out.writeByte(responseContext.getRequestProtocolTypeEnum().getType());
        out.writeByte(responseContext.getRequestCompressTypeEnum().getType());
        out.writeByte(responseContext.getRequestSerializerTypeEnum().getType());
        out.writeInt(0);
        out.writeLong(0L);
        out.writeBytes(buffer);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
        if (cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        logger.error("响应协议编码出错:" + cause.getMessage(), cause);
    }
}
