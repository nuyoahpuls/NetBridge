import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//生成日志
public class RunLogger {
    private static final Path LOG_FILE = Path.of("run_log.txt");//日志文件的路径
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private RunLogger() {
    }
    public static synchronized void init(String role) {
        String header = "==== " + role + " run log started at " + now() + " ====" + System.lineSeparator();

        try {
            Files.writeString(
                    LOG_FILE,
                    header,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.out.println("Failed to initialize run_log.txt: " + e.getMessage());
        }
    }

    public static void sent(String peer, short type, int length) {//记录发送事件
        write("SEND", peer, type, length, "");
    }

    public static void received(String peer, short type, int length) {//记录接收事件
        write("RECEIVE", peer, type, length, "");
    }

    public static void info(String message) {//记录一般信息
        writeLine(now() + " | INFO | " + message);
    }

    public static void error(String message) {//记录报错信息
        writeLine(now() + " | ERROR | " + message);
    }

    private static synchronized void write(String event, String peer, short type, int length, String extra) {
        String line = now() + " | " + event + " | peer=" + peer + " | type=" + type + " | packet=" + packetName(type) + " | length=" + length;
        if (extra != null && !extra.isEmpty()) {//额外信息
            line += " | " + extra;
        }
        writeLine(line);
    }

    private static synchronized void writeLine(String line) {
        try {
            Files.writeString(
                    LOG_FILE,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.out.println("Failed to write run_log.txt: " + e.getMessage());
        }
    }

    private static String now() {//获取当前时间并且格式化
        return LocalDateTime.now().format(FORMATTER);
    }

    private static String packetName(short type) {
        switch (type){
            case Protocol.TYPE_INITIALIZATION -> {
                return  "Initialization";
            }
            case Protocol.TYPE_AGREE -> {
                return "Agree";
            }
            case Protocol.TYPE_REVERSE_REQUEST -> {
                return "ReverseRequest";
            }
            case Protocol.TYPE_REVERSE_RESPONSE -> {
                return "ReverseAnswer";
            }
        }
        return "Unknown";
    }
}