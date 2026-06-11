TCPTask 程序运行说明
====================

一、项目说明
------------

本项目完成计网课设 Task1: TCP Socket programming。

程序基于 Java TCP Socket 实现 client/server 通信。client 从本地读取一个 ASCII 文本文件，将文件随机切分成若干块，每次向 server 发送一块数据；server 收到后将该块内容反转，再返回给 client。client 收到所有反转后的块之后，按照块顺序反向拼接，最终得到原文件整体反转后的输出文件。


二、文件结构
------------

项目目录：

D:\JAVA\NetBridge\TCPTask

主要源码位于：

D:\JAVA\NetBridge\TCPTask\src

包含文件：

1. Protocol.java
   自定义应用层协议的封装与解析代码。
   负责发送和读取四种报文：
   - Initialization
   - Agree
   - ReverseRequest
   - ReverseAnswer

2. ReverseTcpClient.java
   TCP 客户端程序。
   负责读取文件、随机分块、发送反转请求、接收反转结果、生成输出文件。

3. ReverseTcpServer.java
   TCP 服务端程序。
   负责监听端口、接收客户端连接、处理 reverse 请求，并支持多个客户端并发访问。

4. RunLogger.java
   运行日志工具类。
   自动生成 run_log.txt，用于记录每次报文发送、接收、连接、错误等事件。


三、运行环境
------------

推荐环境：

1. 操作系统：
   Windows 10/11，PowerShell 终端。

2. Java 环境：
   JDK 17 或更高版本。

3. 网络环境：
   - 本机测试时，client 和 server 可以都运行在 Windows 本机，此时 serverIP 可以写 127.0.0.1。
   - 如果按照课程要求区分 host OS 和 guest OS，则建议：
     server 运行在 guest OS 中；
     client 运行在 host OS 中；
     client 启动时指定 guest OS 的实际 IP 地址和 server 监听端口。

4. 抓包工具：
   Wireshark。


四、编译方法
------------

打开 PowerShell，进入源码目录：

cd D:\JAVA\NetBridge\TCPTask\src

编译全部 Java 文件：

javac *.java

如果编译成功，当前目录下会生成若干 .class 文件，例如：

Protocol.class
ReverseTcpClient.class
ReverseTcpServer.class
RunLogger.class


五、准备输入文件
----------------

可以在源码目录 D:\JAVA\NetBridge\TCPTask\src 下创建一个测试输入文件 input.txt。

PowerShell 示例：

Set-Content -Path .\input.txt -Value "a little monkey likes computer network socket programming." -NoNewline -Encoding ASCII

注意：

1. 课程要求输入文件为全英文可打印字符的 ASCII 文件。
2. 不建议使用中文作为测试内容，因为中文通常不是单字节 ASCII 字符，会影响字节级反转结果的理解。


六、启动 server
---------------

在第一个 PowerShell 窗口中运行：

cd D:\JAVA\NetBridge\TCPTask\src
java ReverseTcpServer 8888

参数说明：

8888 表示 server 监听的 TCP 端口号。

运行后 server 会一直等待 client 连接。如果有多个 client 同时连接，server 会为每个 client 创建独立线程进行处理。


七、启动 client
---------------

在第二个 PowerShell 窗口中运行：

cd D:\JAVA\NetBridge\TCPTask\src
java ReverseTcpClient 127.0.0.1 8888 .\input.txt .\output 3 8 42

参数含义如下：

1. 127.0.0.1
   serverIP。
   本机测试时使用 127.0.0.1。
   如果 server 在虚拟机/WSL/guest OS 中运行，应改成对应系统的实际 IP。

2. 8888
   serverPort。
   必须和 server 启动时监听的端口一致。

3. .\input.txt
   输入文件路径。

4. .\output
   输出文件名前缀。
   程序实际生成的输出文件会附加 serverIP 和 serverPort，用来区分不同连接。
   例如本地运行时可能生成：
   output_127_0_0_1_8888.out

5. 3
   Lmin，表示每个数据块的最小字节数。

6. 8
   Lmax，表示每个数据块的最大字节数。

7. 42
   chunk_seed，随机分块使用的种子。
   使用固定 seed 可以保证每次分块结果一致，便于验收时复现。


八、一次完整运行流程
--------------------

推荐按以下顺序运行：

1. 打开第一个 PowerShell，启动 server：

   cd D:\JAVA\NetBridge\TCPTask\src
   java ReverseTcpServer 8888

2. 打开第二个 PowerShell，创建测试文件：

   cd D:\JAVA\NetBridge\TCPTask\src
   Set-Content -Path .\input.txt -Value "a little monkey likes computer network socket programming." -NoNewline -Encoding ASCII

3. 在第二个 PowerShell 中启动 client：

   java ReverseTcpClient 127.0.0.1 8888 .\input.txt .\output 3 8 42

4. 查看输出文件：

   Get-Content .\output_127_0_0_1_8888.out

5. 查看运行日志：

   Get-Content .\run_log.txt


九、运行日志说明
----------------

程序运行时会自动生成 run_log.txt。

日志中记录的主要内容包括：

1. 程序初始化信息。
2. client 与 server 的连接信息。
3. 每一次报文发送事件。
4. 每一次报文接收事件。
5. 报文类型 Type。
6. 报文长度。
7. 对端地址 peer，例如 127.0.0.1:8888。
8. 时间戳。

run_log.txt 的作用：

1. 方便观察程序是否按照协议流程运行。
2. 方便和 Wireshark 抓包结果进行时间戳对应。
3. 验收时可以用来说明每一次报文发送和接收事件。

注意：

server 是多线程程序，多个 client 同时连接时可能共同写 run_log.txt。
RunLogger 中使用 synchronized 保证单次日志写入不会互相穿插。


十、Wireshark 抓包建议
---------------------

如果 client 和 server 都在本机运行，并且 serverIP 使用 127.0.0.1，则通信发生在回环地址上。

Windows 中抓取本机 127.0.0.1 通信时，通常需要选择 Wireshark 的 Loopback Adapter。

推荐过滤条件：

tcp.port == 8888

如果 server 运行在 guest OS，client 运行在 host OS，则应选择 host OS 与 guest OS 通信所使用的网卡，并根据实际端口过滤：

tcp.port == 8888

抓包时重点观察 TCP payload，即应用层自定义报文内容。


十一、协议类型说明
------------------

本程序使用 Type 字段区分四种应用层报文：

1. Type = 1
   Initialization 报文。
   client 发送给 server，告诉 server 本次需要 reverse 的数据块数量 N。

2. Type = 2
   Agree 报文。
   server 发送给 client，表示 server 已经接受本次任务。

3. Type = 3
   ReverseRequest 报文。
   client 发送给 server，携带一个需要反转的数据块。

4. Type = 4
   ReverseAnswer 报文。
   server 发送给 client，携带反转后的数据块。


十二、注意事项
--------------

1. client 启动前必须先启动 server。

2. client 参数中的 serverIP 和 serverPort 不能写死在代码中，应通过命令行参数传入。

3. Lmin 和 Lmax 应满足：
   Lmin > 0
   Lmax >= Lmin

4. TCP 是字节流协议，没有天然的报文边界。
   因此程序必须通过自定义协议中的 Type 和 Length 字段来判断一条应用层报文的结构。

5. DataOutputStream 写入的字段宽度必须和 DataInputStream 读取的字段宽度一致。
   例如：
   writeShort 对应 readShort；
   writeInt 对应 readInt；
   writeFully 对应 readFully。

6. 如果抓包中看到一个 TCP 包里包含多个应用层报文，或者一个应用层报文被拆到多个 TCP 包中，这是正常现象。
   因为 TCP 只保证字节流可靠有序，不保证应用层一次 write 对应对方一次 read。

7. 如果本机测试抓不到包，优先检查：
   - 是否选择了 Loopback Adapter；
   - 过滤条件是否正确；
   - serverPort 是否和实际运行端口一致；
   - 是否在抓包开始后才运行 client。


十三、示例命令汇总
------------------

编译：

cd D:\JAVA\NetBridge\TCPTask\src
javac *.java

创建输入文件：

Set-Content -Path .\input.txt -Value "a little monkey likes computer network socket programming." -NoNewline -Encoding ASCII

启动 server：

java ReverseTcpServer 8888

启动 client：

java ReverseTcpClient 127.0.0.1 8888 .\input.txt .\output 3 8 42

查看输出文件：

Get-Content .\output_127_0_0_1_8888.out

查看日志：

Get-Content .\run_log.txt

