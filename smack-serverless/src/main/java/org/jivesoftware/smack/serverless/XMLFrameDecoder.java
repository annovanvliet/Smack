/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * @author anno
 *
 */
public class XMLFrameDecoder extends ChannelInboundHandlerAdapter {

    private static final Logger log = Logger.getLogger(XMLFrameDecoder.class.getName());

    private XMPPReader reader;
    private PipedInputStream sink;
    private PipedOutputStream source;
    private final boolean outgoing;

    private final XMPPSLConnection xmppslConnection;

    private final LLPresence remotePresence;

    public XMLFrameDecoder( XMPPSLConnection xmppslConnection ) {

        log.info("init");
        this.xmppslConnection = xmppslConnection;
        this.remotePresence = null;
        this.outgoing = false;
    }

    /**
     * @param xmppslConnection2
     * @param remotePresence
     */
    public XMLFrameDecoder(XMPPSLConnection connection, LLPresence remotePresence) {
        log.info("init");
        this.xmppslConnection = connection;
        this.remotePresence = remotePresence;
        this.outgoing = true;
    }

    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#channelActive(io.netty.channel.ChannelHandlerContext)
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        
        log.info("channelActive");
        sink = new PipedInputStream();
        source = new PipedOutputStream(sink);
        
        if ( outgoing ) {
            reader = xmppslConnection.createOutgoingXMPPReader(ctx.channel(), remotePresence);
        } else {
            reader = xmppslConnection.createIncomingXMPPReader(ctx.channel());
        }
        
        reader.setInput(sink);
        
        reader.init();
        
        // TODO Auto-generated method stub
        super.channelActive(ctx);
    }
    
    
    
    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#channelRead(io.netty.channel.ChannelHandlerContext, java.lang.Object)
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.info("channelRead:" + msg);
        
        ByteBuf in = (ByteBuf) msg;
        try {
            while (in.isReadable()) { // (1)
                int kar = in.readByte();
                source.write(kar);
                System.out.print((char) kar);
            }
            source.flush();
        } finally {
            ReferenceCountUtil.release(msg); // (2)
        }
    }
    
    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#channelReadComplete(io.netty.channel.ChannelHandlerContext)
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info("channelReadComplete");
        // TODO Auto-generated method stub
        super.channelReadComplete(ctx);
    }
    
    /* (non-Javadoc)
     * @see io.netty.channel.ChannelInboundHandlerAdapter#exceptionCaught(io.netty.channel.ChannelHandlerContext, java.lang.Throwable)
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { // (4)
        // Close the connection when an exception is raised.
        log.log(Level.WARNING, "unknown error"  , cause);
        ctx.close();
    }    
}
