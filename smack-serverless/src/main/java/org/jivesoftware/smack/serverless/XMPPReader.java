/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.InputStream;
import java.io.PipedInputStream;

import org.jivesoftware.smack.SmackException;

/**
 * @author anno
 *
 */
public interface XMPPReader {

    /**
     * @param sink
     * @throws SmackException 
     */
    void setInput(InputStream stream) throws SmackException;

    /**
     * 
     */
    void init();

}
