/**
 * 
 */
package org.jivesoftware.smack.serverless;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Stanza;

import io.netty.channel.Channel;

/**
 * A model object representing a Stream to a remote Presense.
 * 
 * When fully iniatated, it has an reference to a channel and a reader. 
 * 
 * @author anno
 *
 */
public interface LLStream {
    

    /**
     * 
     */
    public void openStream();


    /**
     * 
     */
    public void sendFeatures();


    /**
     * @param packet
     */
    public void send(Stanza packet);


    /**
     * @param packet
     */
    public void send(Nonza packet);


    /**
     * 
     */
    public void closeChannel();


    /**
     * @throws InterruptedException 
     * @throws SmackException 
     * @throws NoResponseException 
     * 
     */
    public void openOutgoingStream() throws InterruptedException, NoResponseException;


    /**
     * @return
     */
    public XMPPReader getReader();


    /**
     * @return
     */
    public LLPresence getRemotePresence();


    /**
     * @param channel
     */
    public void setChannel(Channel channel);


    /**
     * @param reader
     */
    public void setReader(XMPPReader reader);

}
