#include <jni.h>
#include <sys/time.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/syscall.h>

#define LOG(tid, level, format, ...) \
    do { \
        struct timeval tv; \
        struct tm tm; \
        gettimeofday(&tv, NULL); \
        localtime_r(&tv.tv_sec, &tm); \
        fprintf(stderr, "%02d:%02d:%02d.%03d [" level "] (%u) PROFILER: " format "\n", \
                tm.tm_hour, tm.tm_min, tm.tm_sec, (int) tv.tv_usec / 1000, tid, \
                ##__VA_ARGS__); \
    } while(0)

#define DEBUG(tid, format, ...) LOG(tid, "DEBUG", format, ##__VA_ARGS__)
#define ERROR(tid, format, ...) LOG(tid, "ERROR", format, ##__VA_ARGS__)

#define MAX_SAMPLED_THREADS 100

static pid_t sampled[MAX_SAMPLED_THREADS];
static int n_sampled = 0;

static void sighandler_alarm(int sig, siginfo_t *info, void *context)
{
    pid_t tid;
    sigset_t ss;
    int rc, i;

    tid = (pid_t) syscall(SYS_gettid);

    for (i = 0; i < n_sampled; i++) {
        if (sampled[i] == tid) {
            DEBUG(tid, "woke up, taking a sample");
            return;
        }
    }

    DEBUG(tid, "sampling disabled for this thread, block and pause ...");

    sigemptyset(&ss);
    sigaddset(&ss, SIGALRM);

    rc = sigprocmask(SIG_BLOCK, &ss, NULL);
    if (rc < 0) {
        ERROR(tid, "could not block signal: %s", strerror(errno));
    } else {
        pause();
    }
}

static void sighandler_terminate(int sig, siginfo_t *info, void *context)
{
    abort();
}

JNIEXPORT void JNICALL Java_org_example_EpollTimerExample_startProfiler(JNIEnv *env, jclass clazz, jlong interval)
{
    struct sigaction sa;
    struct itimerval it;
    sigset_t ss;
    pid_t tid;
    int rc;

    tid = (pid_t) syscall(SYS_gettid);

    DEBUG(tid, "setting up signal handlers");

    sa.sa_sigaction = sighandler_alarm;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);

    rc = sigaction(SIGALRM, &sa, NULL);
    if (rc < 0) {
        ERROR(tid, "could not set up signal handler: %s", strerror(errno));
    }

    sa.sa_sigaction = sighandler_terminate;

    rc = sigaction(SIGTERM, &sa, NULL);
    if (rc < 0) {
        ERROR(tid, "could not set up signal handler: %s", strerror(errno));
    }
    rc = sigaction(SIGINT, &sa, NULL);
    if (rc < 0) {
        ERROR(tid, "could not set up signal handler: %s", strerror(errno));
    }

    DEBUG(tid, "arming timer with %ldms interval", interval);

    it.it_interval.tv_sec = interval / 1000;
    it.it_interval.tv_usec = interval % 1000 * 1000;
    it.it_value = it.it_interval;

    rc = setitimer(ITIMER_REAL, &it, NULL);
    if (rc < 0) {
        ERROR(tid, "could not set itimer: %s", strerror(errno));
    }
}

JNIEXPORT void JNICALL Java_org_example_EpollTimerExample_enableSampling(JNIEnv *env, jclass clazz)
{
    pid_t tid;

    tid = (pid_t) syscall(SYS_gettid);

    DEBUG(tid, "enabling sampling");

    sampled[n_sampled++] = tid;
}
