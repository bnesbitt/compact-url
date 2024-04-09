package com.brian;

import com.brian.cache.URLCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;

public class URLServiceHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(URLServiceHandler.class);

    // We use this to buffer the request body that should contain the URL we want to shorten.
    private final StringBuilder postBody = new StringBuilder();

    private final URLCache cache;

    private final long startTime = System.nanoTime();

    // We assign a unique UUID per request so that we can trace each transaction.
    private final UUID uuid = UUID.randomUUID();

    public URLServiceHandler(URLCache cache) {
        this.cache = cache;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object req) throws Exception {
        if (req instanceof HttpRequest httpRequest) {
            HttpMethod method = httpRequest.method();
            if (HttpMethod.POST != method && HttpMethod.GET != method) {
                // We only GET and POSTs.
                respondMethodNotAllowed(context, httpRequest);
                logTxnTime();
                return;
            }

            if (HttpMethod.GET == method) {
                handleGet(context, httpRequest);
                logTxnTime();
            }
        }

        if (req instanceof HttpContent httpContent) {
            // fetch the body from the request.
            ByteBuf content = httpContent.content();
            if (content != null && content.isReadable()) {
                handleRequestContent(context, httpContent, content);
            } else {
                // No request body found!
                var socketAddress = (InetSocketAddress) context.channel().remoteAddress();
                var ip = socketAddress.getAddress().getHostAddress();
                var port = socketAddress.getPort();
                logger.warn("[{}] The POST request from [{}]:{} did not contain a body", uuid, ip, port);

                sendErrorBodyMissing(context);
                logTxnTime();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {
            var socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            var ip = socketAddress.getAddress().getHostAddress();
            var port = socketAddress.getPort();
            logger.warn("[{}] Closing slow/idle client connection [{}]:{}", uuid, ip, port);
            logTxnTime();
            ctx.close();
        } else {
            super.exceptionCaught(ctx, cause);
            ctx.close();
        }
    }

    private void handleRequestContent(ChannelHandlerContext context,
                                      HttpContent req,
                                      ByteBuf content) {

        // The body is being streamed in, so store what we get until we get it all.
        var bodyFragment = content.toString(CharsetUtil.UTF_8);
        postBody.append(bodyFragment);

        if (req instanceof LastHttpContent) {
            // We're at the end of the stream now.
            var socketAddress = (InetSocketAddress) context.channel().remoteAddress();
            var ip = socketAddress.getAddress().getHostAddress();
            var port = socketAddress.getPort();
            var body = postBody.toString();

            logger.info("[{}] Received a post request from [{}]:{} - POST body: {}",
                    uuid, ip, port, body);

            String shortenedUrl = cache.shorten(uuid, body);
            if (shortenedUrl != null) {
                logger.info("[{}] URL {} has been encoded to {}", uuid, body, shortenedUrl);
                sendResponse(context, shortenedUrl);
            } else {
                logger.warn("[{}] Failed to encode: The POST does not contain a valid URL: {}",
                        uuid, body);
                sendErrorResponse(context, body);
            }

            logTxnTime();
        }
    }

    private void respondMethodNotAllowed(ChannelHandlerContext ctx, HttpRequest request) {
        var socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        var ip = socketAddress.getAddress().getHostAddress();
        var port = socketAddress.getPort();

        logger.warn("[{}] Received an invalid {} request from [{}]:{}",
                uuid, request.method().asciiName(), ip, port);

        var responseMsg = HttpResponseStatus.METHOD_NOT_ALLOWED.reasonPhrase();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.METHOD_NOT_ALLOWED,
                Unpooled.copiedBuffer(responseMsg, CharsetUtil.UTF_8));

        // Set the response headers.
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        // We're done, so tell the client to close the connection.
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        // Set the content length.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseMsg.length());

        // Send the response.
        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendResponse(ChannelHandlerContext ctx, String shortenedUrl) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(shortenedUrl, CharsetUtil.UTF_8));

        // Set the response headers.
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        // We're done, so tell the client to close the connection.
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        // Set the content length.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, shortenedUrl.length());

        // Send the response.
        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendErrorResponse(ChannelHandlerContext ctx, String body) {
        StringBuilder buf = new StringBuilder();
        buf.append(HttpResponseStatus.BAD_REQUEST.reasonPhrase());
        buf.append(" : The post does not contain a valid URL ");
        buf.append(body);

        String responseBody = buf.toString();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_REQUEST,
                Unpooled.copiedBuffer(responseBody, CharsetUtil.UTF_8));

        // Set the response headers.
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        // We're done, so tell the client to close the connection.
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        // Set the content length.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseBody.length());

        // Send the response.
        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendErrorBodyMissing(ChannelHandlerContext ctx) {
        StringBuilder buf = new StringBuilder();
        buf.append(HttpResponseStatus.BAD_REQUEST.reasonPhrase());
        buf.append(" : The post body is missing.");

        String responseBody = buf.toString();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.BAD_REQUEST,
                Unpooled.copiedBuffer(responseBody, CharsetUtil.UTF_8));

        // Set the response headers.
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        // We're done, so tell the client to close the connection.
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        // Set the content length.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseBody.length());

        // Send the response.
        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    private void handleGet(ChannelHandlerContext ctx, HttpRequest httpRequest) {

        // Remove the leading /
        var path = httpRequest.uri();
        if (!path.isBlank() && path.startsWith("/")) {
            path = path.substring(1);
        }

        logger.info("Checking URL cache for {}", path);

        // Check the cache for the path.
        String url = cache.getOriginalUrlFor(path);

        /*
         * If we get a valid URL back from the cache then we send a redirect.
         */
        StringBuilder buf = new StringBuilder();
        HttpResponseStatus status;
        if (null != url) {
            status = HttpResponseStatus.MOVED_PERMANENTLY;

            String redirectTemplate = """
                    <!DOCTYPE HTML>
                    <html lang="en-US">
                        <head>
                            <meta charset="UTF-8">
                            <meta http-equiv="refresh" content="0; url=%s">
                            <title>Page Redirection</title>
                        </head>
                    </html>
                    """;

            String redirectResponse = String.format(redirectTemplate, url);
            buf.append(redirectResponse);

            logger.info("[{}] Redirecting GET request {} to {}", uuid, path, url);
        } else {
            // Not found.
            status = HttpResponseStatus.NOT_FOUND;
            buf.append(HttpResponseStatus.NOT_FOUND.reasonPhrase());

            logger.warn("[{}] Cannot redirect path", uuid);
        }

        String responseBody = buf.toString();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(responseBody, CharsetUtil.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseBody.length());

        // Add the location header.
        if (null != url) {
            response.headers().set(HttpHeaderNames.LOCATION, url);
        }

        ctx.write(response);
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    private void logTxnTime() {
        long endTime = System.nanoTime();
        long totalTimeMicro = (endTime - startTime) / 1000;
        logger.info("[{}] Total transaction time: {}us", uuid, totalTimeMicro);
    }

}
