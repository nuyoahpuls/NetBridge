import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

//TCP的协议类
public class Protocol {
    public static final short TYPE_INITIALIZATION = 1;
    public static final short TYPE_AGREE = 2;
    public static final short TYPE_REVERSE_REQUEST = 3;
    public static final short TYPE_REVERSE_RESPONSE = 4;

    public static final int TYPE_SIZE = 2;
    public static final int INT_SIZE = 4;
    public static final int INIT_PACKET_SIZE = TYPE_SIZE + INT_SIZE;
    public static final int AGREE_SIZE = TYPE_SIZE;
    public static final int DATA_HEADER_SIZE = TYPE_SIZE + INT_SIZE;

    //工具类
    private Protocol() {
    }

    //发送数据
    public static void sendInitialization(DataOutputStream out ,int blockCount)throws IOException {
        if(out == null){
            throw  new IOException("Output不能为null");
        }
        out.writeShort(TYPE_INITIALIZATION);
        out.writeInt(blockCount);
        out.flush();
    }

    //接受初始化数据
    public static  int readInitialization(DataInputStream in) throws IOException{
        if(in == null){
            throw new  IOException("Input不能为null");
        }
        short type = in.readShort();
        if(type != TYPE_INITIALIZATION){
            throw new IOException("发送的协议报文类型错误，期望类型为"+TYPE_INITIALIZATION+"，但实际类型为"+type);
        }
        return in.readInt();
    }

    //发送同意报文
    public static void sendAgree(DataOutputStream out) throws IOException{
        if(out == null){
            throw new IOException("Output不能为null");
        }
        out.writeShort(TYPE_AGREE);
        out.flush();
    }

    //读取同意报文
    public static void readAgree(DataInputStream in) throws IOException {
        if(in == null){
            throw new IOException("Input不能为null");
        }
        short type = in.readShort();
        if(type != TYPE_AGREE){
            throw new IOException("发送的协议报文类型错误，期望类型为"+TYPE_AGREE+"，但实际类型为"+type);
        }
    }

    //发送反转请求报文
    public static void sendReverseRequest(DataOutputStream out,byte[] data) throws IOException {
        if(out ==null){
            throw new IOException("Output不能为null");
        }
        out.writeShort(TYPE_REVERSE_REQUEST);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    //读取反转请求报文
    public static byte[] readReverseRequest(DataInputStream in) throws IOException {
        if(in == null){
            throw new IOException("Input不能为null");
        }
        short type= in.readShort();
        if(type != TYPE_REVERSE_REQUEST){
            throw new IOException("发送的协议报文类型错误，期望类型为"+TYPE_REVERSE_REQUEST+"，但实际类型为"+type);
        }
        int length = in.readInt();
        return readBytes(in,length);
    }

    //发送反转响应报文
    public static void sendReverseAnswer(DataOutputStream out,byte[] data) throws IOException {
        if(out == null){
            throw new IOException("Output不能为null");
        }
        out.writeShort(TYPE_REVERSE_RESPONSE);
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    //读取反转响应报文
    public static byte[] readReverseAnswer(DataInputStream in) throws IOException {
        if(in == null){
            throw new IOException("Input不能为null");
        }
        short type = in.readShort();
        if(type != TYPE_REVERSE_RESPONSE){
            throw new IOException("发送的协议报文类型错误，期望类型为"+TYPE_REVERSE_RESPONSE+"，但实际类型为"+type);
        }
        int length = in.readInt();
        return readBytes(in,length);
    }


    public static byte[] readBytes (DataInputStream in ,int length) throws IOException{
        if(in == null){
            throw new IOException("Input不能为null");
        }
        if(length<0){
            throw new IOException("数据长度不能为负数");
        }
        //读取数据
        byte [] data =new byte[length];
        in.readFully(data);
        return data;
    }
    public static byte[] toAsciiBytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    public static String fromAsciiBytes(byte[] data) {
        return new String(data, StandardCharsets.US_ASCII);
    }
}
