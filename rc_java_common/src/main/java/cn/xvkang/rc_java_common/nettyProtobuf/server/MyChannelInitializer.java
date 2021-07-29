package cn.xvkang.rc_java_common.nettyProtobuf.server;

import java.util.concurrent.TimeUnit;

import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;

public class MyChannelInitializer extends ChannelInitializer<SocketChannel> {
    private NettyProtobufServerBootstrap nettyProtobufServerBootstrap;
    
    public MyChannelInitializer(NettyProtobufServerBootstrap nettyProtobufServerBootstrap) {
        this.nettyProtobufServerBootstrap = nettyProtobufServerBootstrap;
        System.out.println("netty thread's name:" + Thread.currentThread().getName());
        System.out.println("MyChannelInitializer6() constructor");
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
      
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new IdleStateHandler(100, 0, 0, TimeUnit.SECONDS));
        channelPipeline.addLast(new ProtobufVarint32FrameDecoder());
        channelPipeline.addLast(new ProtobufDecoder(ProtoBufNettyMessage.Data.getDefaultInstance()));
        channelPipeline.addLast(new MyProtobufVarint32LengthFieldPrepender());
        channelPipeline.addLast(new ProtobufEncoder());
        channelPipeline.addLast(new MyChannelHandler(nettyProtobufServerBootstrap));

    }
}
