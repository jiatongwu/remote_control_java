package cn.xvkang.rc_java_common.nettyProtobuf.client;

import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.ByteString;

import cn.xvkang.rc_java_common.utils.SpringContextHolder;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data.DataType;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.ListRinedtAndSshrListenInfoReturnData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRRemoteClientReturnedData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRTellRemoteClientDisConnectData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRTellServerClientDisConnectData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRToRemoteClientData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshrToRemoteSocketComeInData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author wu
 *
 */
@Slf4j
public class MyChannelHandler extends SimpleChannelInboundHandler<ProtoBufNettyMessage.Data> {
    @SuppressWarnings("unused")
    private NettyProtobufClientBootstrap nettyProtobufClientBootstrap;

    public static AttributeKey<Boolean> proxySocketCreated = AttributeKey.valueOf("proxySocketCreated");
    
    private boolean active=false;

    public MyChannelHandler(NettyProtobufClientBootstrap nettyProtobufClientBootstrap) {
        super();
        this.nettyProtobufClientBootstrap = nettyProtobufClientBootstrap;
    }

    /**
     * uuid 对应 的客户端代理socket
     */
    private Map<String, NetSocket> uuidNetSocketProxyMapForSshr = new HashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtoBufNettyMessage.Data msg) throws Exception {
        ProtoBufNettyMessage.Data.DataType dataType = msg.getDataType();
        if (dataType == ProtoBufNettyMessage.Data.DataType.SSHRTOREMOTECLIENTDATA) {
            SshRToRemoteClientData sshRToRemoteClientData = msg.getSshRToRemoteClientData();
            processSshRToRemoteClientData(sshRToRemoteClientData, ctx);
            // log.info("客户端收到：SSHRTOREMOTECLIENTDATA");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.SSHR_TELL_REMOTE_CLIENT_DISCONNECT) {
            SshRTellRemoteClientDisConnectData sshRTellRemoteClientDisConnectData = msg
                    .getSshRTellRemoteClientDisConnectData();
            String uuid = sshRTellRemoteClientDisConnectData.getUuid();
            NetSocket netSocket = uuidNetSocketProxyMapForSshr.get(uuid);
            if (netSocket != null) {
                try {
                    netSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            log.info("客户端收到：SSHR_TELL_REMOTE_CLIENT_DISCONNECT");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.LIST_RINETD_AND_SSHR_LISTEN_INFO_RETURN) {
            ListRinedtAndSshrListenInfoReturnData listRinedtAndSshrListenInfoReturnData = msg
                    .getListRinedtAndSshrListenInfoReturnData();

            log.info("客户端收到：LIST_RINETD_AND_SSHR_LISTEN_INFO_RETURN {}", listRinedtAndSshrListenInfoReturnData);
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.PONG) {
            // log.info("客户端收到：PONG ");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.SSHRTOREMOTE_SOCKET_COME_IN) {            
            processSshRToRemotSocketComeInData(msg.getSshrToRemoteSocketComeInData(), ctx);
            Thread.sleep(100);
        }

    }

    private void processSshRToRemotSocketComeInData(SshrToRemoteSocketComeInData sshrToRemoteSocketComeInData,
            ChannelHandlerContext ctx) {
        String uuid = sshrToRemoteSocketComeInData.getUuid();
        int serverListenPort = sshrToRemoteSocketComeInData.getServerListenPort();
        int localPort = sshrToRemoteSocketComeInData.getLocalPort();

        // 向本地服务器建立连接,并发送数据
        // 判断是否已经建立了连接
        NetSocket netSocketProxy = uuidNetSocketProxyMapForSshr.get(uuid);
        if (netSocketProxy == null) {
            //Vertx vertx = Vertx.vertx();
            Vertx vertx = SpringContextHolder.getBean(Vertx.class);
            NetClientOptions optionsProxyClient = new NetClientOptions().setConnectTimeout(10000);
            NetClient client = vertx.createNetClient(optionsProxyClient);
            client.connect(localPort, "127.0.0.1", res -> {
                if (res.succeeded()) {
                    NetSocket netSocketProxyFirst = res.result();
                    uuidNetSocketProxyMapForSshr.put(uuid, netSocketProxyFirst);
                    netSocketProxyFirst.handler(bufferProxy -> {
                        // 代理socket收到数据后 转发给远程服务器，让它再转给最终的客户端
                        ByteString byteString = ByteString.copyFrom(bufferProxy.getBytes());
                        SshRRemoteClientReturnedData sshRRemoteClientReturnedData = SshRRemoteClientReturnedData
                                .newBuilder().setUuid(uuid).setServerListenPort(serverListenPort)
                                .setBytesData(byteString).build();
                        Data data = ProtoBufNettyMessage.Data.newBuilder()
                                .setDataType(DataType.SSHRREMOTECLIENTRETURNEDDATA)
                                .setSshRRemoteClientReturnedData(sshRRemoteClientReturnedData).build();
                        ctx.writeAndFlush(data);
                    });
                    netSocketProxyFirst.closeHandler((a) -> {
                        // 告诉远程服务器 本地服务器关闭了连接
                        closeLocalProxySocketAndTelRemoteServerCloseSocket(client, ctx, uuid, serverListenPort);
                    });
                } else {
                    closeLocalProxySocketAndTelRemoteServerCloseSocket(client, ctx, uuid, serverListenPort);
                    log.error("Failed to connect: ", res.cause().getMessage());
                }
            });
        }

    }

    /**
     * sshr 有人在远程服务器建立 连接了并且带着数据过来了 ，需要启动一个客户端代理去访问本地服务器。
     * 
     * @param sshRToRemoteClientData
     * @author vwujiatong
     * @param ctx
     * @date 2021年7月5日 下午8:30:58
     */
    private void processSshRToRemoteClientData(SshRToRemoteClientData sshRToRemoteClientData,
            ChannelHandlerContext ctx) {
        String uuid = sshRToRemoteClientData.getUuid();
        int serverListenPort = sshRToRemoteClientData.getServerListenPort();
        int localPort = sshRToRemoteClientData.getLocalPort();
        ByteString bytesData = sshRToRemoteClientData.getBytesData();

        // 向本地服务器建立连接,并发送数据
        // 判断是否已经建立了连接
        NetSocket netSocketProxy = uuidNetSocketProxyMapForSshr.get(uuid);
        if (netSocketProxy != null) {
            // 收到数据 转发给toIp toPort
            netSocketProxy.write(Buffer.buffer(bytesData.toByteArray()));
        } else {
//            Vertx vertx = Vertx.vertx();
            Vertx vertx = SpringContextHolder.getBean(Vertx.class);
            NetClientOptions optionsProxyClient = new NetClientOptions().setConnectTimeout(10000);
            NetClient client = vertx.createNetClient(optionsProxyClient);
            client.connect(localPort, "127.0.0.1", res -> {
                if (res.succeeded()) {
                    NetSocket netSocketProxyFirst = res.result();
                    uuidNetSocketProxyMapForSshr.put(uuid, netSocketProxyFirst);
                    netSocketProxyFirst.handler(bufferProxy -> {
                        // 代理socket收到数据后 转发给远程服务器，让它再转给最终的客户端
                        ByteString byteString = ByteString.copyFrom(bufferProxy.getBytes());
                        SshRRemoteClientReturnedData sshRRemoteClientReturnedData = SshRRemoteClientReturnedData
                                .newBuilder().setUuid(uuid).setServerListenPort(serverListenPort)
                                .setBytesData(byteString).build();
                        Data data = ProtoBufNettyMessage.Data.newBuilder()
                                .setDataType(DataType.SSHRREMOTECLIENTRETURNEDDATA)
                                .setSshRRemoteClientReturnedData(sshRRemoteClientReturnedData).build();
                        ctx.writeAndFlush(data);
                    });
                    netSocketProxyFirst.closeHandler((a) -> {
                        // 告诉远程服务器 本地服务器关闭了连接
                        closeLocalProxySocketAndTelRemoteServerCloseSocket(client, ctx, uuid, serverListenPort);
                    });

                    // 收到数据 转发给本地服务器
                    netSocketProxyFirst.write(Buffer.buffer(bytesData.toByteArray()));
                } else {
                    closeLocalProxySocketAndTelRemoteServerCloseSocket(client, ctx, uuid, serverListenPort);
                    log.error("Failed to connect: ", res.cause().getMessage());
                }
            });
        }

    }

    private void closeLocalProxySocketAndTelRemoteServerCloseSocket(NetClient client, ChannelHandlerContext ctx,
            String uuid, int serverListenPort) {
        try {
            client.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 通知服务器上的客户端 断开连接
        SshRTellServerClientDisConnectData sshRTellServerClientDisConnectData = SshRTellServerClientDisConnectData
                .newBuilder().setUuid(uuid).setServerListenPort(serverListenPort).build();
        Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.SSHR_TELL_SERVER_CLIENT_DISCONNECT)
                .setSshRTellServerClientDisConnectData(sshRTellServerClientDisConnectData).build();
        ctx.writeAndFlush(data);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);

        System.out.println("handlerAdded,channel's id:" + ctx.channel().id().asLongText());
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        System.out.println("channelRegistered");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        nettyProtobufClientBootstrap.restartRinetdAndSshr();
        System.out.println("channelActive:" + ctx.channel().remoteAddress() + " 上线");
        active=true;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        System.out.println("channelInactive:" + ctx.channel().remoteAddress() + " 掉线");
        shutdownClientAndReconnectServer(ctx);
        active=false;
    }

    private void shutdownClientAndReconnectServer(ChannelHandlerContext ctx) throws InterruptedException {
        nettyProtobufClientBootstrap.setChannel(null);
        // nettyProtobufClientBootstrap.getRemoteConnectBossGroup().shutdownGracefully();
        Thread.sleep(5000);
        // 断线了 重新进行连接
        nettyProtobufClientBootstrap.connect();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        System.out.println("channelUnregistered");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        System.out.println("handlerRemoved");

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // IdleStateHandler 所产生的 IdleStateEvent 的处理逻辑.
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            switch (e.state()) {
            case READER_IDLE:
                handleReaderIdle(ctx);
                break;
            case WRITER_IDLE:
                handleWriterIdle(ctx);
                break;
            case ALL_IDLE:
                handleAllIdle(ctx);
                break;
            default:
                break;
            }
        }
    }

    private void handleAllIdle(ChannelHandlerContext ctx2) {

    }

    private void handleWriterIdle(ChannelHandlerContext ctx2) {

    }

    private void handleReaderIdle(ChannelHandlerContext ctx2) {
        if(active)
            ctx2.close();
    }
}