/**
 * 
 */
package org.jivesoftware.smack.serverless.service.jmdns;

import java.io.IOException;

import org.jivesoftware.smack.XMPPException;

/**
 * @author anno
 *
 */
public class DNSException extends XMPPException {
    
    private static final long serialVersionUID = -2557291528131452686L;

    /**
     * @param string
     * @param ioe
     */
    public DNSException(String string, IOException ioe) {
        super(string , ioe); //, XMPPError.Condition.undefined_condition
        
    }

    /**
     * @param string
     */
    public DNSException(String string) {
        super(string);
    }


}
