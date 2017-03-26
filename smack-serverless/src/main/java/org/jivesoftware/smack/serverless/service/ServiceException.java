/**
 * 
 */
package org.jivesoftware.smack.serverless.service;

import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.packet.XMPPError.Condition;

/**
 * @author anno
 */
public class ServiceException extends XMPPErrorException {

    private static final long serialVersionUID = 6059975543086870735L;

    /**
     * @param string
     */
    public ServiceException(String string) {
        this(string, XMPPError.Condition.undefined_condition);
    }

    /**
     * @param string
     * @param condition
     */
    public ServiceException(String string, Condition condition) {

        super(null, XMPPError.getBuilder(condition).setDescriptiveEnText(string).build());
    }

    /**
     * @param error
     */
    public ServiceException(XMPPError error) {
        super(null, XMPPError.getBuilder(error).build());
    }

}
