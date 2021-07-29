package cn.xvkang.rc_java_common.nettyProtobuf.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import org.apache.commons.lang3.StringUtils;

import cn.xvkang.rc_java_common.nettyProtobuf.client.command.SendCommandToServerCommand;
//import cn.xvkang.sorClient.command.SendCommandToServerCommand;
//import cn.xvkang.sorClient.command.SendCommandToServerCommand.ListenPortToIpToPortPojo;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data.DataType;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Rinetd;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshR;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
@lombok.Data
public class NettyProtobufClientBootstrap {

    private Bootstrap bootstrap = new Bootstrap();
    private NioEventLoopGroup remoteConnectBossGroup = new NioEventLoopGroup(1,
            new DefaultThreadFactory("netty-client"));
    MyChannelInitializer myChannelInitializer;
    Channel channel;
    private String serverIp;
    private Integer serverPort;

    public static Thread thread;
    public static Thread pingThread;

    public static boolean stoped = false;

    public void readFromCommandLine(NettyProtobufClientBootstrap nettyProtobufClientBootstrap) {
        if (thread == null) {
            thread = new Thread() {
                public void run() {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                    String line = null;
                    while (true) {
                        if (stoped) {
                            break;
                        }
                        try {
                            line = br.readLine();
                            if (StringUtils.isNotBlank(line)) {
                                CommandLine.run(new SendCommandToServerCommand(nettyProtobufClientBootstrap),
                                        System.err, StringUtils.split(line, " "));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
            };
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void restartRinetdAndSshr() {
//        for (Integer listenPort : SendCommandToServerCommand.rinetdPortMap.keySet()) {
//            ListenPortToIpToPortPojo listenPortToIpToPortPojo = SendCommandToServerCommand.rinetdPortMap
//                    .get(listenPort);
//            String type = listenPortToIpToPortPojo.getType();
//            String toip = listenPortToIpToPortPojo.getToip();
//            Integer toport = listenPortToIpToPortPojo.getToport();
//            createOneRinetd(listenPort, toip, toport);
//        }
//        for (Integer listenPort : SendCommandToServerCommand.sshRPortMap.keySet()) {
//            ListenPortToIpToPortPojo listenPortToIpToPortPojo = SendCommandToServerCommand.sshRPortMap.get(listenPort);
//            String type = listenPortToIpToPortPojo.getType();
//            String toip = listenPortToIpToPortPojo.getToip();
//            Integer toport = listenPortToIpToPortPojo.getToport();
//            createSshR(listenPort, toport);
//        }
    }

    private void startPingThread() {
        if (pingThread == null) {
            pingThread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        if (stoped) {
                            break;
                        }
                        sendPingToServer();
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            };
            pingThread.setDaemon(true);
            pingThread.start();
        }
    }

    public boolean createOneRinetd(int listenPort, String toIp, int toPort) {
        if (channel != null) {
            Rinetd rinetdData = Rinetd.newBuilder().setListenPort(listenPort).setToIp(toIp)// ("47.92.226.141")
                    // .setToPort(20022)
                    .setToPort(toPort).build();
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.RINETD).setRinetdData(rinetdData)
                    .build();
            channel.writeAndFlush(data);
            return true;
        } else {
            log.error("client channel is null");
        }

        return false;
    }

    public boolean createSshR(int listenPort, int clientLocalPort) {
        if (channel != null) {
            SshR sshr = SshR.newBuilder().setListenPort(listenPort).setClientLocalPort(clientLocalPort).build();
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.SSHR).setSshRData(sshr).build();
            channel.writeAndFlush(data);
            return true;
        } else {
            log.error("client channel is null");
        }
        return false;
    }

    public boolean sendPingToServer() {
        if (channel != null) {
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.PING).build();
            channel.writeAndFlush(data);
            // log.info("sendPingToServer");
            return true;
        } else {
            log.error("client channel is null");
        }
        return false;
    }

    public boolean stopAllRinetdListenServer() {
        if (channel != null) {
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.STOP_All_RINETD).build();
            channel.writeAndFlush(data);
            return true;
        } else {
            log.error("client channel is null");
        }
        return false;
    }

    public boolean stopAllSshRListenServer() {
        if (channel != null) {
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.STOP_ALL_SSHR).build();
            channel.writeAndFlush(data);
            return true;
        } else {
            log.error("client channel is null");
        }
        return false;
    }

    public boolean getAllRinetdAndSShrListenPort() {
        if (channel != null) {
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.LIST_RINETD_AND_SSHR_LISTEN_INFO)
                    .build();
            channel.writeAndFlush(data);
            return true;
        } else {
            log.error("client channel is null");
        }
        return false;
    }

    public void stop() {
        stoped = true;
        if (channel != null) {
            try {
                channel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        channel = null;
        remoteConnectBossGroup.shutdownGracefully();
    }

    public void init() {
        if (myChannelInitializer == null)
            myChannelInitializer = new MyChannelInitializer(this);
        bootstrap.group(remoteConnectBossGroup)//
                .option(ChannelOption.TCP_NODELAY, true)//
                .option(ChannelOption.SO_REUSEADDR, true)//
                .channel(NioSocketChannel.class);
        bootstrap.handler(myChannelInitializer);
    }

//    public ChannelFuture connectOld(String ip, int port) {
//        this.ip = ip;
//        this.port = port;
//
//        try {
//            remoteConnectBossGroup.shutdownNow();
//            Thread.sleep(1000);
//        } catch (Exception e) {
//        }
//        remoteConnectBossGroup = new NioEventLoopGroup(1);
//
//        if (myChannelInitializer == null)
//            myChannelInitializer = new MyChannelInitializer(this);
//
//        if (bootstrap == null) {
//            bootstrap = new Bootstrap();
//            bootstrap.group(remoteConnectBossGroup).channel(NioSocketChannel.class)
//                    .option(ChannelOption.TCP_NODELAY, true).handler(myChannelInitializer);
//        }
//
//        // TODO socks5和shadowsocks关键区别
//        // logger.trace("连接目标服务器");
//        // ChannelFuture future = bootstrap.connect(msg.dstAddr(), msg.dstPort());
//        log.info("连接shadowsocks目标服务器");
//        ChannelFuture channelFuture = bootstrap.connect(ip, port);
//        return channelFuture;
//
//    }

//    public void reConnect() {
//        ChannelFuture channelFuture = connect(serverIp, serverPort);
//        channelFuture.addListener(new ChannelFutureListener() {
//
//            public void operationComplete(final ChannelFuture future) throws Exception {
//                if (future.isSuccess()) {
//                    log.info("成功连接目标服务器");
//                    Channel channel = channelFuture.channel();
//                    setChannel(channel);
//
//                    // 连接成功或重新连接成功后 需要重新启动rinetd和sshr
//                    restartRinetdAndSshr();
//                    // 接收用户输入的指令线程
//                    readFromCommandLine(NettyProtobufClientBootstrap.this);
//                    // 每隔5s定时发PING消息
//                    startPingThread();
//
//                } else {
//                    log.error("连接服务器失败");
//                    try {
//                        getChannel().close();
//                    } catch (Exception e) {
//                    }
//                    try {
//                        getRemoteConnectBossGroup().shutdownGracefully();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                    setChannel(null);
//                    Thread.sleep(5000);
//                    // 连接没有连接成功 重新进行连接
//                    reConnect();
//                }
//            }
//
//        });
//    }

    public ChannelFuture connect() {
        ChannelFuture future = bootstrap.connect(new InetSocketAddress(serverIp, serverPort));
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (future.isSuccess()) {
                    log.info("start netty client success, host={}, port={}", serverIp, serverPort);

                    log.info("成功连接目标服务器");
                    Channel channel = future.channel();
                    setChannel(channel);

                    // 连接成功或重新连接成功后 需要重新启动rinetd和sshr
                    // restartRinetdAndSshr();
                    // 接收用户输入的指令线程
                    readFromCommandLine(NettyProtobufClientBootstrap.this);
                    // 每隔5s定时发PING消息
                    startPingThread();

                } else {
                    if (!stoped) {
                        setChannel(null);
                        // final EventLoop loop = future.channel().eventLoop();
//                        loop.schedule(new Runnable() {
//                            @Override
//                            public void run() {
//                                // Reconnection
//                                log.info("reconnect host:{},port:{}", serverIp, serverPort);
//                                NettyProtobufClientBootstrap.this.connect();
//                            }
//                        }, 5L, TimeUnit.SECONDS);

                        try {
                            Thread.sleep(5000);
                            log.info("reconnect host:{},port:{}", serverIp, serverPort);
                            NettyProtobufClientBootstrap.this.connect();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        });
        return future;
    }

    public NettyProtobufClientBootstrap(String serverIp, Integer serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        init();
    }

    public static void main(String[] args) {
        NettyProtobufClientBootstrap nettyProtobufClientBootstrap = new NettyProtobufClientBootstrap("serverIp", 8888);
        nettyProtobufClientBootstrap.connect();
    }

}
