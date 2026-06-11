UDP Socket 可靠传输实验运行说明

1. 程序说明

本程序基于 UDP Socket 实现一个简单的可靠传输协议。UDP 本身不保证连接、可靠到达、顺序到达和自动重传。
因此本程序在应用层实现了连接建立、StudentToken 校验、数据包编号、累计 ACK、超时重传、发送窗口、RTT 统计和连接关闭。

主要文件：
- UdpProtocol.java：定义应用层报文格式，负责封装和解析 UDP 报文
- ReliableUdpServer.java：UDP 服务端，负责校验连接、模拟丢包、接收数据、返回累计 ACK、处理 FIN
- ReliableUdpClient.java：UDP 客户端，负责读取文件、分包、建立连接、发送数据、处理 ACK、超时重传和输出统计
- UdpRunLogger.java：运行日志工具，自动生成 run_log.txt

2. 运行环境

- 操作系统：Windows 10/11，或 Linux/WSL/虚拟机环境
- JDK：建议 JDK 17 或以上
- 抓包工具：Wireshark
- 编码要求：输入文件建议使用 ASCII 文本

检查 Java 环境：
java -version
javac -version

3. 编译方式

进入源码目录：

cd D:\JAVA\NetBridge\UDPTask\src

编译：

javac *.java

4. 准备测试数据文件

建议在 src 目录下创建 udp_input.txt

说明：
- 每个 DATA 数据包最多携带 80 字节应用层数据。
- 如果希望发送 30 个数据包，输入文件至少需要 30 * 80 = 2400 字节。

5. 本地运行方式

需要打开两个 PowerShell 窗口。

窗口 1：启动 server

cd D:\JAVA\NetBridge\UDPTask\src
java ReliableUdpServer 9999 0.3 42

参数说明：
- 9999：server 监听端口
- 0.3：模拟丢包率，表示 30%
- 42：随机种子，用于复现实验结果

窗口 2：启动 client

cd D:\JAVA\NetBridge\UDPTask\src
java ReliableUdpClient 127.0.0.1 9999 1234 .\udp_input.txt 30 300

参数说明：
- 127.0.0.1：serverIP，本地测试时表示本机
- 9999：serverPort，必须和 server 监听端口一致
- 1234：学号后四位，程序会计算 studentToken = 1234 XOR 0x5A3C
- .\udp_input.txt：待发送文件
- 30：最多发送 30 个 UDP DATA 数据包
- 300：超时时间 300ms

6. Host OS / Guest OS 运行方式

题目要求 client 运行在 host OS，server 运行在 guest OS。实际运行时：

- 在 guest OS 中启动 server
- 在 host OS 中启动 client
- client 的 serverIP 参数填写 guest OS 的真实 IP，而不是 127.0.0.1

例如 guest OS 的 IP 是 192.168.56.101，则 client 命令为：

java ReliableUdpClient 192.168.56.101 9999 1234 .\udp_input.txt 30 300

guest OS 查看 IP：

Linux/WSL:
hostname -I
ip addr

7. 日志文件

程序运行时会自动生成 run_log.txt，记录：
- SEND：发送事件
- RECEIVE：接收事件
- DROP：server 模拟丢包事件
- TIMEOUT：client 超时事件
- RETRANSMIT：client 重传事件
- INFO：普通信息事件
- ERROR：错误信息事件

run_log.txt 会生成在当前执行 java 命令的目录下。建议 client 和 server 分别在不同目录运行，或者运行后及时保存日志，避免互相覆盖

8. Wireshark 抓包

本机 127.0.0.1 测试时，需要抓取 Adapter for loopback traffic capture

过滤器：

udp.port == 9999

如果 server 在虚拟机或 WSL 中运行，则抓取对应的物理网卡、虚拟网卡或 host-only 网卡

9. 预期现象

client 输出示例：

发送连接请求，等待服务器响应... 第 1 次
收到连接确认，连接已建立
发送第 1 个（第 1~80 字节）数据包
收到ACK，确认第 1 个（1~80 字节）数据包，RTT=xx ms
等待ACK超时，重传窗口内的未确认数据包...
重传第 n 个（第 x~y 字节）数据包
发送FIN包，等待服务器响应... 第 1 次
收到FIN_ACK包，连接已关闭

server 输出示例：

UDP SERVER 开始在端口 9999 上运行，丢包率=0.3
客户端连接成功: 127.0.0.1:xxxxx
收到数据包: 1 (1-80 bytes), ack=1
模拟丢包: 4 from 127.0.0.1:xxxxx is ignored
客户端已完成传送，服务器发送fin-ack

10. 注意事项

- 80 字节指 DATA 报文的应用层 payload，不包含自定义协议头、UDP 头和 IP 头。
- 自定义协议头为 24 字节，因此一个满载 DATA 报文的应用层 UDP payload 为 24 + 80 = 104 字节。
- 如果使用 127.0.0.1 通信，普通 WLAN/以太网接口可能抓不到包，需要抓 loopback 接口。
- 若丢包率较高，client 会出现多次超时和重传，这是正常现象。
- server 是长期运行的监听程序，client 结束后 server 继续等待新的 client，这是正常行为。
