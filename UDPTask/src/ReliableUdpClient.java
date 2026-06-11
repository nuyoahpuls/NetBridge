import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

//带有重传机制的UDP客户端
public class ReliableUdpClient {
    private static final int DEFAULT_TIMEOUT_MS = 300;//默认超时时间300ms
    private static final int MAX_CONNECT_ATTEMPTS = 10;//重试的最大次数10
    private static final int MAX_PACKET_ATTEMPTS = 30;//每个包的最大重传次数
    private static final int MAX_FIN_ATTEMPTS = 5;//关闭连接的最大重试次数
    //主函数
    public static void main(String[] args){
        UdpRunLogger.init("UDP CLIENT");
        if(args.length <4 || args.length > 6){
            System.out.println("参数错误/n <serverHost> <serverPort> <filePath> <studentLast4> [packetCountLimit] [timeoutMs]");
            UdpRunLogger.error("Invalid arguments");
            return;
        }

        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int studentLast4 = Integer.parseInt(args[2]);
        Path inputFile = Path.of(args[3]);
        int packetCountLimit = args.length >= 5 ? Integer.parseInt(args[4]) : -1;
        int timeoutMs = args.length == 6 ? Integer.parseInt(args[5]) : DEFAULT_TIMEOUT_MS;
        String peer = serverIP + ":" + serverPort;

        //生成
        int studentToken = UdpProtocol.makeStudentToken(studentLast4);
        try{
            InetAddress serverAddress = InetAddress.getByName(serverIP);
            byte[] fileData = Files.readAllBytes(inputFile);
            if(fileData.length ==0){
                System.out.println("输入文件没有数据，直接建立连接后关闭");
                UdpRunLogger.error("Input file is empty, no data to send");
                return;
            }
            List<DataPacketState> packets = splitIntoPackets(fileData, packetCountLimit);
            if(packets.isEmpty()){
                System.out.println("输入文件没有数据，直接建立连接后关闭");
            }
            try(DatagramSocket socket  =new DatagramSocket()){
                socket.setSoTimeout(timeoutMs);//设置超时时间
                establishConnection(socket,serverAddress,serverPort,peer,studentToken);
                TransferStats stats = transferData(socket,serverAddress,peer,serverPort,packets,studentToken);
                closeConnection(socket,serverAddress,serverPort,peer,studentToken);
                int deliveredPackets =  packets.size();
                printSummary(stats, deliveredPackets);
            }


        }catch(IOException ex){
            System.out.println("无法解析服务器地址: " + ex.getMessage());
            UdpRunLogger.error("Failed to resolve server address: " + ex.getMessage());
            return;
        }
    }

    //发送数据
    private static TransferStats transferData(DatagramSocket socket,InetAddress serverAddress,String peer,
            int serverPort,List<DataPacketState > packets,int studentToken) throws IOException{
        TransferStats stats =new TransferStats();
        int baseSeq =1;
        int nextSeq = 1;
        int totalPackes = packets.size();
        while(baseSeq <= totalPackes){
            while(nextSeq<baseSeq+UdpProtocol.WINDOW_PACKETS && nextSeq <=totalPackes){//需要满足小于当前窗口 并且小于总的次数
                sendDataPacket(socket,serverAddress,serverPort,peer,studentToken,packets.get(nextSeq-1),
                        stats,false);
                nextSeq++;
            }
            try{
                UdpProtocol.Packet response = receive(socket);
                UdpRunLogger.received(peer,response.type,response.seq,response.ack,
                        response.byteStart,response.byteEnd,response.packetLength());
                if(response.type == UdpProtocol.TYPE_ACK){
                    //需要判断ack和total
                    int ack = Math.min(response.ack,totalPackes);
                    String serverTime =UdpProtocol.payloadAsAscii(response.payload);//服务器返回的时间戳，转为字符串
                    if(ack >=baseSeq){
                        long now = System.nanoTime();
                        for(int seq = baseSeq;seq<=ack;seq++){
                            DataPacketState packet = packets.get(seq-1);
                            if(!packet.acked){
                                packet.acked = true;
                                double rttMs = (now - packet.lastSentTime) / 1_000_000.0;//计算RTT
                                stats.rtts.add(rttMs);
                                System.out.printf("收到ACK，确认第 %d 个（%d~%d 字节）数据包，RTT=%.3f ms，服务器时间=%s%n",
                                        packet.seq, packet.byteStart, packet.byteEnd, rttMs, serverTime);

                            }
                        }
                        while(baseSeq<=totalPackes && packets.get(baseSeq-1).acked){
                            baseSeq++;//移动窗口
                        }
                    }
                }else if(response.type == UdpProtocol.TYPE_REJECT) {
                    String message = new String(response.payload);
                    System.out.println("连接被拒绝: " + message);
                    throw new IOException("连接被拒绝: " + message);
                }
            }catch (SocketTimeoutException e){//超出时间
                UdpRunLogger.timeout(peer,baseSeq,nextSeq);
                System.out.println("等待ACK超时，重传窗口内的未确认数据包...");
                for(int seq = baseSeq;seq<nextSeq;seq++){
                    DataPacketState packet = packets.get(seq-1);
                    if(!packet.acked){//如果没有被确认
                        if(packet.attempts >= MAX_PACKET_ATTEMPTS){
                            throw new IOException("数据包 " + packet.seq + " 重传次数超过 " + MAX_PACKET_ATTEMPTS);
                        }
                        sendDataPacket(socket,serverAddress,serverPort,peer,studentToken,packet,
                                stats,true);
                    }
                }
            }
        }

        return stats;
    }
    private static void establishConnection (DatagramSocket socket,InetAddress serverAddress,
            int serverPort,String peer,int studentToken) throws IOException{
        byte [] connectData= UdpProtocol.buildPacket(UdpProtocol.TYPE_CONNECT, studentToken,
                0,0,0,0,new byte[0]);
        for(int i = 1;i<=MAX_CONNECT_ATTEMPTS;i++){
            send(socket,connectData,serverAddress,serverPort);
            UdpRunLogger.sent(peer,UdpProtocol.TYPE_CONNECT,0,0,0,0,connectData.length);
            System.out.println("发送连接请求，等待服务器响应... 第 " + i + " 次");
            try{
                UdpProtocol.Packet response = receive(socket);
                if(response.type == UdpProtocol.TYPE_CONNECT_ACK){
                    UdpRunLogger.received(peer,response.type,response.seq,response.ack,
                            response.byteStart,response.byteEnd,response.packetLength());
                    System.out.println("收到连接确认，连接已建立");
                    return;
                }
                if(response.type == UdpProtocol.TYPE_REJECT){
                    UdpRunLogger.received(peer,response.type,response.seq,response.ack,
                            response.byteStart,response.byteEnd,response.packetLength());
                    String message = new String(response.payload);
                    System.out.println("连接被拒绝: " + message);
                    return;
                }
                UdpRunLogger.info("收到非连接确认包，继续等待... type=" + response.type);
            }catch(SocketTimeoutException e){
                UdpRunLogger.timeout(peer,0,0);
                System.out.println("等待连接响应超时");
            }
        }
        throw new IOException("连接失败，超过最大重试次数 " + MAX_CONNECT_ATTEMPTS);
    }

    private static void closeConnection(DatagramSocket socket, InetAddress serverAddress,
            int serverPort, String peer, int studentToken) throws IOException {
        byte[] finData = UdpProtocol.buildPacket(UdpProtocol.TYPE_FIN,
                studentToken, 0, 0, 0, 0, new byte[0]);

        for (int attempt = 1; attempt <= MAX_FIN_ATTEMPTS; attempt++) {
            send(socket, finData, serverAddress, serverPort);
            UdpRunLogger.sent(peer, UdpProtocol.TYPE_FIN, 0, 0, 0, 0, finData.length);
            System.out.println("发送FIN包，等待服务器响应... 第 " + attempt + " 次");


            while (true) {
                try {
                    UdpProtocol.Packet response = receive(socket);
                    if (response.type == UdpProtocol.TYPE_FIN_ACK) {
                        UdpRunLogger.received(peer, response.type, response.seq, response.ack,
                                response.byteStart, response.byteEnd, response.packetLength());
                        System.out.println("收到FIN_ACK包，连接已关闭");
                        return;
                    }
                    UdpRunLogger.info("收到非FIN_ACK包，继续等待... type=" + response.type);
                } catch (SocketTimeoutException e) {
                    UdpRunLogger.timeout(peer, 0, 0);
                    System.out.println("等待FIN_ACK包超时，准备重传FIN");
                    break;
                }
            }
        }
        UdpRunLogger.info("FIN 等待次数 " + MAX_FIN_ATTEMPTS + " attempts, client closes local socket");
        System.out.println("多次等待FIN_ACK超时，客户端直接关闭本地socket");
    }


    private static void sendDataPacket(DatagramSocket socket, InetAddress serverAdddress ,
        int serverPort, String peer , int studentToken, DataPacketState packet
            , TransferStats stats ,boolean retransmit)throws IOException{
        byte [ ]data = UdpProtocol.buildPacket(UdpProtocol.TYPE_DATA,studentToken,packet.seq,0,
                packet.byteStart,packet.byteEnd,packet.payload);
        send (socket,data,serverAdddress,serverPort);
        packet.lastSentTime = System.nanoTime();//记录发送的时间戳
        packet.attempts++;
        stats.totalTransmissions++;
        if(retransmit){
            stats.retransmissions++;
            UdpRunLogger.retransmit(peer, packet.seq, packet.byteStart, packet.byteEnd, data.length);
            System.out.printf("重传第 %d 个（第 %d~%d 字节）数据包%n", packet.seq, packet.byteStart, packet.byteEnd);
        }else{
            UdpRunLogger.sent(peer, UdpProtocol.TYPE_DATA, packet.seq, 0, packet.byteStart, packet.byteEnd, data.length);
            System.out.printf("发送第 %d 个（第 %d~%d 字节）数据包%n", packet.seq, packet.byteStart, packet.byteEnd);
        }
    }
    //发送数据
    private static void send(DatagramSocket socket , byte[] data, InetAddress address , int port) throws IOException{
        DatagramPacket packet = new DatagramPacket(data,data.length,address,port);
        socket.send(packet);
    }

    //接受数据
    private static UdpProtocol.Packet receive (DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[UdpProtocol.MAX_PACKET_SIZE];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        socket.receive(datagram);
        return UdpProtocol.parsePacket(datagram.getData(), datagram.getLength());
    }
    //拆分数据
    private static List<DataPacketState> splitIntoPackets (byte[] data , int packetCountLimit){
        List<DataPacketState> packets =new ArrayList<>();
        int totalPackets = (data.length + UdpProtocol.PAYLOAD_SIZE - 1)/UdpProtocol.PAYLOAD_SIZE;//
        if(packetCountLimit>0){
            totalPackets = Math.min(totalPackets, packetCountLimit);//限制报文的数量
        }
        int byteStart = 0;
        int byteEnd = 0;
        for(int i =1; i<= totalPackets ;i++){
            //复制
            byteEnd = Math.min(byteStart + UdpProtocol.PAYLOAD_SIZE-1, data.length-1);//计算当前包的结束位置
            int payloadLength = byteEnd - byteStart + 1;//计算当前包的有效数据长度
            byte[] payload = new byte[payloadLength];
            System.arraycopy(data,byteStart,payload,0,payloadLength);
            DataPacketState state = new DataPacketState(i,byteStart+1,byteEnd+1,payload);
            packets.add(state);
            byteStart = byteEnd+1;//下一个包的起始位置
        }
        return packets;
    }
    //打印传输统计信息
    private static void printSummary(TransferStats stats, int deliveredPackets) {

        double lossRate = stats.totalTransmissions == 0 ? 0.0 : stats.retransmissions * 100.0 / stats.totalTransmissions;

        System.out.println("\n[Summary]");
        System.out.printf("Delivered UDP packets: %d%n", deliveredPackets);//成功交付的UDP包数量
        System.out.printf("Actual sent UDP packet number: %d%n", stats.totalTransmissions);//实际发送的UDP包数量，包括重传
        System.out.printf("Retransmissions: %d%n", stats.retransmissions);//重传的次数
        System.out.printf("Loss rate: %.2f%%%n", lossRate);

        if (stats.rtts.isEmpty()) {
            System.out.println("No RTT samples.");
            return;
        }

        double min = stats.rtts.get(0);
        double max = stats.rtts.get(0);
        double sum = 0.0;

        for (double rtt : stats.rtts) {
            min = Math.min(min, rtt);
            max = Math.max(max, rtt);
            sum += rtt;
        }

        double avg = sum / stats.rtts.size();//平均RTT
        double varianceSum = 0.0;
        for (double rtt : stats.rtts) {
            double diff = rtt - avg;
            varianceSum += diff * diff;//计算RTT的方差总和
        }
        double std = Math.sqrt(varianceSum / stats.rtts.size());

        System.out.printf("Max RTT: %.3f ms%n", max);
        System.out.printf("Min RTT: %.3f ms%n", min);
        System.out.printf("Average RTT: %.3f ms%n", avg);
        System.out.printf("RTT standard deviation: %.3f ms%n", std);

        UdpRunLogger.info("Summary deliveredPackets=" + deliveredPackets
                + ", totalTransmissions=" + stats.totalTransmissions
                + ", retransmissions=" + stats.retransmissions
                + ", lossRate=" + String.format("%.2f%%", lossRate)
                + ", maxRtt=" + String.format("%.3f", max)
                + ", minRtt=" + String.format("%.3f", min)
                + ", avgRtt=" + String.format("%.3f", avg)
                + ", stdRtt=" + String.format("%.3f", std));
    }

    //记录每个数据包的状态
    private static class DataPacketState{
        private final int seq;
        private final  int byteStart;
        private final int byteEnd;
        private final byte[] payload;
        private long lastSentTime; //记录上次发送的时间戳
        private int attempts;
        private boolean acked;

        public DataPacketState(int seq, int byteStart, int byteEnd, byte[] payload) {
            this.seq = seq;
            this.byteStart = byteStart;
            this.byteEnd = byteEnd;
            this.payload = payload;
        }
    }

    public static class TransferStats {
        private final List<Double> rtts = new ArrayList<>();//记录额每个包的RTT
        private int totalTransmissions  ;//记录发送的总次数，包括重传
        private int retransmissions  ;//记录重传的次数
    }
}
