package com.ifengxue.rpc.protocol;

import com.ifengxue.rpc.client.factory.ClientConfigFactory;
import com.ifengxue.rpc.protocol.enums.CompressTypeEnum;
import com.ifengxue.rpc.protocol.enums.SerializerTypeEnum;
import com.ifengxue.rpc.protocol.serialize.ISerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

/**
 * 请求协议编码器
 *
 * Created by LiuKeFeng on 2017-04-21.
 */
public class RequestProtocolEncoder extends MessageToByteEncoder<RequestProtocol> {
    private SerializerTypeEnum serializerTypeEnum = ClientConfigFactory.getInstance().getSerializerTypeEnum();
    private ISerializer serializer = serializerTypeEnum.getSerializer();
    private CompressTypeEnum compressTypeEnum = ClientConfigFactory.getInstance().getCompressTypeEnum();
    private long compressRequestIfLengthGreaterTo = ClientConfigFactory.getInstance().minCompressFrameLength();
    private Logger logger = LoggerFactory.getLogger(getClass());
    @Override
    protected void encode(ChannelHandlerContext ctx, RequestProtocol msg, ByteBuf out) throws Exception {
        byte[] buffer = serializer.serialize(msg);
        boolean isCompress = false;
        if (buffer.length > compressRequestIfLengthGreaterTo && compressTypeEnum != CompressTypeEnum.UNCOMPRESS) {
            buffer = compressTypeEnum.getCompress().compress(buffer);
            isCompress = true;
        }
        int totalLength = ProtocolConsts.PROTOCOL_PACKAGE_HEADER_LENGTH + buffer.length;
        out.writeInt(totalLength);
        out.writeByte(ProtocolConsts.VERSION);
        out.writeByte(msg.getRequestProtocolTypeEnum().getType());
        out.writeByte(isCompress ? compressTypeEnum.getType() : CompressTypeEnum.UNCOMPRESS.getType());
        out.writeByte(serializerTypeEnum.getType());
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
        logger.error("请求协议编码出错:" + cause.getMessage(), cause);
    }
}
