package cn.xvkang.rc_java_common.nettyProtobuf.client;

import java.util.concurrent.TimeUnit;

import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

public class MyChannelInitializer extends ChannelInitializer<SocketChannel> {
    private NettyProtobufClientBootstrap nettyProtobufClientBootstrap;
    private MyChannelHandler myChannelHandler;

    public MyChannelInitializer(NettyProtobufClientBootstrap nettyProtobufClientBootstrap) {
        this.nettyProtobufClientBootstrap = nettyProtobufClientBootstrap;
        System.out.println("netty thread's name:" + Thread.currentThread().getName());
        System.out.println("MyChannelInitializer6() constructor");
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        myChannelHandler = new MyChannelHandler(nettyProtobufClientBootstrap);
        ChannelPipeline channelPipeline = ch.pipeline();
        channelPipeline.addLast(new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
        channelPipeline.addLast(new ProtobufVarint32FrameDecoder());
        channelPipeline.addLast(new ProtobufDecoder(ProtoBufNettyMessage.Data.getDefaultInstance()));
        channelPipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        channelPipeline.addLast(new ProtobufEncoder());
        channelPipeline.addLast(myChannelHandler);

    }
}
