package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class EpollTimerExample {
    private static final Logger log = LogManager.getLogger(EpollTimerExample.class);

    private static final int PORT = 8000;
    private static final long TIMER_SEC = 2;
    private static final long PROFILER_INTERVAL_MS = 1000;

    private static final ByteBuf DEFAULT_RESPONSE = Unpooled.copiedBuffer("Hello, world!\r\n", US_ASCII);

    static {
        ClassLoader loader = PlatformDependent.getClassLoader(EpollTimerExample.class);
        NativeLibraryLoader.load("profiler", loader);
    }

    public static void main(String[] args) throws InterruptedException {
        startProfiler(PROFILER_INTERVAL_MS);

        EpollEventLoopGroup group = new EpollEventLoopGroup(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                return new Thread(() -> {
                    enableSampling();
                    runnable.run();
                });
            }
        });

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(group)
                .channel(EpollServerSocketChannel.class)
                .handler(new LoggingHandler())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new HttpServerCodec(), new HttpRequestHandler());
                    }
                });
        Channel channel = bootstrap.bind(PORT).sync().channel();

        log.info("listening on port {}", PORT);
        channel.closeFuture().sync();
    }

    private static class HttpRequestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HttpObject) {
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    log.info("{} {}", request.method(), request.uri());

                    log.info("setting timer: {} {}", TIMER_SEC, TimeUnit.SECONDS);
                    ctx.channel().eventLoop().schedule(() -> {
                        log.info("BEEP!");
                    }, TIMER_SEC, TimeUnit.SECONDS);

                    int length = DEFAULT_RESPONSE.readableBytes();
                    HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, DEFAULT_RESPONSE.retainedSlice());
                    response.headers().add(CONTENT_LENGTH, Integer.toString(length));
                    response.headers().add(CONTENT_TYPE, TEXT_PLAIN);

                    ctx.writeAndFlush(response);
                }
                ReferenceCountUtil.release(msg);
            } else {
                super.channelRead(ctx, msg);
            }
        }
    }

    private static native void startProfiler(long interval);
    private static native void enableSampling();
}
