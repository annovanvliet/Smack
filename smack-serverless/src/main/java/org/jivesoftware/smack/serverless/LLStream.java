/**
 * 
 */
package org.jivesoftware.smack.serverless;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
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
     * @throws NotConnectedException 
     * 
     */
    public void openStream() throws NotConnectedException;


    /**
     * @throws NotConnectedException 
     * 
     */
    public void sendFeatures() throws NotConnectedException;


    /**
     * @param packet
     * @throws InterruptedException 
     * @throws NotConnectedException 
     */
    public void send(Stanza packet) throws InterruptedException, NotConnectedException;


    /**
     * @param packet
     * @throws NotConnectedException 
     */
    public void send(Nonza packet) throws NotConnectedException;


    /**
     * 
     */
    public void closeChannel();


    /**
     * @throws InterruptedException 
     * @throws NoResponseException 
     * @throws NotConnectedException 
     * 
     */
    public void openOutgoingStream() throws InterruptedException, NoResponseException, NotConnectedException;


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
