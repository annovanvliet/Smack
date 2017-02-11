/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.serverless.service.jmdns.JmDNSService;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;

/**
 * @author anno
 *
 */
public class XMPPSLConnection extends AbstractXMPPConnection implements XMPPConnection {

    private static final Logger LOGGER = Logger.getLogger(XMPPSLConnection.class.getName());

    private LLService service;
    private final LLConnectionConfiguration configuration;

    /**
     * @param build
     */
    public XMPPSLConnection(LLConnectionConfiguration configuration) {
        super(configuration);
        
        this.configuration = configuration;
        
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#isSecureConnection()
     */
    @Override
    public boolean isSecureConnection() {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendStanzaInternal(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    protected void sendStanzaInternal(Stanza packet) throws NotConnectedException, InterruptedException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendNonza(org.jivesoftware.smack.packet.Nonza)
     */
    @Override
    public void sendNonza(Nonza element) throws NotConnectedException, InterruptedException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#isUsingCompression()
     */
    @Override
    public boolean isUsingCompression() {
        // TODO Auto-generated method stub
        return false;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#connectInternal()
     */
    @Override
    protected void connectInternal() throws SmackException, IOException, XMPPException, InterruptedException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#loginInternal(java.lang.String, java.lang.String, org.jxmpp.jid.parts.Resourcepart)
     */
    @Override
    protected void loginInternal(String username, String password, Resourcepart resource)
                    throws XMPPException, SmackException, IOException, InterruptedException {

        // Start Service
        EntityJid name = JidCreate.entityBareFrom( Localpart.from(username) , Domainpart.from(configuration.getInetAddress().getHostName()));

        // Create a basic presence (only set name, and status to available)
        LLPresence presence = new LLPresence(name);
        
        service = JmDNSService.create( presence, configuration.getInetAddress());

    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#shutdown()
     */
    @Override
    protected void shutdown() {
        
        if ( service != null ) {
            try {
                service.close();
            }
            catch (IOException e) {
                LOGGER.log(Level.FINE, "Service close not succesfull", e);
            }
        }

    }

}
