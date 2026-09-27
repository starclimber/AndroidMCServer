/*
 * libandroid-shmem.c — Pocket MCserver 自研的 SysV 共享内存最小实现
 * ------------------------------------------------------------------
 * 背景：Android(bionic) 没有 SysV 共享内存（shmget/shmat/shmdt/shmctl），
 *       而 HotSpot 的 PerfMemory 会调用它们（经 libandroid_ 前缀封装）。
 *       本文件用 memfd_create + mmap 在进程内模拟这四个调用，替代 Termux 的
 *       libandroid-shmem.so，只导出 JVM 实际用到的 4 个符号。
 *
 * 特点：纯 aarch64 系统调用，不依赖任何 libc 函数；唯一外部符号是 bionic 的
 *       __errno（用于向调用方回报 errno，运行时由 linker 从 libc 解析）。
 *
 * 编译（Zig 交叉编译，产出 Android aarch64 共享库）：
 *   zig cc -target aarch64-linux-android -nostdlib -ffreestanding -shared -fPIC \
 *          -fno-sanitize=undefined -Wl,-soname,libandroid-shmem.so \
 *          -o libandroid-shmem.so libandroid-shmem.c
 */

typedef long            s64;
typedef unsigned long   u64;
typedef int             s32;
typedef unsigned int    u32;
typedef unsigned short  u16;
typedef unsigned char   u8;

/* ---------------- aarch64 系统调用 ---------------- */
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

#define NR_memfd_create 279
#define NR_mmap         222
#define NR_munmap       215
#define NR_ftruncate     46
#define NR_close         57

#define PROT_RW   3      /* PROT_READ|PROT_WRITE */
#define MAP_SHARED 1

/* ---------------- errno（借 bionic） ---------------- */
extern int *__errno(void);
#define SET_ERRNO(v)  do { *__errno() = (v); } while (0)

#define ENOENT   2
#define EINVAL  22
#define EEXIST  17
#define EMFILE  24
#define ENOSPC  28

/* ---------------- IPC 常量 ---------------- */
#define IPC_CREAT  01000
#define IPC_EXCL   02000
#define IPC_RMID   0
#define IPC_SET    1
#define IPC_STAT   2

/* ---------------- 结构（aarch64 LP64 布局） ---------------- */
struct ipc_perm {
    s32 key; u32 uid; u32 gid; u32 cuid; u32 cgid;
    u32 mode; u16 seq; u16 __pad;
    u64 __unused1; u64 __unused2;
};
struct shmid_ds {
    struct ipc_perm shm_perm;
    u64 shm_segsz;
    s64 shm_atime; s64 shm_dtime; s64 shm_ctime;
    s32 shm_cpid; s32 shm_lpid;
    u64 shm_nattch;
    u64 __unused3; u64 __unused4;
};

/* ---------------- 进程内表 ---------------- */
#define MAX_SHM 32
struct ent { s32 used; s32 key; s32 fd; u64 size; s32 id; };
static struct ent g_tab[MAX_SHM];
static s32 g_seq = 1;

#define MAX_MAP 64
struct mp { s32 used; u64 addr; u64 size; s32 id; };
static struct mp g_map[MAX_MAP];

static s32 find_slot(void) {
    for (s32 i = 0; i < MAX_SHM; i++) if (!g_tab[i].used) return i;
    return -1;
}
static s32 find_by_key(s32 key) {
    for (s32 i = 0; i < MAX_SHM; i++) if (g_tab[i].used && g_tab[i].key == key) return i;
    return -1;
}
static s32 find_by_id(s32 id) {
    for (s32 i = 0; i < MAX_SHM; i++) if (g_tab[i].used && g_tab[i].id == id) return i;
    return -1;
}

/* ============== 导出接口 ============== */

__attribute__((visibility("default")))
s32 libandroid_shmget(s32 key, u64 size, s32 flag) {
    if (key != 0) {
        s32 i = find_by_key(key);
        if (i >= 0) {
            if ((flag & IPC_CREAT) && (flag & IPC_EXCL)) { SET_ERRNO(EEXIST); return -1; }
            if (size > g_tab[i].size) {
                if (__sc6(NR_ftruncate, g_tab[i].fd, (s64)size, 0, 0, 0, 0) < 0) {
                    SET_ERRNO(EINVAL); return -1;
                }
                g_tab[i].size = size;
            }
            return g_tab[i].id;
        }
        if (!(flag & IPC_CREAT)) { SET_ERRNO(ENOENT); return -1; }
    }
    s32 slot = find_slot();
    if (slot < 0) { SET_ERRNO(ENOSPC); return -1; }
    static const char nm[] = "pmshm";
    s64 fd = __sc6(NR_memfd_create, (s64)(u64)nm, 0, 0, 0, 0, 0);
    if (fd < 0) { SET_ERRNO(EMFILE); return -1; }
    if (__sc6(NR_ftruncate, fd, (s64)size, 0, 0, 0, 0) < 0) {
        __sc6(NR_close, fd, 0, 0, 0, 0, 0);
        SET_ERRNO(EINVAL); return -1;
    }
    g_tab[slot].used = 1;
    g_tab[slot].key  = key;
    g_tab[slot].fd   = (s32)fd;
    g_tab[slot].size = size;
    g_tab[slot].id   = g_seq++;
    return g_tab[slot].id;
}

__attribute__((visibility("default")))
void *libandroid_shmat(s32 id, void *addr, s32 flag) {
    s32 i = find_by_id(id);
    if (i < 0) { SET_ERRNO(EINVAL); return (void *)-1; }
    s64 r = __sc6(NR_mmap, (s64)(u64)addr, (s64)g_tab[i].size,
                  PROT_RW, MAP_SHARED, g_tab[i].fd, 0);
    if (r < 0 && r > -4096) { SET_ERRNO((s32)(-r)); return (void *)-1; }
    for (s32 k = 0; k < MAX_MAP; k++) {
        if (!g_map[k].used) {
            g_map[k].used = 1; g_map[k].addr = (u64)r;
            g_map[k].size = g_tab[i].size; g_map[k].id = id;
            break;
        }
    }
    return (void *)r;
}

__attribute__((visibility("default")))
s32 libandroid_shmdt(void *addr) {
    for (s32 k = 0; k < MAX_MAP; k++) {
        if (g_map[k].used && g_map[k].addr == (u64)addr) {
            s64 r = __sc6(NR_munmap, (s64)g_map[k].addr, (s64)g_map[k].size, 0, 0, 0, 0);
            g_map[k].used = 0;
            return r < 0 ? -1 : 0;
        }
    }
    SET_ERRNO(EINVAL);
    return -1;
}

__attribute__((visibility("default")))
s32 libandroid_shmctl(s32 id, s32 cmd, void *buf) {
    s32 i = find_by_id(id);
    if (i < 0) { SET_ERRNO(EINVAL); return -1; }
    if (cmd == IPC_RMID) {
        __sc6(NR_close, g_tab[i].fd, 0, 0, 0, 0, 0);
        g_tab[i].used = 0;
        return 0;
    }
    if (cmd == IPC_STAT && buf) {
        struct shmid_ds *ds = (struct shmid_ds *)buf;
        for (u64 z = 0; z < (u64)sizeof(struct shmid_ds); z++) ((u8 *)ds)[z] = 0;
        ds->shm_perm.key  = g_tab[i].key;
        ds->shm_perm.mode = 0666;
        ds->shm_segsz     = g_tab[i].size;
        ds->shm_nattch    = 1;
        return 0;
    }
    return 0;
}
