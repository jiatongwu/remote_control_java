package cn.xvkang.rc_java_common.nettyProtobuf.client.command;

import java.util.HashMap;
import java.util.Map;

import cn.xvkang.rc_java_common.nettyProtobuf.client.NettyProtobufClientBootstrap;
import lombok.Data;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "Greet", header = "%n@|green SendCommandToServerCommand|@")
public class SendCommandToServerCommand implements Runnable {
    private NettyProtobufClientBootstrap nettyProtobufClientBootstrap;
    public static Map<Integer, ListenPortToIpToPortPojo> rinetdPortMap = new HashMap<>();
    public static Map<Integer, ListenPortToIpToPortPojo> sshRPortMap = new HashMap<>();

    @Data
    public static class ListenPortToIpToPortPojo {
        String type;
        Integer listenport;
        String toip;
        Integer toport;

    }

    public SendCommandToServerCommand() {
    }

    public SendCommandToServerCommand(NettyProtobufClientBootstrap nettyProtobufClientBootstrap) {
        this.nettyProtobufClientBootstrap = nettyProtobufClientBootstrap;
    }

    @Option(names = { "-t",
            "--type" }, required = true, description = "comand type . rinetd sshr stopAllRinetd stopAllSshR listListenPorts quit")
    String type;
    @Option(names = { "-l", "--listenport" }, required = false, description = "listen port")
    Integer listenport;
    @Option(names = { "-r", "--toip" }, required = false, description = "rinetd toip")
    String toip;
    @Option(names = { "-p", "--toport" }, required = false, description = "rinetd toport or sshr localport")
    Integer toport;

    public void run() {
        //ObjectMapper om = new ObjectMapper();
        if ("rinetd".equals(type)) {
            boolean createOneRinetd = nettyProtobufClientBootstrap.createOneRinetd(listenport, toip, toport);
            if (createOneRinetd) {
                // 记录下来rinetd
                ListenPortToIpToPortPojo listenPortToIpToPortPojo = new ListenPortToIpToPortPojo();
                listenPortToIpToPortPojo.setType("rinetd");
                listenPortToIpToPortPojo.setListenport(listenport);
                listenPortToIpToPortPojo.setToip(toip);
                listenPortToIpToPortPojo.setToport(toport);
                rinetdPortMap.put(listenport, listenPortToIpToPortPojo);
                // 将这些记录到本地系统文件
                // writeRinetdPortToFile(om);
            }
        } else if ("sshr".equals(type)) {
            boolean createSshR = nettyProtobufClientBootstrap.createSshR(listenport, toport);
            if (createSshR) {
                // 记录下来sshr
                ListenPortToIpToPortPojo listenPortToIpToPortPojo = new ListenPortToIpToPortPojo();
                listenPortToIpToPortPojo.setType("sshr");
                listenPortToIpToPortPojo.setListenport(listenport);
                listenPortToIpToPortPojo.setToport(toport);
                sshRPortMap.put(listenport, listenPortToIpToPortPojo);
                // 将这些记录到本地系统文件
                // writeSshrPortToFile(om);
            }
        } else if ("stopAllRinetd".equals(type)) {
            boolean stopAllRinetdListenServer = nettyProtobufClientBootstrap.stopAllRinetdListenServer();
            if (stopAllRinetdListenServer) {
                rinetdPortMap.clear();
                // 将这些记录到本地系统文件
                // writeRinetdPortToFile(om);
            }
        } else if ("stopAllSshR".equals(type)) {
            boolean stopAllSshRListenServer = nettyProtobufClientBootstrap.stopAllSshRListenServer();
            if (stopAllSshRListenServer) {
                sshRPortMap.clear();
                // 将这些记录到本地系统文件
                // writeSshrPortToFile(om);
            }
        } else if ("listListenPorts".equals(type)) {
            nettyProtobufClientBootstrap.getAllRinetdAndSShrListenPort();
        } else if ("quit".equals(type)) {
            rinetdPortMap.clear();
            sshRPortMap.clear();
            nettyProtobufClientBootstrap.stop();
        }
    }

//    private void writeSshrPortToFile(ObjectMapper om) {
//        try {
//            File rinetdFile = new File(SorClient.sshRFile);
//            FileUtils.write(rinetdFile, om.writeValueAsString(sshRPortMap), Charset.forName("UTF-8"), false);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void writeRinetdPortToFile(ObjectMapper om) {
//        try {
//            File rinetdFile = new File(SorClient.rinetdFile);
//            FileUtils.write(rinetdFile, om.writeValueAsString(rinetdPortMap), Charset.forName("UTF-8"), false);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

}
