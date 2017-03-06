/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.util.logging.Logger;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.serverless.packet.StreamFeatures;

/**
 * @author anno
 *
 */
public class LLStreamImpl extends LLStreamModel implements LLStream {

    private static final Logger LOGGER = Logger.getLogger(LLStreamImpl.class.getName());

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
        LOGGER.fine("openStream" );

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
    public void send(Stanza packet) {
        LOGGER.fine("send" );
        
        if ( getChannel() != null ) {
            getChannel().writeAndFlush(packet);
        } else {
            new NotConnectedException("No Channel");
        }
        
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
                LOGGER.fine("Closing empty channel");
            }
        }
        catch (InterruptedException e) {
            LOGGER.warning("Close channel Interrupted");;
        }
        setChannel(null);
        
    }

}
