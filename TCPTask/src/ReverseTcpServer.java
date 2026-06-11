import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ReverseTcpServer {
    public static void main(String[] args){
        RunLogger.init("TCP SERVER");
        if(args.length!=1){
            System.out.println("服务端需要参数 <serverPort>");
            RunLogger.error("Invalid arguments");
            return;
        }
        int port=  Integer.parseInt(args[0]);
        try(ServerSocket socket =  new ServerSocket(port)){
            System.out.println("服务器启动，监听端口:" + port);
            RunLogger.info("Server started on port " + port);//记录日志
            while(true){
                try{
                    Socket clientSocket  = socket.accept();//等待客户端连接
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    RunLogger.info("Client connected: " + clientSocket.getRemoteSocketAddress());
                    new Thread(clientHandler).start();
                }catch(IOException ex){
                    ex.printStackTrace();
                    RunLogger.error("Error accepting client connection: " + ex.getMessage());
                    continue;
                }
            }
        }catch (IOException ex){
            ex.printStackTrace();
             RunLogger.error("Server error: " + ex.getMessage());
        }
    }

    //新建一个线程类处理每个客户端连接
    private static class ClientHandler implements Runnable {
        private Socket socket ;
        public ClientHandler(Socket clientSocket) {
            this.socket = clientSocket;//获取客户端套接字

        }
        @Override
        public void run(){
            //读取客户端发送的数据
            try(Socket clientSocket =socket;
                DataInputStream in =new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())){
                //处理客户端请求
                String peer= clientSocket.getRemoteSocketAddress().toString();
                RunLogger.info("Client connected: " + peer);
                //接受初始化报文，获取块的数量
                int blockCount = Protocol.readInitialization(in);
                RunLogger.received(peer, Protocol.TYPE_INITIALIZATION, Protocol.INIT_PACKET_SIZE);
                if(blockCount < 0){
                    RunLogger.error("Invalid block count received: " + blockCount);
                    System.out.println("收到的块数量无效: " + blockCount);
                    return;
                }
                System.out.println("收到Initialization报文, block count = " + blockCount);
                //发送同意报文
                Protocol.sendAgree(out);
                RunLogger.sent(peer, Protocol.TYPE_AGREE, Protocol.AGREE_SIZE);
                System.out.println("发送Agree报文");

                //接受数据块
                for(int i=0;i<blockCount;i++) {
                    byte[] data = Protocol.readReverseRequest(in);//接受到数据
                    RunLogger.received(peer,Protocol.TYPE_REVERSE_REQUEST,Protocol.DATA_HEADER_SIZE + data.length);
                    System.out.println("收到ReverseRequest " + (i + 1) + ", length = " + data.length);

                    byte[] reverseData = reverseBytes(data);//反转数据
                    Protocol.sendReverseAnswer(out, reverseData);//发送反转响应报文，包含反转后的数据
                    RunLogger.sent(peer, Protocol.TYPE_REVERSE_RESPONSE, Protocol.DATA_HEADER_SIZE + reverseData.length);
                    System.out.println("发送ReverseResponse " + (i + 1) + ", length = " + reverseData.length);
                }
                //反转数据
                System.out.println("客户端处理完成: " + peer);
                RunLogger.info("Client finished: " + peer);

            }catch (IOException ex){
                ex.printStackTrace();
                 RunLogger.error("Client handler error: " + ex.getMessage());
            }
        }

        private byte[] reverseBytes(byte[] data) {
            byte[] result = new byte[data.length];
            for(int i=0;i<data.length;i++) {
                result[i] = data[data.length - 1 - i];
            }
            return result;
        }
    }

}
