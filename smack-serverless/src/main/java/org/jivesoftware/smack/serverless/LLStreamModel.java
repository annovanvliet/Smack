/**
 * 
 */
package org.jivesoftware.smack.serverless;

import io.netty.channel.Channel;

/**
 * @author anno
 *
 */
public class LLStreamModel {

    private final LLPresence remotePresence;
    protected final XMPPLLConnection connection;
    
    private Channel channel = null;
    private XMPPReader reader = null;

    
    
    public LLStreamModel( XMPPLLConnection connection, LLPresence remotePresence ) {
        this.connection = connection;
        this.remotePresence = remotePresence;
    }
    
    
    /**
     * @return the remotePresence
     */
    public LLPresence getRemotePresence() {
        return remotePresence;
    }

    /**
     * @return the channel
     */
    public Channel getChannel() {
        return channel;
    }
    
    /**
     * @param channel the channel to set
     */
    public void setChannel(Channel channel) {
        this.channel = channel;
    }
    
    /**
     * @return the reader
     */
    public XMPPReader getReader() {
        return reader;
    }
    
    /**
     * @param reader the reader to set
     */
    public void setReader(XMPPReader reader) {
        this.reader = reader;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        
        return String.format("LLStream: user %1$s channel %2$s", remotePresence , channel );
    }

}
