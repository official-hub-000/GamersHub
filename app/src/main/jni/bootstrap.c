#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define BLKROSET   0x125d
/* Defaults only. The kernel module passes --payload-dir and --prefs when it execs this
 * binary, and those win. They exist because three parties have to agree on one absolute
 * path and they are computed in three languages: baking it in here as well as in the
 * module is how they drifted apart in the first place. */
#define DEFAULT_PAYLOAD_DIR "/data/user_de/0/com.gamershub"
#define DEFAULT_PREFS       "/data/user_de/0/com.gamershub/shared_prefs/dfroot.xml"
#define KSUD                "/data/adb/ksud"
#define MODULES_DIR         "/data/adb/modules"

static const char *payload_dir = DEFAULT_PAYLOAD_DIR;
static const char *prefs_path  = DEFAULT_PREFS;

/* How long the uid-0 bridge stays available after the exploit runs. Long enough that a
 * user who presses RUN DFRROOT and then walks through the tab is not racing a timer, and
 * bounded so an abandoned run does not leave a root shell on the device indefinitely. */
static int bridge_timeout = 900;

static void build_paths(char *ksud_out, size_t ksud_size)
{
    snprintf(ksud_out, ksud_size, "%s/ksud", payload_dir);
}

static int pref_true(const char *buf, const char *key)
{
    char needle[64];
    snprintf(needle, sizeof(needle), "name=\"%s\"", key);
    char *p = strstr(buf, needle);
    if (!p) return 0;
    char *tag_end = strchr(p, '>');
    char *v = strstr(p, "value=\"true\"");
    return v && tag_end && v < tag_end;
}

static int read_prefs(char *su_manager, size_t su_manager_size, int *soft_reboot,
                      int *disable_modules)
{
    int fd = open(prefs_path, O_RDONLY);
    if (fd < 0) return -1;

    char buf[4096];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';

    char *p = strstr(buf, "name=\"su_manager\">");
    if (!p) return -1;
    p += strlen("name=\"su_manager\">");
    char *end = strchr(p, '<');
    if (!end) return -1;
    size_t len = end - p;
    if (len == 0 || len >= su_manager_size) return -1;
    memcpy(su_manager, p, len);
    su_manager[len] = '\0';

    *soft_reboot = pref_true(buf, "soft_reboot");
    *disable_modules = pref_true(buf, "disable_modules");

    return 0;
}

static int adopt_zygote_env(void)
{
    FILE *f = popen("pidof zygote64 zygote", "r");
    if (!f) return -1;
    int pid = 0;
    fscanf(f, "%d", &pid);
    pclose(f);
    if (!pid) return -1;

    char path[32];
    snprintf(path, sizeof(path), "/proc/%d/environ", pid);
    int fd = open(path, O_RDONLY);
    if (fd < 0) return -1;
    static char buf[16384];
    int n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return -1;
    buf[n] = '\0';
    for (char *p = buf, *end = buf + n; p < end; p += strlen(p) + 1)
        putenv(p);
    return 0;
}

static int should_ro(const char *name)
{
    size_t len = strlen(name);
    if (!strcmp(name, "super"))  return 1;
    if (!strcmp(name, "misc"))   return 1;
    if (!strcmp(name, "steady")) return 1;
    if (len >= 2 && name[len - 2] == '_' &&
        (name[len - 1] == 'a' || name[len - 1] == 'b'))
        return 1;
    return 0;
}

static int set_partitions_ro(void)
{
    DIR *dir = opendir("/dev/block/by-name");
    if (!dir)
        return -1;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (!should_ro(ent->d_name))
            continue;

        char path[128];
        snprintf(path, sizeof(path), "/dev/block/by-name/%s", ent->d_name);

        int fd = open(path, O_RDONLY);
        if (fd < 0)
            continue;

        struct stat st;
        if (fstat(fd, &st) == 0 && S_ISBLK(st.st_mode)) {
            int on = 1;
            ioctl(fd, BLKROSET, &on);
        }
        close(fd);
    }

    closedir(dir);
    return 0;
}

static int late_load_running(void)
{
    DIR *d = opendir("/proc");
    if (!d)
        return 0;
    struct dirent *e;
    char path[64], buf[256];
    int found = 0;
    while ((e = readdir(d)) != NULL) {
        char *end;
        strtol(e->d_name, &end, 10);
        if (*end) continue;
        snprintf(path, sizeof(path), "/proc/%s/cmdline", e->d_name);
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        int n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
        if (n <= 0) continue;
        buf[n] = '\0';
        for (int i = 0; i < n; i++)
            if (!buf[i]) buf[i] = ' ';
        if (strstr(buf, "ksud") && strstr(buf, "late-load")) {
            found = 1;
            break;
        }
    }
    closedir(d);
    return found;
}

static int run_ctx(const char *ctx, char *const argv[])
{
    pid_t pid = fork();
    if (pid < 0)
        return -1;
    if (pid == 0) {
        if (ctx) {
            int fd = open("/proc/self/attr/exec", O_WRONLY);
            if (fd >= 0) { write(fd, ctx, strlen(ctx)); close(fd); }
        }
        execv(argv[0], argv);
        _exit(127);
    }
    int status;
    waitpid(pid, &status, 0);
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

static void touch(const char *path)
{
    int fd = open(path, O_CREAT | O_WRONLY, 0666);
    if (fd >= 0)
        close(fd);
}

/* Mark every installed module disabled before ksud runs. A broken module
 * otherwise loads on the next boot and bootloops the device. */
static int disable_modules(void)
{
    DIR *dir = opendir(MODULES_DIR);
    if (!dir)
        return -1;

    struct dirent *ent;
    while ((ent = readdir(dir))) {
        if (ent->d_name[0] == '.')
            continue;
        char path[256];
        snprintf(path, sizeof(path), MODULES_DIR "/%s/disable", ent->d_name);
        touch(path);
    }
    closedir(dir);
    return 0;
}


/* ---------------------------------------------------------------------------
 * The uid-0 session bridge
 * ---------------------------------------------------------------------------
 *
 * Why this exists, and why it is not `su`.
 *
 * This process runs as uid 0 inside the `u:r:init:s0` domain. That is a real root
 * credential, but credentials are not a service: when bootstrap exits, root is gone and
 * the app is back to app_uid. The app used to wait 15 s for a `su` daemon to appear and
 * then report `the su daemon did not answer`. That daemon can never appear on this
 * kernel, and the reason is worth writing down, because three separate things are all
 * true at once and none of them is fixable from inside the app:
 *
 *  1. KernelSU does not ship a `su` file. `kernel/feature/sucompat.c` defines
 *     `SU_PATH "/system/bin/su"` and intercepts execve for exactly that path, replacing
 *     it with `/data/adb/ksud`. There is nothing on disk to execute or to `test -x`.
 *  2. That interception is gated on `ksu_is_allow_uid_for_current()`. Our uid is not in
 *     the allowlist, so the syscall is passed through untouched and the shell gets
 *     ENOENT — the literal `su: inaccessible or not found` in DIAGNOSTICS.
 *  3. The only uid allowed without an allowlist entry is the manager, matched by
 *     `strncmp(package, KSU_PACKAGE_NAME)` where KSU_PACKAGE_NAME is compiled into the
 *     `.ko` from `kernel/Kbuild` as `me.weishu.kernelsu`. It is not our package and no
 *     `ksud` flag can change it.
 *
 * So there is no configuration of the bundled backend that yields a persistent su daemon
 * on this device. What there *is* is this process: uid 0, alive, and willing to run
 * commands. The bridge is that: a couple of pipes and a fork per request, which is the
 * honest maximum for a temporary root and is exactly what the UI advertises when it says
 * "temporary — uid 0 for this session".
 *
 * Protocol, deliberately tiny so it cannot be the next thing that silently breaks:
 *
 *   connect to $payload_dir/bridge.sock
 *   write  "<command>\n"
 *   read   until the line "__GH_ROOT_DONE__\n"   (output is the command's output)
 *
 * One connection may carry many commands. The socket is created in the app's own
 * device-protected directory and chowned to that directory's owner, so only the app can
 * connect: SELinux is permissive at this point, and a permissive kernel does not protect
 * a world-writable socket.
 */

#define BRIDGE_DONE "__GH_ROOT_DONE__\n"

static int bridge_listen(const char *dir, char *path, size_t path_size)
{
    snprintf(path, path_size, "%s/bridge.sock", dir);
    unlink(path);

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0)
        return -1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    /* sun_path is 108 bytes on Linux. Refuse rather than silently truncate: a truncated
     * path binds to the wrong place and every later connect looks like "no root". */
    if (strlen(path) >= sizeof(addr.sun_path)) {
        close(fd);
        return -1;
    }
    memcpy(addr.sun_path, path, strlen(path) + 1);

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(fd);
        return -1;
    }

    /* Hand ownership to the app. The payload dir is created by the app, so its owner is
     * the app's uid; anything else (root-owned, or world-writable) is either unusable or
     * an open door. */
    struct stat st;
    if (stat(dir, &st) == 0) {
        if (chown(path, st.st_uid, st.st_gid) != 0)
            chmod(path, 0600);
    }
    chmod(path, 0600);

    if (listen(fd, 4) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

/* Runs one command as uid 0 and writes its output to `out`. */
static void bridge_run(const char *command, FILE *out)
{
    FILE *p = popen(command, "r");
    if (!p) {
        fprintf(out, "bridge: popen failed: %s\n", strerror(errno));
        return;
    }
    char buf[4096];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), p)) > 0)
        fwrite(buf, 1, n, out);
    int status = pclose(p);
    int code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
    /* Only report a failure. Successful output has to stay byte-clean: callers parse it
     * (`id`, `magisk -V`, module lists), and a decoration line on every reply is exactly
     * how a parser starts returning nonsense. */
    if (code != 0)
        fprintf(out, "\n[exit %d]\n", code);
}

/* Serves one connection until it closes. Returns when the client hangs up. */
static void bridge_serve(int client)
{
    FILE *in = fdopen(dup(client), "r");
    FILE *out = fdopen(client, "w");
    if (!in || !out) {
        if (in) fclose(in);
        if (out) fclose(out);
        else close(client);
        return;
    }
    setvbuf(out, NULL, _IONBF, 0);

    char line[4096];
    while (fgets(line, sizeof(line), in)) {
        size_t len = strlen(line);
        while (len && (line[len - 1] == '\n' || line[len - 1] == '\r'))
            line[--len] = '\0';
        if (len == 0)
            continue;
        if (!strcmp(line, "quit"))
            break;
        bridge_run(line, out);
        /* The app waits for this exact line rather than for EOF, so a command that
         * prints a lot of output cannot be mistaken for the end of the reply. It must
         * also start at column 0 and carry no NUL: the client compares the line, and a
         * stray byte turns every reply into a timeout. */
        fputs(BRIDGE_DONE, out);
        fflush(out);
    }

    fclose(in);
    fclose(out);
}

/* Accepts connections until the deadline passes. */
static void bridge_loop(int listen_fd, int timeout_s)
{
    time_t deadline = time(NULL) + timeout_s;
    while (time(NULL) < deadline) {
        struct timeval tv = { 5, 0 };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(listen_fd, &rfds);
        int r = select(listen_fd + 1, &rfds, NULL, NULL, &tv);
        if (r <= 0)
            continue;
        int client = accept(listen_fd, NULL, NULL);
        if (client < 0)
            continue;
        touch("/dev/dfmb0");
        bridge_serve(client);
    }
    close(listen_fd);
}

int main(int argc, char **argv)
{
    for (int i = 1; i + 1 < argc; i++) {
        if (strcmp(argv[i], "--payload-dir") == 0)
            payload_dir = argv[++i];
        else if (strcmp(argv[i], "--prefs") == 0)
            prefs_path = argv[++i];
        else if (strcmp(argv[i], "--bridge-seconds") == 0)
            bridge_timeout = atoi(argv[++i]);
    }

    touch("/dev/dfm1");
    char su_manager[256];
    int soft_reboot, disable_mods;
    if (read_prefs(su_manager, sizeof(su_manager), &soft_reboot, &disable_mods) != 0) {
        touch("/dev/dfme0");
        return 1;
    }

    touch("/dev/dfm2");
    if (adopt_zygote_env())
        touch("/dev/dfmw0");

    touch("/dev/dfm3");
    if (set_partitions_ro())
        touch("/dev/dfmw1");

    if (disable_mods) {
        touch("/dev/dfm4");
        if (disable_modules()) {
            touch("/dev/dfme1");
            return 1;
        }
    }

    touch("/dev/dfm5");    
    char ksud_stage[512];
    build_paths(ksud_stage, sizeof(ksud_stage));
    if (run_ctx(NULL, (char *[]){ ksud_stage, "late-load", "--package-name", su_manager, NULL }) == 0) {
        touch("/dev/dfm6");
        if (soft_reboot) {
            while (late_load_running()) sleep(1);
            run_ctx("u:r:ksu:s0", (char *[]){ KSUD, "soft-reboot", NULL });
        }
    } else {
        touch("/dev/dfme2");
    }

    /* `ksud` has run (or failed). Either way this process still holds uid 0, and that
     * credential is the only root this kernel will give us — see the long comment above
     * bridge_listen(). Publish it, then serve until the app is done or the cap expires. */
    char sock[600];
    int lfd = bridge_listen(payload_dir, sock, sizeof(sock));
    if (lfd >= 0) {
        touch("/dev/dfmb1");
        bridge_loop(lfd, bridge_timeout);
        unlink(sock);
    } else {
        touch("/dev/dfmbe0");
    }

    return 0;
}
