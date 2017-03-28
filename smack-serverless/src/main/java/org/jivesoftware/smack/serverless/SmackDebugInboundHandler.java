/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @author anno
 *
 */
public class SmackDebugInboundHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(SmackDebugInboundHandler.class.getName());

    private final XMPPLLConnection xmppslConnection;

    public SmackDebugInboundHandler( XMPPLLConnection xmppslConnection ) {
        logger.finest("init");
        this.xmppslConnection = xmppslConnection;
    }
   
    
    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#channelRead(io.netty.channel.ChannelHandlerContext, java.lang.Object)
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.finest("channelRead");
        
        Reader reader = xmppslConnection.getDebugReader();
        ByteBuf in = (ByteBuf) msg;
        
        try {
          if (in.isReadable()) {
            int size = in.readableBytes();
            byte[] buf = new byte[size];
            in.markReaderIndex();
            in.readBytes(buf, 0, size);
            in.resetReaderIndex();
            char[] arr = new String(buf, StandardCharsets.UTF_8).toCharArray();
            reader.read(arr, 0 , arr.length);
          }
        } catch (IOException e) {
          logger.log(Level.FINE, "channelRead Error:" ,e);
        }
        ctx.fireChannelRead(msg);
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
