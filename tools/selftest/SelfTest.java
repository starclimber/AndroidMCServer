package dev.tinymcserver.selftest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;
import java.security.Signature;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipFile;
import javax.crypto.KeyAgreement;
import javax.net.ssl.SSLContext;

/**
 * Android MC Server 环境探测 jar。
 *
 * 设计要点：每个动作都是「先打印、flush，再执行」，所以一旦进程被原生层 abort，
 * 最后一条打印就是崩溃点。模式：
 *   net1/net2/net3  —— 连做 1/2/5 次 HTTPS 请求（每次请求前都打印）
 *   http1           —— 明文 HTTP（不走 TLS）
 *   sock1           —— DNS 解析 + 裸 TCP 连接（不走 TLS）
 *   native1         —— 无网络：临时目录写入 / Deflate / 读 zip / 起子进程 / dlopen / 内存标签
 *   dl              —— 按官方清单下载 vanilla 服务端 jar 并校验 SHA-1
 *   all             —— 上面全部
 * 用法: java -jar selftest.jar <mode> [minecraft版本]
 */
public class SelfTest {

    static final String[] HTTPS_URLS = {
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
            "https://api.purpurmc.org/v2/purpur",
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://api.modrinth.com/",
            "https://piston-data.mojang.com/",
    };

    static void p(String s) {
        System.out.println(s);
        System.out.flush();
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "all";
        String mc = args.length > 1 ? args[1] : "";
        p("==== Android MC Server 探测 [mode=" + mode + "] 版本=" + (mc.isEmpty() ? "-" : mc) + " ====");
        switch (mode) {
            case "net1": net(1); break;
            case "net2": net(2); break;
            case "net3": net(5); break;
            case "http1": httpPlain(); break;
            case "sock1": sock(); break;
            case "native1": nativeProbe(); break;
            case "dl": download(mc); break;
            default: all(mc); break;
        }
        p("==== 结束（未崩溃）====");
    }

    static void all(String mc) {
        props();
        tmpWrite();
        crypto();
        ctx();
        net(2);
        httpPlain();
        sock();
        nativeProbe();
        download(mc);
    }

    // ---------------- 各模式 ----------------

    static void net(int n) {
        p("--> 连续 " + n + " 次 HTTPS 请求");
        for (int i = 0; i < n; i++) {
            String url = HTTPS_URLS[i % HTTPS_URLS.length];
            p("  [" + (i + 1) + "/" + n + "] 准备请求 " + url);
            long t = System.currentTimeMillis();
            https(url);
            p("      本次耗时 " + (System.currentTimeMillis() - t) + "ms");
        }
        p("    完成");
    }

    static void httpPlain() {
        p("--> 明文 HTTP（不经过 TLS）");
        try {
            HttpURLConnection c = open("http://connectivitycheck.gstatic.com/generate_204");
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) { in.read(new byte[1024]); in.close(); }
            p("      HTTP " + code + "  已关闭");
            c.disconnect();
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("    完成");
    }

    static void sock() {
        p("--> DNS 解析");
        for (String h : new String[]{"piston-meta.mojang.com", "api.purpurmc.org"}) {
            try {
                InetAddress[] as = InetAddress.getAllByName(h);
                p("  " + h + " -> " + Arrays.toString(as));
            } catch (Throwable e) {
                p("  " + h + " -> 失败 " + e);
            }
        }
        p("--> 裸 TCP 连接（无 TLS）");
        for (String h : new String[]{"piston-meta.mojang.com", "api.purpurmc.org"}) {
            p("  连接 " + h + ":443");
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(h, 443), 15000);
                p("    已连接 " + s.getRemoteSocketAddress());
                OutputStream o = s.getOutputStream();
                o.write(("GET / HTTP/1.0\r\nHost: " + h + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                o.flush();
                p("    写入成功，字节数=" + s.getInputStream().read(new byte[256]));
            } catch (Throwable e) {
                p("    失败 " + e);
            }
        }
        p("    完成");
    }

    static void nativeProbe() {
        p("--> 无网络原生能力探测");
        p("  [1] 临时目录写入");
        try {
            Path t = Files.createTempFile("pmcs", ".tmp");
            Files.writeString(t, "hello-tiny");
            p("      " + t + " 大小=" + Files.size(t));
            Files.deleteIfExists(t);
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("  [2] Deflate/Inflater（原生 libzip）");
        try {
            byte[] data = new byte[200_000];
            new Random(42).nextBytes(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (DeflaterOutputStream d = new DeflaterOutputStream(bos)) { d.write(data); }
            byte[] z = bos.toByteArray();
            ByteArrayOutputStream back = new ByteArrayOutputStream();
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(z))) {
                in.transferTo(back);
            }
            p("      压缩 " + data.length + " -> " + z.length + "  还原=" + back.size());
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("  [3] 读 zip");
        try {
            String self = System.getProperty("java.class.path", "").split(File.pathSeparator)[0];
            try (ZipFile zf = new ZipFile(self)) {
                p("      " + self + " 条目数=" + zf.size());
            }
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("  [4] 起子进程（jspawnhelper / posix_spawn）");
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", "echo child_ok; uname -m");
            pb.redirectErrorStream(true);
            Process pr = pb.start();
            String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p("      退出码=" + pr.waitFor() + "  输出=" + out.replace('\n', ' '));
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("  [5] dlopen 原生库");
        for (String lib : new String[]{"zip", "net", "nio", "java", "verify", "management", "dt_socket", "prefs", "sunec"}) {
            try {
                System.loadLibrary(lib);
                p("      lib" + lib + ".so = 已加载");
            } catch (Throwable e) {
                p("      lib" + lib + ".so = 失败 " + e.getClass().getSimpleName());
            }
        }
        p("  [6] 内存标签（判断本进程是否被 Android 打了 tagged pointer）");
        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object u = f.get(null);
            Method alloc = uc.getMethod("allocateMemory", long.class);
            Method free = uc.getMethod("freeMemory", long.class);
            for (int i = 0; i < 4; i++) {
                long a = (Long) alloc.invoke(u, 1 << 20);
                long top = (a >>> 56) & 0xff;
                p("      malloc 0x" + Long.toHexString(a) + "  顶字节=0x" + Long.toHexString(top)
                        + (top == 0 ? "  (无标签)" : "  (有标签!)"));
                free.invoke(u, a);
            }
        } catch (Throwable e) {
            p("      失败 " + e);
        }
        p("    完成");
    }

    static void download(String mcVersion) {
        p("--> 下载 vanilla 服务端 jar");
        if (mcVersion.isEmpty()) { p("  未指定版本，跳过"); return; }
        try {
            String manifest = httpText("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            p("  版本清单 " + manifest.length() + " 字节");
            Matcher m = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*\"" + Pattern.quote(mcVersion)
                    + "\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(manifest);
            if (!m.find()) { p("  清单里没有版本 " + mcVersion); return; }
            String vjson = httpText(m.group(1));
            Matcher sm = Pattern.compile(
                    "\"server\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"sha1\"\\s*:\\s*\"([0-9a-f]+)\"[^}]*?\"size\"\\s*:\\s*(\\d+)")
                    .matcher(vjson);
            if (!sm.find()) { p("  未解析到 server 下载项"); return; }
            String dl = sm.group(1), sha1 = sm.group(2), size = sm.group(3);
            p("  url=" + dl);
            p("  size=" + size + "  sha1=" + sha1);
            p("  开始下载…");
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            HttpURLConnection c = open(dl);
            long total = 0, t0 = System.currentTimeMillis();
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) { md.update(buf, 0, n); total += n; }
            }
            String got = hex(md.digest());
            p("  下载完成 " + total + " 字节，用时 " + ((System.currentTimeMillis() - t0) / 1000) + "s");
            p("  sha1 " + got + "  匹配=" + got.equalsIgnoreCase(sha1));
        } catch (Throwable e) {
            p("  失败 " + e);
        }
        p("    完成");
    }

    // ---------------- 基础设施 ----------------

    static void props() {
        p("--> 运行时属性");
        p("  java.version   = " + System.getProperty("java.version"));
        p("  java.home      = " + System.getProperty("java.home"));
        p("  os.name/arch   = " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        p("  user.home      = " + System.getProperty("user.home"));
        p("  java.io.tmpdir = " + System.getProperty("java.io.tmpdir"));
        p("  可用处理器      = " + Runtime.getRuntime().availableProcessors());
        p("  最大堆          = " + (Runtime.getRuntime().maxMemory() >> 20) + " MB");
        p("  输入参数        = " + java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        p("    完成");
    }

    static void tmpWrite() {
        p("--> 临时目录写入");
        try {
            Path t = Files.createTempFile("pmcs", ".tmp");
            Files.writeString(t, "ok");
            p("  " + t);
            Files.deleteIfExists(t);
        } catch (Throwable e) {
            p("  失败 " + e);
        }
        p("    完成");
    }

    static void crypto() {
        p("--> EC / 加密算法可用性");
        for (String a : new String[]{"ECDH", "XDH", "SHA256withECDSA", "SHA256withRSA"}) {
            try {
                if (a.equals("ECDH") || a.equals("XDH")) KeyAgreement.getInstance(a);
                else Signature.getInstance(a);
                p("  " + a + " = 可用");
            } catch (Throwable e) {
                p("  " + a + " = 不可用 " + e.getClass().getSimpleName());
            }
        }
        StringBuilder sb = new StringBuilder();
        Arrays.stream(Security.getProviders()).forEach(x -> sb.append(x.getName()).append(' '));
        p("  providers = " + sb);
        p("    完成");
    }

    static void ctx() {
        p("--> TLS 上下文 / 密码套件");
        try {
            SSLContext sc = SSLContext.getDefault();
            List<String> cs = Arrays.asList(sc.getSocketFactory().getSupportedCipherSuites());
            p("  套件数=" + cs.size() + "  含ECDHE=" + cs.stream().anyMatch(s -> s.contains("ECDHE")));
        } catch (Throwable e) {
            p("  失败 " + e);
        }
        p("    完成");
    }

    static void https(String url) {
        try {
            HttpURLConnection c = open(url);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            int n = 0;
            if (in != null) {
                byte[] buf = new byte[4096];
                n = in.read(buf);
                in.close();
            }
            p("      HTTP " + code + "  首块 " + n + " 字节  已关闭");
            c.disconnect();
        } catch (Throwable e) {
            p("      失败 " + e.getClass().getName() + ": "
                    + String.valueOf(e.getMessage()).split("\n")[0]);
        }
    }

    static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "TinyMCserver-selftest");
        return c;
    }

    static String httpText(String url) throws Exception {
        HttpURLConnection c = open(url);
        InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
