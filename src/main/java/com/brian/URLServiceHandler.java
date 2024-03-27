package com.brian;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public class URLServiceHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(URLServiceHandler.class);

    // We use this to buffer the request body that should contain the URL we want to shorten.
    private final StringBuilder postBody = new StringBuilder();
    private final URLCache cache;
    private HttpRequest request;

    private final long startTime = System.nanoTime();


    public URLServiceHandler(URLCache cache) {
        this.cache = cache;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object req) throws Exception {
        if (req instanceof HttpRequest) {
            request = (HttpRequest) req;
            HttpMethod method = request.method();
            if (HttpMethod.POST != method) {
                // We only accept POSTs
                respondMethodNotAllowed(context);
            }
        }

        if (req instanceof HttpContent) {

            var httpContent = (HttpContent) req;

            // fetch the body from the request.
            ByteBuf content = httpContent.content();
            if (content != null && content.isReadable()) {
                // The body is being streamed in, so store what we get until we get it all.
                var bodyFragment = content.toString(CharsetUtil.UTF_8);
                postBody.append(bodyFragment);

                if (req instanceof LastHttpContent) {
                    // We're at the end of the stream now.
                    var socketAddress = (InetSocketAddress) context.channel().remoteAddress();
                    var ip = socketAddress.getAddress().getHostAddress();
                    var port = socketAddress.getPort();
                    var body = postBody.toString();

                    logger.info("Received a post request from [{}]:{} - POST body: {}", ip, port, body);

                    String shortenedUrl = cache.shorten(body);
                    if (shortenedUrl != null) {
                        logger.info("URL {} has been encoded to {}", body, shortenedUrl);
                        sendResponse(context, shortenedUrl);
                    } else {
                        sendErrorResponse(context, body);
                    }

                    long endTime = System.nanoTime();
                    long totalTimeMicro = (endTime - startTime) / 1000;
                    logger.info("Total transaction time: {}us", totalTimeMicro);
                }
            }
        }
    }

    private void respondMethodNotAllowed(ChannelHandlerContext ctx) {
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

}
