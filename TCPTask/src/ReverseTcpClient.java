import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
public class ReverseTcpClient {
    public static void main(String[] args) {
        RunLogger.init("TCP CLIENT");
        if(args.length < 7){
            System.out.println("需要如下参数 <serverIP> <serverPort> <inFile> <outFile> <Lmin> <Lmax> <chunkSeed>");
            RunLogger.error("Invalid arguments");
            return;
        }
        //获取到参数
        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String peer = serverIP + ":" + serverPort;
        Path inputFile = Path.of(args[2]);
        //输出文件路径 需要根据客户端的ip和端口号重命名
        Path outputFile = Path.of(args[3]+"_" + serverIP.replace(".","_") + "_" + serverPort + ".out");

        int lmin = Integer.parseInt(args[4]);
        int lmax = Integer.parseInt(args[5]);
        long chunkSeed = Long.parseLong(args[6]);

        //参数检查
        if(lmin < 0 || lmax<lmin){
            System.out.println("Lmin、Lmax数据范围不正确，需要: 0 < Lmin <= Lmax");
            RunLogger.error("Invalid Lmin/Lmax. Lmin=" + lmin + ", Lmax=" + lmax);
            return;
        }
        try{
            byte[] fileData = Files.readAllBytes(inputFile);
            ArrayList<byte[]> blocks = splitIntoBlocks(fileData, lmin, lmax, chunkSeed);
            RunLogger.info("Input file= " + inputFile + ", output file= " + outputFile
                    + ", file bytes= " + fileData.length + ", Lmin= " + lmin + ", Lmax= " + lmax + ", chunkSeed= " +
                    chunkSeed + ", block count= " + blocks.size());
            System.out.println("Input file: " + inputFile);
            System.out.println("Output file: " + outputFile);
            int N = blocks.size();
            //建立连接并发送数据
            try(Socket socket = new Socket(serverIP,serverPort)){
                //连接
                System.out.println("成功连接服务器 " + peer);
                RunLogger.info("Connected to server " + peer);

                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                //发送初始化报文
                Protocol.sendInitialization(out, N);
                RunLogger.sent(peer, Protocol.TYPE_INITIALIZATION, Protocol.INIT_PACKET_SIZE);
                System.out.println("发送Initialization报文, N = " + N);

                //接受同意报文
                Protocol.readAgree(in);
                RunLogger.received(peer, Protocol.TYPE_AGREE, Protocol.AGREE_SIZE);
                System.out.println("从服务端接受到Agree报文");

                //循环发送数据块
                ArrayList<byte[]> reversedBlocks = new ArrayList<>();//保存反转后的数据块
                ByteArrayOutputStream reversedWholeFile = new ByteArrayOutputStream();//保存从服务器接收的反转数据
                for(int i=0;i<N;i++) {
                    byte[] block  = blocks.get(i);//获取到当前数据块
                    Protocol.sendReverseRequest(out,block);
                    RunLogger.sent(peer, Protocol.TYPE_REVERSE_REQUEST, Protocol.DATA_HEADER_SIZE + block.length);
                    System.out.println("发送第 " + (i + 1) + ", length = " + block.length);

                    byte[] reversedBlock = Protocol.readReverseAnswer(in);//接受反转后的数据块
                    RunLogger.received(peer, Protocol.TYPE_REVERSE_RESPONSE, Protocol.DATA_HEADER_SIZE + reversedBlock.length);
                    reversedBlocks.add(reversedBlock);//保存反转后的数据块到列表中
                    String reverseText= new String(reversedBlock, StandardCharsets.US_ASCII);
                    System.out.println("收到第" + (i + 1) + "块, length = " + reversedBlock.length+" "+reverseText);
                }
                //把反转后的数据块写入输出文件
                for(int i= reversedBlocks.size() - 1; i>=0; i--){
                    reversedWholeFile.write(reversedBlocks.get(i));
                }
                Files.write(outputFile, reversedWholeFile.toByteArray());
                RunLogger.info("Output written to " + outputFile + ", bytes=" + reversedWholeFile.size());//写入
                System.out.println("输出文件到: " + outputFile);
            }catch(IOException ex){
                System.out.println("连接服务端失败: " + ex.getMessage());
                RunLogger.error("Failed to connect to server: " + ex.getMessage());
                return;
            }

        }catch (IOException ex){
            System.out.println("文件读取失败 : " + ex.getMessage());
            RunLogger.error("Failed to read input file: " + ex.getMessage());
            ex.printStackTrace();
        }finally {
            System.out.println("客户端结束");
            RunLogger.info("客户端结束");
        }
    }


    //分割数据块
    private static ArrayList<byte[]> splitIntoBlocks(byte[] data ,int lmin,int lmax, long seed){
        ArrayList<byte[]> blocks = new ArrayList<>();
        Random random = new Random(seed);
        int offset = 0;
        while(offset <data.length){
            int remaining = data.length - offset;
            if(remaining<=0){
                break;
            }
            int blocklength = random.nextInt(lmax - lmin + 1) + lmin;
            if(remaining < lmax){
                blocklength = remaining;
            }
            byte[] block = new byte[blocklength];
            System.arraycopy(data,offset,block,0,blocklength);
            blocks.add(block);
            offset += blocklength;
        }
        return blocks;
    }
}
