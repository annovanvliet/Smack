/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.util.logging.Logger;

import org.jivesoftware.smack.packet.Nonza;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;

/**
 * @author anno
 *
 */
public class XmppNonzaDecoder extends MessageToByteEncoder<Nonza> {

    private static final Logger LOGGER = Logger.getLogger(XmppNonzaDecoder.class.getName());


    /* (non-Javadoc)
     * @see io.netty.handler.codec.MessageToByteEncoder#encode(io.netty.channel.ChannelHandlerContext, java.lang.Object, io.netty.buffer.ByteBuf)
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Nonza msg, ByteBuf out) throws Exception {
        LOGGER.fine("encode:" + msg );
        
        out.writeCharSequence(msg.toXML(), CharsetUtil.UTF_8);
        
    }
    
    
    

}
