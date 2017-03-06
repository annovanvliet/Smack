/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.InputStream;
import java.io.PipedInputStream;

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

}
