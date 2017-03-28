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
    public LLStreamImpl(XMPPLLConnection connection, LLPresence remotePresence) {
        super(connection, remotePresence);
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#openOutgoingStream()
     */
    @Override
    public void openOutgoingStream() throws InterruptedException, NoResponseException, NotConnectedException {
        openStream();
        getReader().waitStreamOpened();
        
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#openStream()
     */
    @Override
    public void openStream() throws NotConnectedException {
        logger.fine("openStream" );

       StreamOpen streamOpen = new StreamOpen(getRemotePresence().getServiceName(), connection.getMe(), null);
       send(streamOpen);
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#sendFeatures()
     */
    @Override
    public void sendFeatures() throws NotConnectedException {
        
        StreamFeatures features = new StreamFeatures();
        send(features);

    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#send(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    public void send(Stanza packet) throws InterruptedException, NotConnectedException {
        logger.fine("send" );
        
        if ( getChannel() == null ) {
          throw new NotConnectedException("No Channel");
        } else {
            
            if ( !getReader().isRFC6120Compatible() ) {
                if ( packet instanceof IQ ) {
                    connection.sendToDebug(packet);
                    connection.sendAndReplyOnIQ( (IQ) packet);
                    return;
                }    
                if ( packet instanceof Presence ) {
                    logger.finest("Probably No support for presences. Trying...");
                }
            }
            getChannel().writeAndFlush(packet);

        }
        
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.serverless.LLStream#send(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    public void send(Nonza packet) throws NotConnectedException {
        
        if ( getChannel() != null ) {
            getChannel().writeAndFlush(packet);
        } else {
            throw new NotConnectedException("No Channel");
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
