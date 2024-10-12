An example Netty server with a native profiler component, that sets a signal timer, and interrupts Epoll event loop threads every second. This is a test set up to reproduce an issue with delayed Epoll scheduled tasks, described in https://github.com/netty/netty/issues/14368.

To reproduce the bug:

1. Build the package on a Linux x86\_64 or arm\_64 platform:
```
mvn package
```
2. Run the app:
```
java -jar lib/epoll-timer-example-1.0-SNAPSHOT.jar
```
3. Wait until it blocks all non event loop threads, and starts waking up on a Netty's Epoll event loop thread:
```
11:33:59.363 [DEBUG] (3967243) PROFILER: setting up signal handlers
11:33:59.363 [DEBUG] (3967243) PROFILER: arming timer with 1000ms interval
11:33:59.397 [DEBUG] (3967272) PROFILER: enabling sampling
11:33:59.402 [INFO] (main) org.example.EpollTimerExample: listening on port 8000
11:34:00.363 [DEBUG] (3967242) PROFILER: sampling disabled for this thread, block and pause ...
11:34:01.363 [DEBUG] (3967243) PROFILER: sampling disabled for this thread, block and pause ...
11:34:02.364 [DEBUG] (3967244) PROFILER: sampling disabled for this thread, block and pause ...
...
11:34:28.364 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:29.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:30.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
...
```
4. Run an HTTP request to http://localhost:8000/
5. Watch the logs for "setting timer" message, and see how it never goes off because the event loop wait operation is interrupted and restarted every second.
```
...
11:34:31.841 [INFO] (Thread-0) org.example.EpollTimerExample: GET /ping
11:34:31.841 [INFO] (Thread-0) org.example.EpollTimerExample: setting timer: 2 SECONDS
11:34:32.364 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:33.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:34.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
...
```
6. The scheduled task only gets executed when there are some I/O (non-timer) events in the Epoll interest group. For example, when a new HTTP request comes in, it generates a read event on the socket, and completes the `epoll_wait` call successfully. This triggers the last scheduled timer, but it blocks again on the next one:
```
...
11:34:37.911 [INFO] (Thread-0) org.example.EpollTimerExample: BEEP! after 6069ms
11:34:37.912 [INFO] (Thread-0) org.example.EpollTimerExample: GET /ping
11:34:37.912 [INFO] (Thread-0) org.example.EpollTimerExample: setting timer: 2 SECONDS
11:34:38.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:39.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
11:34:40.363 [DEBUG] (3967272) PROFILER: woke up, taking a sample
...
```
7. For reference: re-run the application with disabled Epoll wait threshold, and see how the 2 second timer goes off after every HTTP request:
```
java -Dio.netty.channel.epoll.epollWaitThreshold=0 -jar lib/epoll-timer-example-1.0-SNAPSHOT.jar
```
