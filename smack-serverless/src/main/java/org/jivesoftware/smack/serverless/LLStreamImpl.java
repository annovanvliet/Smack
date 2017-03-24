/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.util.logging.Logger;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.serverless.packet.StreamFeatures;

/**
 * @author anno
 *
 */
public class LLStreamImpl extends LLStreamModel implements LLStream {

    private static final Logger logger = Logger.getLogger(LLStreamImpl.class.getName());

    /**
     * @param connection
     * @param remotePresence
     */
    public LLStreamImpl(XMPPSLConnection connection, LLPresence remotePresence) {
        super(connection, remotePresence);
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#openOutgoingStream()
     */
    @Override
    public void openOutgoingStream() throws InterruptedException, NoResponseException {
        openStream();
        getReader().waitStreamOpened();
        
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#openStream()
     */
    @Override
    public void openStream() {
        logger.fine("openStream" );

       StreamOpen streamOpen = new StreamOpen(getRemotePresence().getServiceName(), connection.getMe(), null);
       send(streamOpen);
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#sendFeatures()
     */
    @Override
    public void sendFeatures() {
        
        StreamFeatures features = new StreamFeatures();
        send(features);

    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#send(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    public void send(Stanza packet) throws InterruptedException {
        logger.fine("send" );
        
        if ( getChannel() == null ) {
          new NotConnectedException("No Channel");
        } else {
            
            if ( !getReader().isRFC6120Compatible() ) {
                if ( packet instanceof IQ ) {
                    sendAndReplyOnIQ( (IQ) packet);
                    return;
                }    
                if ( packet instanceof Presence ) {
                    logger.finest("Probably No support for presences. Trying...");
                }
            }
            getChannel().writeAndFlush(packet);

        }
        
    }
    
    /**
     * @param packet
     * @throws InterruptedException 
     */
    private void sendAndReplyOnIQ(IQ iq) throws InterruptedException {
        logger.finest("sendAndReplyOnIQ");
        
        switch (iq.getType()) {
        case get:
            sendIQError(iq);
            break;
            
        case set:
            sendIQError(iq);
            break;

        default:
            break;
        }
        
    }

    /**
     * @param iq
     * @throws InterruptedException 
     */
    private void sendIQError(IQ iq) throws InterruptedException {
        logger.finest("sendIQError");

        IQ err = IQ.createErrorResponse(iq, XMPPError.getBuilder((
                        XMPPError.Condition.feature_not_implemented)));
        
        connection.autoRespond(err);        
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#send(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    public void send(Nonza packet) {
        
        if ( getChannel() != null ) {
            getChannel().writeAndFlush(packet);
        } else {
            new NotConnectedException("No Channel");
        }
        
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#closeChannel()
     */
    @Override
    public void closeChannel() {
        try {
            if ( getChannel() != null ) {
                getChannel().closeFuture().sync();
            } else {
                logger.fine("Closing empty channel");
            }
        }
        catch (InterruptedException e) {
            logger.warning("Close channel Interrupted");;
        }
        setChannel(null);
        
    }

}
