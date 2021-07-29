package cn.xvkang.rc_java_common.nettyProtobuf.server;

import java.util.HashMap;
import java.util.Map;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 每个账号对应一个监听端口 每个监听端口对应一个ZeusServerBootstrap 对象 每个监听端口对应一个obfs-server外部进程
 * 
 * @author macos
 *
 */
@Slf4j
@Data
public class NettyProtobufServerBootstrap {

    public static final Map<Integer, NettyProtobufServerBootstrap> portMap = new HashMap<>();

    private ServerBootstrap serverBootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    // private String password;
    private Integer port;
    private MyChannelInitializer myChannelInitializer;

    //private Map<Integer, NetServer> listenPortNetServerMapForRinetd = new HashMap<>();
    //private Map<Integer, NetServer> listenPortNetServerMapForSshR = new HashMap<>();
    // private Process process;
//	/**
//	 * 数据库account表主键
//	 */
//	private Integer accountId;

    // private static final InternalLogger logger =
    // InternalLoggerFactory.getInstance(ZeusServerBootstrap.class);

//	private static final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
//
//	private static final EventLoopGroup workerGroup = new NioEventLoopGroup();
//
//	private static final ServerBootstrap serverBootstrap = new ServerBootstrap();
//
//	private static final ZeusServerBootstrap zeusServerBootstrap = new ZeusServerBootstrap();

//	public static ZeusServerBootstrap getInstance() {
//		return zeusServerBootstrap;
//	}

    public void start(Integer port) throws Exception {
        // final Config config = ConfigLoader.load(configPath);
        // logger.info("load {} config file success", configPath);
        // for (Map.Entry<Integer, String> portPassword :
        // config.getPortPassword().entrySet()) {
        // this.accountId = accountId;
        this.port = port;

//		String obfsServerCommand = obfsServerPath + " -p " + obfsServerPort + " --obfs tls -r 127.0.0.1:" + port;
//		Runtime runtime = Runtime.getRuntime();
//		process = runtime.exec(obfsServerCommand);
//
//		this.password = password;
        start0(port);

        // start0(7778, "wujiatong", "aes-256-cfb");
        // }
    }

//	private static final Logger LOGGER = LoggerFactory.getLogger(ServerBootstrapConfiguration.class);
//    // region Dependencies
//    @Autowired
//    private NioEventLoopGroup bossGroup;
//
//    @Autowired
//    private NioEventLoopGroup workerGroup;

    // @Autowired
    // private ChannelInitializer<SocketChannel> channelInitializer;
//    @Bean
//    public ChannelInitializer<SocketChannel> channelInitializer() {
//        return new MyChannelInitializer();
//    }

    // endregion

    // region Public methods
//    @Bean(name = "serverBootstrap")
//    public ServerBootstrap serverBootstrap() {
//        LOGGER.debug("Bootstrapping websocket server...");
//        return new ServerBootstrap().group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.DEBUG)).childHandler(channelInitializer())
//               .childOption(ChannelOption.SO_KEEPALIVE, true);
//    }

    private void start0(Integer port) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(10);
        workerGroup = new NioEventLoopGroup(50);
        serverBootstrap = new ServerBootstrap();
        myChannelInitializer = new MyChannelInitializer(this);
        serverBootstrap.group(bossGroup, workerGroup)// .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.TCP_NODELAY, true)
                .channel(NioServerSocketChannel.class).childHandler(myChannelInitializer);
        ChannelFuture future = serverBootstrap.bind(port).sync();
        serverChannel = future.channel();
        log.info("NettyProtobufServerBootstrap server [TCP] running at {}", port);
        portMap.put(port, this);
        // future.channel().closeFuture().sync();
    }

    public void stop() {
//        try {
//            process.destroy();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        portMap.remove(port);
        ChannelFuture channelFuture = serverChannel.close();
        channelFuture.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<? super Void> future) throws Exception {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();

            }
        });
    }
    public static void main(String[] args) throws Exception {
        new NettyProtobufServerBootstrap().start(65534);
    }

}
