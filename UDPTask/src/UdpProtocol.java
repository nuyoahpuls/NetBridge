//udp的协议
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
//定义报文格式
//解析报文
//封装报文
public class UdpProtocol {
    private static final  short MAGIC = (short) 0x4A57;//判断是不是当前报文
    public static final byte VERSION = 1;//协议版本
    public static final byte TYPE_CONNECT = 1;//链接请求
    public static final byte TYPE_CONNECT_ACK = 2;//连接请求确认
    public static final byte TYPE_DATA = 3;//带有数据
    public static final byte TYPE_ACK = 4;//数据确认
    public static final byte TYPE_FIN = 5;//连接结束
    public static final byte TYPE_FIN_ACK = 6;//连接结束确认
    public static final byte TYPE_REJECT = 7;//连接拒绝

    public static final int STUDENT_XOR_MASK = 0x5A3C;//学生ID异或掩码
    public static final int PAYLOAD_SIZE = 80;//每个数据包的大小
    public static final int WINDOW_SIZE_BYTES = 400;//滑动窗口大小
    public static final int WINDOW_PACKETS = WINDOW_SIZE_BYTES / PAYLOAD_SIZE;//滑动窗口的包数

    public static final int HEADER_SIZE = 24;//固定首部大小
    public static final int MAX_PACKET_SIZE = HEADER_SIZE + PAYLOAD_SIZE ;//最大报文大小，预留128字节的payload

    //工具类
    private UdpProtocol() {
    }

    public static int makeStudentToken (int studentLast4) {
        if (studentLast4 < 0 || studentLast4 > 9999) {
            throw new IllegalArgumentException("学生学号必须在 0-9999 范围内");
        }
        return (studentLast4 ^ STUDENT_XOR_MASK ) & 0xFFFF;
    }

    //解码
    public static  int decodeStudentLast4 (int token) {
        return (token ^ STUDENT_XOR_MASK) & 0xFFFF;
    }

    //校验token
    public static boolean isValidStudentToken(int token) {
        int studentLast4 = decodeStudentLast4(token);
        return studentLast4 >= 0 && studentLast4 <= 9999;
    }

    //封装报文
    public static byte[] buildPacket(byte type ,int studentToken ,int seq,int ack , int byteStart
            ,int byteEnd ,byte[] payload ){
        if(payload == null){
            payload =new byte[0];//允许payload为null，表示没有数据
        }
        if(payload.length> PAYLOAD_SIZE){//数据的长度不能超过规定的大小
            throw new IllegalArgumentException("payload太大，最大只能是 " + PAYLOAD_SIZE + " 字节");
        }
        ByteBuffer buffer  = ByteBuffer.allocate(HEADER_SIZE + payload.length);//头部 + 数据的长度
        buffer.putShort(MAGIC);
        buffer.put(VERSION);
        buffer.put(type);
        buffer.putShort((short) (studentToken & 0xFFFF));
        buffer.putInt(seq);
        buffer.putInt(ack);
        buffer.putInt(byteStart);
        buffer.putInt(byteEnd);
        buffer.putShort((short)(payload.length & 0xFFFF));
        buffer.put(payload);
        return buffer.array();
    }
    //解析报文
    public static Packet parsePacket(byte[] data, int length) throws IOException {
        if(length < HEADER_SIZE){//数据报必须大于等于首部大小
            throw new IllegalArgumentException("数据包太小，无法解析");
        }
        ByteBuffer buffer =ByteBuffer.wrap(data,0,length);
        short magic = buffer.getShort();
        if(magic !=MAGIC){
            throw new IOException("无效的UDP数据包，魔数不匹配");
        }

        byte version = buffer.get();
        if(version != VERSION) {
            throw new IOException("不支持的协议版本: " + version);
        }
        byte type = buffer.get();
        int token = buffer.getShort();
        int seq = buffer.getInt();
        int ack = buffer.getInt();
        int byteStart = buffer.getInt();
        int byteEnd = buffer.getInt();
        short payloadLength = buffer.getShort();
        if(payloadLength < 0 || payloadLength > PAYLOAD_SIZE){
            throw new IOException("无效的payload长度: " + payloadLength);
        }
        if(payloadLength != length - HEADER_SIZE){
            throw new IOException("数据包长度与payload长度不匹配");
        }
        byte [] payload = new byte[payloadLength];
        buffer.get(payload);
        return new Packet(type, token, seq, ack, byteStart, byteEnd, payload);

    }

    //获取当前数据报的名称
    public static String packetName(byte type) {
        return switch (type) {
            case TYPE_CONNECT -> "CONNECT";
            case TYPE_CONNECT_ACK -> "CONNECT_ACK";
            case TYPE_DATA -> "DATA";
            case TYPE_ACK -> "ACK";
            case TYPE_FIN -> "FIN";
            case TYPE_FIN_ACK -> "FIN_ACK";
            case TYPE_REJECT -> "REJECT";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    //把字节数组转为字符串
    public static String payloadAsAscii(byte [] payload) {
        return new String (payload , StandardCharsets.US_ASCII);
    }
    //把字符串转为字节数组
    public static byte[] asciiPayload(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
    public static class Packet {
        public final byte type;
        public final int studentToken;
        public final int seq;
        public final int ack;
        public final int byteStart;
        public final int byteEnd;
        public final byte[] payload;
        public Packet(byte type , int studentToken ,int seq,int ack,int byteStart ,int byteEnd,byte [] payload){
            this.ack = ack;
            this.type = type;
            this.payload = payload;
            this.byteEnd = byteEnd;
            this.byteStart = byteStart;
            this.studentToken = studentToken;
            this.seq = seq;
        }
        public int packetLength () {
            return HEADER_SIZE + payload.length;//首部大小 + 数据的长度
        }
    }
}
