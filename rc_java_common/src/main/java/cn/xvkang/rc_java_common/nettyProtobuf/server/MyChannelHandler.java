package cn.xvkang.rc_java_common.nettyProtobuf.server;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import cn.xvkang.rc_java_common.utils.SpringContextHolder;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Data.DataType;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.ListRinedtAndSshrListenInfoReturnData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.Rinetd;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshR;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRRemoteClientReturnedData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRTellRemoteClientDisConnectData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRTellServerClientDisConnectData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshRToRemoteClientData;
import cn.xvkang.wussserver2.nettyProtobuf.protobuf.ProtoBufNettyMessage.SshrToRemoteSocketComeInData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author wu
 *
 */
@Slf4j
public class MyChannelHandler extends SimpleChannelInboundHandler<ProtoBufNettyMessage.Data> {

    private NettyProtobufServerBootstrap nettyProtobufServerBootstrap;

    private boolean active = false;

    public MyChannelHandler(NettyProtobufServerBootstrap nettyProtobufServerBootstrap) {
        super();
        this.nettyProtobufServerBootstrap = nettyProtobufServerBootstrap;
    }

    /**
     * 一个监听端口对应一个服务器
     */
    public static Map<String, NetServer> listenPortNetServerMapForRinetd = new HashMap<>();
    /**
     * 监听服务器 有多少个客户端在连接 以及对应的代理客户端
     */
    public static Map<NetServer, Map<NetSocket, NetSocket>> netServerSocketMapForRinetd = new HashMap<>();

    public static Set<NetServer> allSshRNetServer = new HashSet<>();

    public static Set<MyChannelHandler> myChannelHandlers = new HashSet<>();

    /**
     * 记录所有sshr 监听端口的情况
     */
    public static Map<String, NetServer> allListenPortNetServerForSshr = new HashMap<>();

    /**
     * 一个监听端口对应一个服务器
     */
    public Map<String, NetServer> listenPortNetServerMapForSshr = new ConcurrentHashMap<>();
    /**
     * 监听服务器 有多少个客户端在连接 以及对应的代理客户端
     */
    public Map<NetServer, Map<String, NetSocket>> netServerSocketMapForSshr = new HashMap<>();

    private ChannelHandlerContext ctx;

    /**
     * 根据uuid找到
     */

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtoBufNettyMessage.Data msg) throws Exception {
        ProtoBufNettyMessage.Data.DataType dataType = msg.getDataType();
        if (dataType == ProtoBufNettyMessage.Data.DataType.RINETD) {
            processRinetd(msg);
            log.info("服务器收到：rinetd");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.SSHR) {
            ProtoBufNettyMessage.SshR sshRData = msg.getSshRData();
            processSshR(sshRData, ctx);
            log.info("服务器收到：sshr");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.SSHRREMOTECLIENTRETURNEDDATA) {
            SshRRemoteClientReturnedData sshRRemoteClientReturnedData = msg.getSshRRemoteClientReturnedData();
            processSshRRemoteClientReturnedData(sshRRemoteClientReturnedData);
            // log.info("服务器收到：SSHRREMOTECLIENTRETURNEDDATA");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.SSHR_TELL_SERVER_CLIENT_DISCONNECT) {
            SshRTellServerClientDisConnectData sshRTellServerClientDisConnectData = msg
                    .getSshRTellServerClientDisConnectData();
            processSshRTellServerClientDisConnect(sshRTellServerClientDisConnectData);
            log.info("服务器收到：SSHR_TELL_SERVER_CLIENT_DISCONNECT");
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.STOP_All_RINETD) {
            log.info("服务器收到：STOP_All_RINETD");
            processStopAllRinetd();
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.STOP_ALL_SSHR) {
            log.info("服务器收到：STOP_ALL_SSHR");
            processStopAllSshR(ctx);
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.LIST_RINETD_AND_SSHR_LISTEN_INFO) {
            log.info("服务器收到：LIST_RINETD_AND_SSHR_LISTEN_INFO");
            processReturnRinetdAndSshRInfo(ctx);
        } else if (dataType == ProtoBufNettyMessage.Data.DataType.PING) {
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.PONG).build();
            ctx.writeAndFlush(data);
        }

    }

    private void processReturnRinetdAndSshRInfo(ChannelHandlerContext ctx) {
        SshrPortAndRinetdPort sshrPortAndRinetdPort = new SshrPortAndRinetdPort();
        Set<String> rinetdPorts = new HashSet<>();
        sshrPortAndRinetdPort.setRinetdPorts(rinetdPorts);
        Set<String> sshrPorts = new HashSet<>();
        sshrPortAndRinetdPort.setSshrPorts(sshrPorts);

        for (MyChannelHandler myChannelHandler : myChannelHandlers) {
            Map<String, NetServer> listenPortNetServerMapForSshrTmp = myChannelHandler
                    .getListenPortNetServerMapForSshr();
            for (String port : listenPortNetServerMapForSshrTmp.keySet()) {
                sshrPorts.add(port);
            }
        }

        for (String listenPort : listenPortNetServerMapForRinetd.keySet()) {
            rinetdPorts.add(listenPort);
        }
        ObjectMapper om = new ObjectMapper();
        ListRinedtAndSshrListenInfoReturnData listRinedtAndSshrListenInfoReturnData;
        try {
            listRinedtAndSshrListenInfoReturnData = ListRinedtAndSshrListenInfoReturnData.newBuilder()
                    .setJsonData(om.writeValueAsString(sshrPortAndRinetdPort)).build();
            Data data = ProtoBufNettyMessage.Data.newBuilder()
                    .setDataType(DataType.LIST_RINETD_AND_SSHR_LISTEN_INFO_RETURN)
                    .setListRinedtAndSshrListenInfoReturnData(listRinedtAndSshrListenInfoReturnData).build();
            ctx.writeAndFlush(data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    @lombok.Data
    public static class SshrPortAndRinetdPort {
        private Set<String> rinetdPorts;
        private Set<String> sshrPorts;
    }

    private void processStopAllSshR(ChannelHandlerContext ctx) {
        // 关闭sshr连接的所有server
        for (MyChannelHandler myChannelHandler : myChannelHandlers) {
            myChannelHandler.closeCurrentConnectSshrServer(myChannelHandler.ctx);
        }
    }

    private void processStopAllRinetd() {
        for (String listenPort : listenPortNetServerMapForRinetd.keySet()) {
            NetServer netServer = listenPortNetServerMapForRinetd.get(listenPort);
            closeClientSocketAndClientProxySocketForRinetdAndRemoveMapWhenNetServerCloseForRinetd(listenPort,
                    netServer);
            try {
                netServer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        listenPortNetServerMapForRinetd.clear();
        netServerSocketMapForRinetd.clear();

    }

    /**
     * 远程客户端 的socket 代理 关闭了
     * 
     * @param
     * @return
     * @author vwujiatong
     * @date 2021年7月5日 下午8:53:10
     * @throws
     */
    private void processSshRTellServerClientDisConnect(
            SshRTellServerClientDisConnectData sshRTellServerClientDisConnectData) {
        String uuid = sshRTellServerClientDisConnectData.getUuid();
        int serverListenPort = sshRTellServerClientDisConnectData.getServerListenPort();
        NetServer netServer = listenPortNetServerMapForSshr.get(serverListenPort + "");
        Map<String, NetSocket> uuidSocketMap = netServerSocketMapForSshr.get(netServer);
        if (uuidSocketMap != null) {
            NetSocket netSocket = uuidSocketMap.get(uuid);
            if (netSocket != null) {
                try {
                    netSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ssr remote client returned data
     * 
     * @param
     * @return
     * @author vwujiatong
     * @date 2021年7月5日 下午7:57:49
     * @throws
     */
    private void processSshRRemoteClientReturnedData(SshRRemoteClientReturnedData sshRRemoteClientReturnedData) {
        String uuid = sshRRemoteClientReturnedData.getUuid();
        ByteString bytesData = sshRRemoteClientReturnedData.getBytesData();
        byte[] byteArray = bytesData.toByteArray();
        int serverListenPort = sshRRemoteClientReturnedData.getServerListenPort();

        NetServer netServer = listenPortNetServerMapForSshr.get(serverListenPort + "");

        Map<String, NetSocket> uuidSocketMap = netServerSocketMapForSshr.get(netServer);
        if (uuidSocketMap != null) {
            NetSocket netSocket = uuidSocketMap.get(uuid);
            if (netSocket != null) {
                netSocket.write(Buffer.buffer(byteArray));
            }
        }
    }

    private Rinetd processRinetd(ProtoBufNettyMessage.Data msg) {
        Rinetd rinetdData = msg.getRinetdData();
        int listenPort = rinetdData.getListenPort();
        String toIp = rinetdData.getToIp();
        int toPort = rinetdData.getToPort();

        // 判断是否已经有端口在监听了
        NetServer netServerOld = listenPortNetServerMapForRinetd.get(listenPort + "");

        if (netServerOld != null) {
            netServerOld.close((a) -> {
                // 通知原服务器下的 socket 和 socket对应的远端客户端代理socket 进行关闭
                closeClientSocketAndClientProxySocketForRinetdAndRemoveMapWhenNetServerCloseForRinetd(listenPort + "",
                        netServerOld);
                log.info("netServerOld stoped {}", listenPort);
                // removeMapWhenNetServerCloseForRinetd(listenPort, netServerOld);
                startRinetd(listenPort, toIp, toPort);
            });
        } else {
            startRinetd(listenPort, toIp, toPort);
        }

        return rinetdData;
    }

    private void processSshR(SshR sshRData, ChannelHandlerContext ctx) {
        log.info("processSshR allListenPortNetServerForSshr:{}", allListenPortNetServerForSshr);
        int listenPort = sshRData.getListenPort();
        int clientLocalPort = sshRData.getClientLocalPort();
        // 判断是否已经有端口在监听了
        NetServer netServerOld = listenPortNetServerMapForSshr.get(listenPort + "");
        NetServer netServerOld2 = allListenPortNetServerForSshr.get(listenPort + "");
        if (netServerOld != null || netServerOld2 != null) {
            if (netServerOld != null) {
                // 通知原服务器下的 socket 和 socket对应的代理socket 进行关闭
                netServerOld.close((a) -> {
                    log.info("netServerOld stoped {}", listenPort);
                    // 通知原服务器下的 socket 和 socket对应的远端客户端代理socket 进行关闭
                    closeClientSocketAndClientProxySocketForSshRAndRemoveMapWhenNetServerCloseForSshR(listenPort + "",
                            netServerOld, ctx);
                    // removeMapWhenNetServerCloseForSshR(listenPort + "", netServerOld);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    startSshR(listenPort, clientLocalPort, ctx);
                });
            }
            if (netServerOld2 != null) {
                // 通知原服务器下的 socket 和 socket对应的代理socket 进行关闭
                netServerOld2.close((a) -> {
                    log.info("netServerOld2 stoped {}", listenPort);
                    // 通知原服务器下的 socket 和 socket对应的远端客户端代理socket 进行关闭
                    closeClientSocketAndClientProxySocketForSshRAndRemoveMapWhenNetServerCloseForSshR(listenPort + "",
                            netServerOld2, ctx);
                    // removeMapWhenNetServerCloseForSshR(listenPort + "", netServerOld2);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    startSshR(listenPort, clientLocalPort, ctx);
                });
            }
        } else {
            startSshR(listenPort, clientLocalPort, ctx);
        }

    }

    private void startRinetd(int listenPort, String toIp, int toPort) {
        Vertx vertx = SpringContextHolder.getBean(Vertx.class); // Vertx.vertx();
        NetServerOptions options = new NetServerOptions().setPort(listenPort).setTcpKeepAlive(true);
        final NetServer server = vertx.createNetServer(options);

        server.connectHandler(socket -> {
            socket.closeHandler((a) -> {
                closeOneProxySocketForRinetd(server, socket);
                Map<NetSocket, NetSocket> netServerSocketMapOuter = netServerSocketMapForRinetd.get(server);
                if (netServerSocketMapOuter == null) {
                    netServerSocketMapOuter = new HashMap<>();
                    netServerSocketMapForRinetd.put(server, netServerSocketMapOuter);
                }
                netServerSocketMapOuter.remove(socket);
            });

            // 有连接进来了 创建代理socket和目标服务器建立连接
            Map<NetSocket, NetSocket> netServerSocketMapSocketComeIn = netServerSocketMapForRinetd.get(server);
            if (netServerSocketMapSocketComeIn == null) {
                netServerSocketMapSocketComeIn = new HashMap<>();
                netServerSocketMapForRinetd.put(server, netServerSocketMapSocketComeIn);
            }
            NetSocket netSocketProxySocketComeIn = netServerSocketMapSocketComeIn.get(socket);
            if (netSocketProxySocketComeIn == null) {
                NetClientOptions optionsProxyClient = new NetClientOptions().setConnectTimeout(10000);
                NetClient clientProxy = vertx.createNetClient(optionsProxyClient);
                clientProxy.connect(toPort, toIp, res -> {
                    if (res.succeeded()) {
                        NetSocket netSocketProxyFirst = res.result();
                        Map<NetSocket, NetSocket> netServerSocketMapInner = netServerSocketMapForRinetd.get(server);
                        if (netServerSocketMapInner == null) {
                            netServerSocketMapInner = new HashMap<>();
                            netServerSocketMapForRinetd.put(server, netServerSocketMapInner);
                        }
                        netServerSocketMapInner.put(socket, netSocketProxyFirst);

                        netSocketProxyFirst.handler(bufferProxy -> {
                            // 代理socket收到数据后 转发给原客户端
                            socket.write(bufferProxy);
                        });
                        netSocketProxyFirst.closeHandler((a) -> {
                            closeClientSocketAndProxyClient(server, socket, clientProxy, netSocketProxyFirst);
                        });
                    } else {
                        closeClientSocketAndProxyClient(server, socket, clientProxy, null);
                        log.error("Failed to connect: ", res.cause().getMessage());
                    }
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            socket.handler(buffer -> {
                // 创建代理socket 向远程服务器建立连接,并发送数据
                // 判断是否已经建立了连接
                Map<NetSocket, NetSocket> netServerSocketMap = netServerSocketMapForRinetd.get(server);
                if (netServerSocketMap == null) {
                    netServerSocketMap = new HashMap<>();
                    netServerSocketMapForRinetd.put(server, netServerSocketMap);
                }
                NetSocket netSocketProxy = netServerSocketMap.get(socket);
                if (netSocketProxy != null) {
                    // 收到数据 转发给toIp toPort
                    netSocketProxy.write(buffer);
                } else {
                    NetClientOptions optionsProxyClient = new NetClientOptions().setConnectTimeout(10000);
                    NetClient clientProxy = vertx.createNetClient(optionsProxyClient);

                    clientProxy.connect(toPort, toIp, res -> {
                        if (res.succeeded()) {
                            NetSocket netSocketProxyFirst = res.result();
                            Map<NetSocket, NetSocket> netServerSocketMapInner = netServerSocketMapForRinetd.get(server);
                            if (netServerSocketMapInner == null) {
                                netServerSocketMapInner = new HashMap<>();
                                netServerSocketMapForRinetd.put(server, netServerSocketMapInner);
                            }
                            netServerSocketMapInner.put(socket, netSocketProxyFirst);

                            netSocketProxyFirst.handler(bufferProxy -> {
                                // 代理socket收到数据后 转发给原客户端
                                socket.write(bufferProxy);
                            });
                            netSocketProxyFirst.closeHandler((a) -> {
                                closeClientSocketAndProxyClient(server, socket, clientProxy, netSocketProxyFirst);
                            });
                            // 收到数据 转发给toIp toPort
                            netSocketProxyFirst.write(buffer);
                        } else {
                            closeClientSocketAndProxyClient(server, socket, clientProxy, null);
                            log.error("Failed to connect: ", res.cause().getMessage());
                        }
                    });
                }
            });
        });

        server.exceptionHandler((e) -> {
            log.error("netServer.exceptionHandler rinetd {}", e);
        });

        server.listen(listenPort, res -> {
            if (res.succeeded()) {
                log.info("Server is now listening! {}", listenPort);
                listenPortNetServerMapForRinetd.put(listenPort + "", server);
                Map<NetSocket, NetSocket> socketAndProxySocketMap = netServerSocketMapForRinetd.get(server);
                if (socketAndProxySocketMap == null) {
                    socketAndProxySocketMap = new HashMap<>();
                    netServerSocketMapForRinetd.put(server, socketAndProxySocketMap);
                }
            } else {
                log.info("Failed to bind! {}", listenPort);
                closeClientSocketAndClientProxySocketForRinetdAndRemoveMapWhenNetServerCloseForRinetd(listenPort + "",
                        server);
            }
        });
    }

    private void startSshR(int listenPort, int clientLocalPort, ChannelHandlerContext ctx) {
        // Vertx vertx = Vertx.vertx();
        Vertx vertx = SpringContextHolder.getBean(Vertx.class);
        NetServerOptions options = new NetServerOptions().setPort(listenPort).setTcpKeepAlive(true);
        NetServer server = vertx.createNetServer(options);

        server.connectHandler(socket -> {
            String socketUuid = UUID.randomUUID().toString();

            // 通知远程客户端 有连接进来了来了 请创建socket代理
            SshrToRemoteSocketComeInData sshrToRemoteSocketComeInData = SshrToRemoteSocketComeInData.newBuilder()
                    .setUuid(socketUuid).setLocalPort(clientLocalPort).setServerListenPort(listenPort).build();
            Data ssrToRemoteSocketComeInData = ProtoBufNettyMessage.Data.newBuilder()
                    .setDataType(DataType.SSHRTOREMOTE_SOCKET_COME_IN)
                    .setSshrToRemoteSocketComeInData(sshrToRemoteSocketComeInData).build();
            ctx.writeAndFlush(ssrToRemoteSocketComeInData);

            Map<String, NetSocket> socketAndProxySocketMap = netServerSocketMapForSshr.get(server);
            if (socketAndProxySocketMap == null) {
                socketAndProxySocketMap = new HashMap<>();
                netServerSocketMapForSshr.put(server, socketAndProxySocketMap);
            }
            socketAndProxySocketMap.put(socketUuid, socket);

            socket.closeHandler((a) -> {
                // 通知远程客户端断开与它本地服务器的连接
                closeOneProxySocketForSshR(server, socketUuid, ctx);
                Map<String, NetSocket> socketAndProxySocketMapInner = netServerSocketMapForSshr.get(server);
                if (socketAndProxySocketMapInner == null) {
                    socketAndProxySocketMapInner = new HashMap<>();
                    netServerSocketMapForSshr.put(server, socketAndProxySocketMapInner);
                }
                socketAndProxySocketMapInner.remove(socketUuid);
            });

            socket.handler(buffer -> {
                // 通知远程客户端 有数据来了 请进行处理
                ByteString byteString = ByteString.copyFrom(buffer.getBytes());
                SshRToRemoteClientData sshRToRemoteClientData = SshRToRemoteClientData.newBuilder().setUuid(socketUuid)
                        .setLocalPort(clientLocalPort).setBytesData(byteString).setServerListenPort(listenPort).build();
                Data ssrToRemoteClientData = ProtoBufNettyMessage.Data.newBuilder()
                        .setDataType(DataType.SSHRTOREMOTECLIENTDATA).setSshRToRemoteClientData(sshRToRemoteClientData)
                        .build();
                ctx.writeAndFlush(ssrToRemoteClientData);
            });
        });

        server.exceptionHandler((e) -> {
            log.error("vuex netServer.exceptionHandler {}", e);
        });

        server.listen(listenPort, res -> {
            if (res.succeeded()) {
                log.info("Server is now listening! {}", listenPort);
                // 记录下启动了一个sshr server
                saveOneSshrNetServer(listenPort, server);
            } else {
                log.info("Failed to bind! {}", listenPort);
                closeClientSocketAndClientProxySocketForSshRAndRemoveMapWhenNetServerCloseForSshR(listenPort + "",
                        server, ctx);
            }
        });

    }

    /**
     * 记录下启动了一个sshr server
     * 
     * @param
     * @return
     * @author vwujiatong
     * @date 2021年7月17日 下午3:01:32
     * @throws
     */
    private void saveOneSshrNetServer(int listenPort, NetServer server) {
        allSshRNetServer.add(server);
        listenPortNetServerMapForSshr.put(listenPort + "", server);
        allListenPortNetServerForSshr.put(listenPort + "", server);
        Map<String, NetSocket> socketAndProxySocketMap = netServerSocketMapForSshr.get(server);
        if (socketAndProxySocketMap == null) {
            socketAndProxySocketMap = new HashMap<>();
            netServerSocketMapForSshr.put(server, socketAndProxySocketMap);
        }
    }

    private void closeClientSocketAndClientProxySocketForRinetdAndRemoveMapWhenNetServerCloseForRinetd(
            String listenPort, NetServer netServerOld) {
        Map<NetSocket, NetSocket> socketAndProxySocketMap = netServerSocketMapForRinetd.get(netServerOld);
        if (socketAndProxySocketMap != null) {
            for (NetSocket netsocket : socketAndProxySocketMap.keySet()) {
                NetSocket netSocket2 = socketAndProxySocketMap.get(netsocket);
                closeSocketAndProxySocketForRinetd(netsocket, netSocket2);
            }
        }

        try {
            netServerSocketMapForRinetd.get(netServerOld).clear();
        } catch (Exception e) {
        }
        listenPortNetServerMapForRinetd.remove(listenPort);
        netServerSocketMapForRinetd.remove(netServerOld);
    }

    private void closeClientSocketAndClientProxySocketForSshRAndRemoveMapWhenNetServerCloseForSshR(String listenPort,
            NetServer netServerOld, ChannelHandlerContext ctx) {
        Map<String, NetSocket> socketAndProxySocketMap = netServerSocketMapForSshr.get(netServerOld);
        if (socketAndProxySocketMap != null) {
            for (String uuid : socketAndProxySocketMap.keySet()) {
                NetSocket netSocket2 = socketAndProxySocketMap.get(uuid);
                closeSocketAndProxySocketForSshR(uuid, netSocket2, ctx);
            }
        }

        try {
            Map<String, NetSocket> map = netServerSocketMapForSshr.get(netServerOld);
            if (map != null) {
                map.clear();
            }
        } catch (Exception e) {
            log.error("netServerSocketMapForSshr.get(server).clear()", e);
        }
        listenPortNetServerMapForSshr.remove(listenPort);
        allListenPortNetServerForSshr.remove(listenPort);
        allSshRNetServer.remove(netServerOld);
        netServerSocketMapForSshr.remove(netServerOld);
    }

    private void closeSocketAndProxySocketForSshR(String uuid, NetSocket netSocket2, ChannelHandlerContext ctx) {
        try {
            SshRTellRemoteClientDisConnectData sshRTellRemoteClientDisConnectData = SshRTellRemoteClientDisConnectData
                    .newBuilder().setUuid(uuid).build();
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.SSHR_TELL_REMOTE_CLIENT_DISCONNECT)
                    .setSshRTellRemoteClientDisConnectData(sshRTellRemoteClientDisConnectData).build();
            ctx.writeAndFlush(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (netSocket2 != null)
                netSocket2.close();



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeSocketAndProxySocketForRinetd(NetSocket netsocket, NetSocket netSocket2) {
        try {
            if (netsocket != null)
                netsocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (netSocket2 != null)
                netSocket2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeOneProxySocketForRinetd(NetServer server, NetSocket socket) {
        Map<NetSocket, NetSocket> netServerSocketMap = netServerSocketMapForRinetd.get(server);
        if (netServerSocketMap != null) {
            NetSocket netSocketProxy = netServerSocketMap.get(socket);
            if (netSocketProxy != null) {
                try {
                    netSocketProxy.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void closeOneProxySocketForSshR(NetServer server, String uuid, ChannelHandlerContext ctx) {
        Map<String, NetSocket> netServerSocketMap = netServerSocketMapForSshr.get(server);
        if (netServerSocketMap != null) {
            NetSocket netSocket = netServerSocketMap.get(uuid);
            if (netSocket != null) {
                try {
                    netSocket.close();
                } catch (Exception e) {
                }
            }
            SshRTellRemoteClientDisConnectData sshRTellRemoteClientDisConnectData = SshRTellRemoteClientDisConnectData
                    .newBuilder().setUuid(uuid).build();
            Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.SSHR_TELL_REMOTE_CLIENT_DISCONNECT)
                    .setSshRTellRemoteClientDisConnectData(sshRTellRemoteClientDisConnectData).build();
            ctx.writeAndFlush(data);

        }
    }

    private void closeClientSocketAndProxyClient(NetServer netServer, NetSocket socket, NetClient clientProxy,
            NetSocket netSocketProxy) {
        Map<NetSocket, NetSocket> netServerSocketMap = netServerSocketMapForRinetd.get(netServer);
        if (netServerSocketMap == null) {
            netServerSocketMap = new HashMap<>();
            netServerSocketMapForRinetd.put(netServer, netServerSocketMap);
        }
        try {
            if (netSocketProxy != null)
                netSocketProxy.close();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        try {
            if (socket != null)
                socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            if (clientProxy != null)
                clientProxy.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        netServerSocketMap.remove(socket);
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
        log.info("channelActive allListenPortNetServerForSshr:{}", allListenPortNetServerForSshr);
        myChannelHandlers.add(this);
        this.ctx = ctx;
        active = true;

        // 发送给客户端一个PING
        Data data = ProtoBufNettyMessage.Data.newBuilder().setDataType(DataType.PING).build();
        ctx.writeAndFlush(data);

        System.out.println("channelActive:" + ctx.channel().remoteAddress() + " 上线");

      

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("channelInactive allListenPortNetServerForSshr:{}", allListenPortNetServerForSshr);
        myChannelHandlers.remove(this);
        active = false;
        System.out.println("channelInactive:" + ctx.channel().remoteAddress() + " 掉线");
        closeCurrentConnectSshrServer(ctx);
    }

    private void closeCurrentConnectSshrServer(ChannelHandlerContext ctx) {
        log.info("channelInactive closeCurrentConnectSshrServer(),{}", listenPortNetServerMapForSshr);
        // 关闭sshr连接的所有server
        for (String listenPort : listenPortNetServerMapForSshr.keySet()) {
            NetServer netServer = listenPortNetServerMapForSshr.get(listenPort);
            if (netServer != null) {
                allSshRNetServer.remove(netServer);
                Map<String, NetSocket> uuidNetSocketMap = netServerSocketMapForSshr.get(netServer);
                if (uuidNetSocketMap != null) {
                    for (String uuid : uuidNetSocketMap.keySet()) {
                        closeOneProxySocketForSshR(netServer, uuid, ctx);
                    }
                }
                try {
                    netServer.close((a) -> {
                        log.info("NetServer close event {}", listenPort);
                        closeClientSocketAndClientProxySocketForSshRAndRemoveMapWhenNetServerCloseForSshR(
                                listenPort + "", netServer, ctx);
                        // removeMapWhenNetServerCloseForSshR(listenPort, netServer);
                    });
                    // netServer.close();
                    log.info("channelInactive closeCurrentConnectSshrServer() netServer.close() listenPort:{}",
                            listenPort);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                allListenPortNetServerForSshr.remove(listenPort);

            }
        }
        netServerSocketMapForSshr.clear();
        listenPortNetServerMapForSshr.clear();
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
        if (active)
            ctx2.close();
    }

    public NettyProtobufServerBootstrap getNettyProtobufServerBootstrap() {
        return nettyProtobufServerBootstrap;
    }

    public void setNettyProtobufServerBootstrap(NettyProtobufServerBootstrap nettyProtobufServerBootstrap) {
        this.nettyProtobufServerBootstrap = nettyProtobufServerBootstrap;
    }

    public static Map<String, NetServer> getListenPortNetServerMapForRinetd() {
        return listenPortNetServerMapForRinetd;
    }

    public static void setListenPortNetServerMapForRinetd(Map<String, NetServer> listenPortNetServerMapForRinetd) {
        MyChannelHandler.listenPortNetServerMapForRinetd = listenPortNetServerMapForRinetd;
    }

    public static Map<NetServer, Map<NetSocket, NetSocket>> getNetServerSocketMapForRinetd() {
        return netServerSocketMapForRinetd;
    }

    public static void setNetServerSocketMapForRinetd(
            Map<NetServer, Map<NetSocket, NetSocket>> netServerSocketMapForRinetd) {
        MyChannelHandler.netServerSocketMapForRinetd = netServerSocketMapForRinetd;
    }

    public static Set<NetServer> getAllSshRNetServer() {
        return allSshRNetServer;
    }

    public static void setAllSshRNetServer(Set<NetServer> allSshRNetServer) {
        MyChannelHandler.allSshRNetServer = allSshRNetServer;
    }

    public static Set<MyChannelHandler> getMyChannelHandlers() {
        return myChannelHandlers;
    }

    public static void setMyChannelHandlers(Set<MyChannelHandler> myChannelHandlers) {
        MyChannelHandler.myChannelHandlers = myChannelHandlers;
    }

    public Map<String, NetServer> getListenPortNetServerMapForSshr() {
        return listenPortNetServerMapForSshr;
    }

    public void setListenPortNetServerMapForSshr(Map<String, NetServer> listenPortNetServerMapForSshr) {
        this.listenPortNetServerMapForSshr = listenPortNetServerMapForSshr;
    }

    public Map<NetServer, Map<String, NetSocket>> getNetServerSocketMapForSshr() {
        return netServerSocketMapForSshr;
    }

    public void setNetServerSocketMapForSshr(Map<NetServer, Map<String, NetSocket>> netServerSocketMapForSshr) {
        this.netServerSocketMapForSshr = netServerSocketMapForSshr;
    }

    public static org.slf4j.Logger getLog() {
        return log;
    }

}