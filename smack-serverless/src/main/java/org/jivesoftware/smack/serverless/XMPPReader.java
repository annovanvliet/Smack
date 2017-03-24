/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.InputStream;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;

import io.netty.channel.Channel;

/**
 * @author anno
 *
 */
public interface XMPPReader {

    /**
     * @param channel 
     * @param sink
     * @throws SmackException 
     */
    void setInput(Channel channel, InputStream stream, boolean outgoing ) throws SmackException;

    /**
     * 
     */
    void init();

    /**
     * @throws SmackException 
     * @throws NoResponseException 
     * 
     */
    void waitStreamOpened() throws InterruptedException, NoResponseException;

    /**
     * Is this stream compatible with RFC 6120? Stream attribute version is supplied.
     * 
     * If not no support for IQ and Presence
     * 
     * @return 
     */
    boolean isRFC6120Compatible();

}
