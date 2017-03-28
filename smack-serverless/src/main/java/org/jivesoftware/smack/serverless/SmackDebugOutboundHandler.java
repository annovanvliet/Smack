/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * @author anno
 *
 */
public class SmackDebugOutboundHandler extends ChannelOutboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(SmackDebugOutboundHandler.class.getName());

    private final XMPPLLConnection xmppslConnection;

    public SmackDebugOutboundHandler( XMPPLLConnection xmppslConnection ) {
        logger.finest("init");
        this.xmppslConnection = xmppslConnection;
    }

    
    /*
     * (non-Javadoc)
     * @see io.netty.channel.ChannelOutboundHandlerAdapter#write(io.netty.channel. ChannelHandlerContext,
     * java.lang.Object, io.netty.channel.ChannelPromise)
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf out = (ByteBuf) msg;
        Writer source = xmppslConnection.getDebugWriter();
        
        if (out.isReadable()) {
            int size = out.readableBytes();
            byte[] buf = new byte[size];
            out.markReaderIndex();
            out.readBytes(buf, 0, size);
            out.resetReaderIndex();
            source.write(new String(buf, StandardCharsets.UTF_8).toCharArray());
            source.flush();
        }
        
        ctx.write(msg, promise);
    }
    
    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#exceptionCaught(io.netty.channel.ChannelHandlerContext, java.lang.Throwable)
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
        // Close the connection when an exception is raised.
        logger.log(Level.WARNING, "unknown error"  , cause);
        ctx.pipeline().remove(ctx.name());
    }    

}
