/*
 * libandroid-spawn.c — Pocket MCserver 自研的 posix_spawn 实现（兼容旧 Android）
 * ---------------------------------------------------------------------------
 * 问题：bionic libc 的 posix_spawn 是 API 28 才加入的，Android 8.x(API 26/27)
 *       及更早根本没有。原「纯转发」方案在旧系统上会失败。
 *
 * 策略（两层，自动选择）：
 *   1) 优先转发到系统(bionic)的 posix_spawn —— API 28+ 存在，性能最好；
 *   2) 系统没有时（API < 28），回退到自带实现：clone(SIGCHLD) + execve。
 *      JVM 只用 posix_spawn 一个符号、且不传 file_actions/attrp（已用 readelf
 *      核实 libjava/libjvm 对该系列的引用只有 posix_spawn），因此回退实现只需
 *      覆盖 fa==NULL && attr==NULL 这一 JVM 实际使用的情形即可。
 *
 * 特点：回退路径为纯 aarch64 系统调用，不依赖任何 libc 函数；外部符号仅
 *       __errno 与 dlsym。
 *
 * 编译（Zig 交叉编译）：
 *   zig cc -target aarch64-linux-android -nostdlib -ffreestanding -shared -fPIC \
 *          -fno-sanitize=undefined -Wl,-soname,libandroid-spawn.so \
 *          -o libandroid-spawn.so libandroid-spawn.c
 */

typedef long          s64;
typedef unsigned long u64;
typedef int           s32;

extern int  *__errno(void);
extern void *dlsym(void *handle, const char *symbol);

#define RTLD_NEXT ((void *)-1L)
#define ENOSYS    38
#define SIGCHLD   17

static inline s64 __sc6(s64 n, s64 a, s64 b, s64 c, s64 d, s64 e, s64 f) {
    register s64 x8 __asm__("x8") = n;
    register s64 x0 __asm__("x0") = a;
    register s64 x1 __asm__("x1") = b;
    register s64 x2 __asm__("x2") = c;
    register s64 x3 __asm__("x3") = d;
    register s64 x4 __asm__("x4") = e;
    register s64 x5 __asm__("x5") = f;
    __asm__ __volatile__("svc #0"
        : "+r"(x0)
        : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5)
        : "memory");
    return x0;
}

#define NR_clone       220
#define NR_execve      221
#define NR_exit_group   94

typedef int (*posix_spawn_fn)(int *pid, const char *path,
                              const void *file_actions, const void *attrp,
                              char *const argv[], char *const envp[]);

/* 自带实现：clone(SIGCHLD) == fork 语义；子进程立即 execve。 */
static s32 own_posix_spawn(int *pid, const char *path,
                           char *const argv[], char *const envp[]) {
    s64 r = __sc6(NR_clone, SIGCHLD, 0, 0, 0, 0, 0);
    if (r < 0) return (s32)(-r);              /* posix_spawn 失败时返回错误码本身 */
    if (r == 0) {
        /* 这里开始是子进程 */
        __sc6(NR_execve, (s64)(u64)path, (s64)(u64)argv, (s64)(u64)envp, 0, 0, 0);
        __sc6(NR_exit_group, 127, 0, 0, 0, 0, 0);   /* execve 失败 */
        for (;;) { }                          /* 兜底，不会到达 */
    }
    if (pid) *pid = (s32)r;
    return 0;
}

__attribute__((visibility("default")))
int posix_spawn(int *pid, const char *path,
                const void *file_actions, const void *attrp,
                char *const argv[], char *const envp[]) {
    static posix_spawn_fn real = (posix_spawn_fn)0;
    static int tried = 0;
    if (!tried) {
        tried = 1;
        real = (posix_spawn_fn)dlsym(RTLD_NEXT, "posix_spawn");   /* bionic 的实现 */
    }
    if (real != (posix_spawn_fn)0) {
        return real(pid, path, file_actions, attrp, argv, envp);
    }
    /* API < 28：系统没有 posix_spawn，走自带实现（只覆盖 JVM 实际使用的 NULL 情形）*/
    if (file_actions == (const void *)0 && attrp == (const void *)0) {
        return own_posix_spawn(pid, path, argv, envp);
    }
    *__errno() = ENOSYS;
    return ENOSYS;
}
