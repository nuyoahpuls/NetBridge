import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.*;

public class ReliableUdpServer {
    private static final DateTimeFormatter SERVER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH-mm-ss");

    public static void main(String [] args){
        UdpRunLogger.init("UDP SERVER");
        if(args.length <2 || args.length >3){
            System.out.println("缺少参数/n <serverPort> <lossRate> [randomSeed]");//模拟丢包
            UdpRunLogger.error("Invalid arguments");
            return;
        }

        int port = Integer.parseInt(args[0]);
        double lossRate = Double.parseDouble(args[1]);
        long randomSeed = args.length == 3 ? Long.parseLong(args[2]) : System.currentTimeMillis();

        if(lossRate <0.0 || lossRate >1.0){
            System.out.println("丢包率必须在 0.0-1.0 范围内");
            UdpRunLogger.error("Invalid lossRate=" + lossRate);
            return;
        }
        Random random = new Random(randomSeed);
        Map<String,Session> sessions = new HashMap<>();//维持客户端会话状态
        try(DatagramSocket socket = new DatagramSocket(port)){
            System.out.println("UDP SERVER 开始在端口 " + port + " 上运行，丢包率=" + lossRate);
            UdpRunLogger.init("UDP SERVER started on port " + port + ", lossRate=" + lossRate + ", randomSeed=" + randomSeed);

            while(true){

                byte[] receiveBuffer =new byte[UdpProtocol.MAX_PACKET_SIZE];//接受数据缓冲区

                DatagramPacket datagram  = new DatagramPacket(receiveBuffer,receiveBuffer.length);


                //接受数据报
                socket.receive(datagram);
                String peer = peerOf(datagram.getAddress(), datagram.getPort());
                UdpProtocol.Packet packet = UdpProtocol.parsePacket(datagram.getData(), datagram.getLength());//解析数据报
                UdpRunLogger.received(peer, packet.type, packet.seq, packet.ack, packet.byteStart, packet.byteEnd, packet.packetLength());

                if(packet.type == UdpProtocol.TYPE_CONNECT) {
                    handleConnect(socket, peer, packet, sessions, datagram.getAddress(), datagram.getPort());
                }else if(packet.type == UdpProtocol.TYPE_DATA) {
                    handleData(socket, datagram.getAddress(), datagram.getPort(), random, lossRate, packet, peer, sessions);
                }else if(packet.type == UdpProtocol.TYPE_FIN) {
                    handleFin(socket, sessions, datagram.getAddress(), datagram.getPort(), peer);
                }else{
                    System.out.println("未知的报文类型: " + packet.type + " from " + peer);
                    UdpRunLogger.error("Unknown packet type: " + packet.type + " from " + peer);
                }
            }
        }catch (IOException e){
            System.out.println("服务器发生错误: " + e.getMessage());
            UdpRunLogger.error("Server error: " + e.getMessage());
        }
    }


    private static void handleConnect(DatagramSocket socket , String peer, UdpProtocol.Packet packet
    ,Map<String,Session> sessions,InetAddress address, int port) throws IOException {
        if(!UdpProtocol.isValidStudentToken(packet.studentToken)){
            System.out.println("无效的学生令牌，拒绝连接: " + peer);
            byte[] payload = UdpProtocol.asciiPayload("Invalid student token");
            byte[] response = UdpProtocol.buildPacket(UdpProtocol.TYPE_REJECT, packet.studentToken, 0, 0,
                    0, 0, payload);
            send(socket, address, port,response);
            UdpRunLogger .sent(peer,UdpProtocol.TYPE_REJECT,0,0,0,0,0);
            return;
        }
        int studentLast4 = UdpProtocol.decodeStudentLast4(packet.studentToken);
        Session session = new Session(studentLast4);
        sessions.put(peer,session);

        byte[] payload= UdpProtocol.asciiPayload("Welcome, student " + packet.studentToken);
        byte[] response = UdpProtocol.buildPacket(UdpProtocol.TYPE_CONNECT_ACK, packet.studentToken, 0, 0, 0, 0, payload);
        send(socket, address, port,response);
        UdpRunLogger.sent(peer,UdpProtocol.TYPE_CONNECT_ACK,0,0,0,0,response.length);
        System.out.println("客户端连接成功: " + peer + " (studentToken=" + packet.studentToken + ")");
    }

    private static void handleData(DatagramSocket socket,InetAddress address ,int port,Random random
            ,double lossRate,UdpProtocol.Packet packet,String peer,
            Map<String ,Session> sessions) throws IOException {
        Session session = sessions.get(peer);
        if(session == null){
            byte[] response = UdpProtocol.buildPacket(UdpProtocol.TYPE_REJECT,packet.studentToken,0,0,0,
                    0,UdpProtocol.asciiPayload("No session"));
            System.out.println("客户端与服务端未建立链接，丢弃数据包: " + peerOf(address, port));
            return;
        }
        if(random.nextDouble() < lossRate){
            UdpRunLogger.dropped(peer,packet.type,packet.seq,packet.ack,packet.byteStart,packet.byteEnd,packet.packetLength());
            System.out.println("模拟丢包: " + packet.seq + " from " + peer + " is ignored");
             return;
        }
        if(packet.seq >= session.expectedSeq){//如果收到的数据报序号大于期望的序号，说明有数据丢失
            session.receivedSeqs.add(packet.seq);
            while(session.receivedSeqs.contains(session.expectedSeq) ){
                session.expectedSeq++;
            }
        }

        //ack确认
        int cumulativeAck  = session.expectedSeq-1;
        byte[] payload = UdpProtocol.asciiPayload(LocalTime.now().format(SERVER_TIME_FORMAT));
        byte[] response = UdpProtocol.buildPacket(UdpProtocol.TYPE_ACK,packet.studentToken,0,cumulativeAck,
                packet.byteStart,packet.byteEnd,payload);
        send(socket,address,port,response);
        UdpRunLogger.sent(peer,UdpProtocol.TYPE_ACK,0,cumulativeAck,packet.byteStart,packet.byteEnd,response.length);
        System.out.println("收到数据包: " + packet.seq + " (" + packet.byteStart + "-" + packet.byteEnd + " bytes), ack=" + cumulativeAck);

    }
    private  static void handleFin(DatagramSocket socket, Map<String ,Session> sessions ,
                                   InetAddress address, int port,String peer) throws IOException {
        sessions.remove(peer);
        byte [] response  = UdpProtocol.buildPacket(UdpProtocol.TYPE_FIN_ACK,0,0,0,
                0,0,UdpProtocol.asciiPayload("BYE"));
        DatagramPacket responsePacket = new DatagramPacket(response,response.length,address,port);
        socket.send(responsePacket);
        UdpRunLogger.sent(peer,UdpProtocol.TYPE_FIN_ACK,0,0,0,0,response.length);
        System.out.println("客户端已完成传送，服务器发送fin-ack: " + peer);
    }

    private static void send(DatagramSocket socket,InetAddress address ,
                             int port, byte[] data) throws IOException {
        DatagramPacket response = new DatagramPacket(data,data.length,address,port);
        socket.send(response);
    }
    private static String peerOf(InetAddress address, int port) {
        return address.getHostAddress() + ":" + port;
    }
    public static class Session {
        public int studentLast4 ;
        public Set<Integer> receivedSeqs = new HashSet<>();
        public  int expectedSeq = 1;
        public Session(int studentLast4) {
            this.studentLast4 = studentLast4;
        }
    }
}
