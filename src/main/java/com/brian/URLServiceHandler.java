package com.brian;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

public class URLServiceHandler extends SimpleChannelInboundHandler<Object> {

    // We use this to buffer the request body that should contain the URL we want to shorten.
    private final StringBuilder postBody = new StringBuilder();
    private final URLCache cache;
    private HttpRequest request;


    public URLServiceHandler(URLCache cache) {
        this.cache = cache;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object req) throws Exception {
        System.err.println("XXX: Got a request");

        if (req instanceof HttpRequest) {
            request = (HttpRequest) req;
            HttpMethod method = request.method();
            if (HttpMethod.POST != method) {
                // We only accept POSTs
                respondMethodNotAllowed(context);
            }
        }

        if (req instanceof HttpContent) {

            System.err.println("XXX: Got the body");

            HttpContent httpContent = (HttpContent) req;

            // fetch the body from the request.
            ByteBuf content = httpContent.content();
            if (content != null && content.isReadable()) {
                // The body is being streamed in, so store what we get until we get it all.
                String bodyFragment = content.toString(CharsetUtil.UTF_8);
                postBody.append(bodyFragment);

                System.err.println("XXX: Got body = " + postBody.toString());

                if (req instanceof LastHttpContent) {
                    System.err.println("XXX: end of stream!");
                    // We're at the end of the stream now.

                    // TODO: Is this a URL in the body? Validate and write to cache.
                    String shortenedUrl = cache.add(postBody.toString());


                    // TODO: Write the response
                    // TODO: It will either be an error because it wasn't a URL then we sent, or a 200 with the shortened URL.
                }
            }
        }
    }

    private void respondMethodNotAllowed(ChannelHandlerContext ctx) {
        String responseMsg = HttpResponseStatus.METHOD_NOT_ALLOWED.reasonPhrase();

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

}
