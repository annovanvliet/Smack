/**
 * 
 */
package org.jivesoftware.smack.serverless.service;

import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.packet.XMPPError.Condition;

/**
 * @author anno
 *
 */
public class ServiceException extends XMPPErrorException {

    /**
     * @param string
     */
    public ServiceException(String string) {
        this(string, XMPPError.Condition.undefined_condition);
    }

    /**
     * @param string
     * @param resourceConstraint
     */
    public ServiceException(String string, Condition condition) {
        
        super(XMPPError.getBuilder(condition).setDescriptiveEnText(string));
    }

    /**
     * @param error
     */
    public ServiceException(XMPPError error) {
        super(XMPPError.getBuilder(error));
    }

}
