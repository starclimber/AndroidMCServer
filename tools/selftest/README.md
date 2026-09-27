# 环境探测 jar（由「自检」按钮 ④ 步在手机内置 JRE 中运行）

编译与更新（在沙箱里用系统 JDK 即可，目标字节码 17）：

```
cd tools/selftest
javac --release 17 -d classes SelfTest.java
jar --create --file ../../app/src/main/assets/runtime/selftest.jar \
    --main-class dev.tinymcserver.selftest.SelfTest -C classes .
```

模式（`java -jar selftest.jar <mode> [mc版本]`）：
- `net1` / `net2` / `net3`：连做 1 / 2 / 5 次 HTTPS 请求（每次请求前都打印）
- `http1`：明文 HTTP（绕过 TLS）
- `sock1`：DNS 解析 + 裸 TCP 连接（绕过 TLS）
- `native1`：无网络 —— 临时目录写入 / Deflate / 读 zip / 起子进程 / dlopen / 内存标签检测
- `dl`：按官方清单下载 vanilla 服务端 jar 并校验 SHA-1
- `all`：以上全部（默认）

每步先打印再执行，进程若被原生层 abort，最后一条 `--> ` 即崩溃点。
App 里的「深诊」会按矩阵自动跑若干组（不同 JVM 参数 / 有无 TLS / 有无 LD_LIBRARY_PATH）。
