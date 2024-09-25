An example Netty server with a native profiler component, that sets a signal timer, and interrupts Epoll event loop threads every second. This is a test set up to reproduce an issue with delayed Epoll scheduled tasks, described in https://github.com/netty/netty/issues/14368.

To reproduce the bug:

1. Build the package on a Linux x86\_64 or arm\_64 Linux platform:
```
mvn package
```
2. Run the app:
```
java -jar lib/epoll-timer-example-1.0-SNAPSHOT.jar
```
3. Wait until it blocks all non event loop threads, and starts waking up on a Netty's Epoll event loop thread:
```
15:19:17.918 [DEBUG] (403620) PROFILER: setting up signal handlers
15:19:17.918 [DEBUG] (403620) PROFILER: arming timer with 1000ms interval
15:19:17.953 [DEBUG] (403649) PROFILER: enabling sampling
15:19:17.958 [INFO] (main) org.example.EpollTimerExample: listening on port 8000
15:19:18.918 [DEBUG] (403619) PROFILER: sampling disabled for this thread, block and pause ...
15:19:19.918 [DEBUG] (403620) PROFILER: sampling disabled for this thread, block and pause ...
15:19:20.918 [DEBUG] (403621) PROFILER: sampling disabled for this thread, block and pause ...
...
15:19:46.918 [DEBUG] (403649) PROFILER: woken up, taking a sample
15:19:47.918 [DEBUG] (403649) PROFILER: woken up, taking a sample
15:19:48.918 [DEBUG] (403649) PROFILER: woken up, taking a sample
...
```
4. Run an HTTP request to http://localhost:8000/
5. Watch the logs for "setting timer" message, and see how it never goes off because the event loop wait operation is interrupted and restarted every second.
```
...
15:29:43.537 [INFO] (Thread-0) org.example.EpollTimerExample: GET /
15:29:43.537 [INFO] (Thread-0) org.example.EpollTimerExample: setting timer: 2 SECONDS
15:29:43.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
15:29:44.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
15:29:45.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
...
```
6. The scheduled task only gets executed when there are some I/O (non-timer) events in the Epoll interest group. For example, when a new HTTP request comes in, it generates a read event on the socket, and completes the `epoll_wait` call successfully. This triggers the last scheduled timer, but it blocks again on the next one:
```
...
15:29:58.811 [INFO] (Thread-0) org.example.EpollTimerExample: BEEP!
15:29:58.811 [INFO] (Thread-0) org.example.EpollTimerExample: GET /
15:29:58.812 [INFO] (Thread-0) org.example.EpollTimerExample: setting timer: 2 SECONDS
15:29:58.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
15:29:59.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
15:30:00.989 [DEBUG] (422298) PROFILER: woken up, taking a sample
...
```

