import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//日志类
public class UdpRunLogger {
    private static final Path LOG_FILE = Path.of("run_log.txt");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private UdpRunLogger() {
    }
    public static synchronized void init(String role) {
        String header = "==== " + role + " UDP run log started at " + now() + " ====" + System.lineSeparator();
        try {
            Files.writeString(LOG_FILE, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.println("创建run_log.txt失败: " + e.getMessage());
        }
    }
    //记录发送事件
    public static void sent(String peer, byte type, int seq, int ack, int byteStart, int byteEnd, int length) {
        write("SEND", peer, type, seq, ack, byteStart, byteEnd, length, "");
    }
    //记录接收事件
    public static void received(String peer, byte type, int seq, int ack, int byteStart, int byteEnd, int length) {
        write("RECEIVE", peer, type, seq, ack, byteStart, byteEnd, length, "");
    }
    //记录丢包事件
    public static void dropped(String peer, byte type, int seq, int ack, int byteStart, int byteEnd, int length) {
        write("DROP", peer, type, seq, ack, byteStart, byteEnd, length, "simulated_loss=true");
    }
    //记录超时事件
    public static void timeout(String peer, int baseSeq, int nextSeq) {
        writeLine(now() + " | TIMEOUT | peer=" + peer + " | baseSeq=" + baseSeq + " | nextSeq=" + nextSeq);
    }
    //记录重传事件
    public static void retransmit(String peer, int seq, int byteStart, int byteEnd, int length) {
        write("RETRANSMIT", peer, UdpProtocol.TYPE_DATA, seq, 0, byteStart, byteEnd, length, "");
    }

    public static void info(String message) {
        writeLine(now() + " | INFO | " + message);
    }

    public static void error(String message) {
        writeLine(now() + " | ERROR | " + message);
    }

    private static synchronized void write(String event, String peer, byte type, int seq, int ack, int byteStart,
                                           int byteEnd, int length, String extra) {
        String line = now()
                + " | " + event + " | peer=" + peer
                + " | type=" + type + " | packet=" + UdpProtocol.packetName(type)
                + " | seq=" + seq + " | ack=" + ack
                + " | bytes=" + byteStart + "-" + byteEnd + " | length=" + length;

        if (extra != null && !extra.isEmpty()) {
            line += " | " + extra;
        }

        writeLine(line);
    }

    private static synchronized void writeLine(String line) {
        try {
            Files.writeString(LOG_FILE, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Failed to write run_log.txt: " + e.getMessage());
        }
    }

    private static String now() {//获取当前时间并格式化为字符串
        return LocalDateTime.now().format(FORMATTER);
    }
}
