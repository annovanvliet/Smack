/**
 *
 * Copyright 2009 Jonas Ådahl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack.serverless.service.jmdns;


import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceInfo;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.LLService;
import org.jivesoftware.smack.serverless.XMPPLLConnection;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;

/**
 * Implements a LLService using JmDNS.
 *
 * @author Jonas Ådahl
 */
public class JmDNSService extends LLService {
    
    private static Logger logger = Logger.getLogger(JmDNSService.class.getName());
    static JmmDNS jmdns = null;
    private ServiceInfo serviceInfo;
    static final String SERVICE_TYPE = "_presence._tcp.local.";

    private JmDNSService(LLPresence presence, LLPresenceDiscoverer presenceDiscoverer, XMPPLLConnection connection) {
        super(presence, presenceDiscoverer, connection);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     * @param connection
     * @return
     * @throws XMPPException
     */
    public static LLService create(LLPresence presence, XMPPLLConnection connection) throws XMPPException {
        // Start the JmDNS daemon.
        initJmDNS();

        // Start the presence discoverer
        JmDNSPresenceDiscoverer presenceDiscoverer = new JmDNSPresenceDiscoverer();

        // Start the presence service
        JmDNSService service = new JmDNSService(presence, presenceDiscoverer, connection);

        return service;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        super.close();
        unregisterService();
        jmdns.close();
        jmdns = null;
    }

    /**
     * Start the JmDNS daemon.
     * @throws XMPPException
     */
    private static void initJmDNS() {
        if (jmdns == null) {
            jmdns = JmmDNS.Factory.getInstance();

            //give some time to start
            try {
                Thread.sleep(5000);
            }
            catch (InterruptedException e) {
            }
        }
    }

    @Override 
    protected void updateText(LLPresence presence) {
        
        serviceInfo.setText(presence.toMap());
    }

    /**
     * Register the DNS-SD service with the daemon.
     * @param presence
     * @return 
     * @throws XMPPException
     */
    @Override 
    protected EntityBareJid registerService(LLPresence presence ) throws XMPPException {
        logger.fine("registerService");
        serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                presence.getServiceName().toString(), presence.getPort(), 0, 0, presence.toMap());
        
        try {
            jmdns.registerService(serviceInfo);
            
            ServiceInfo[] result = jmdns.getServiceInfos(SERVICE_TYPE, serviceInfo.getName());
            logger.fine("getServiceInfos:" + Arrays.toString(result));
            
            EntityBareJid realizedServiceName = JidCreate.entityBareFrom(serviceInfo.getName());

            return realizedServiceName;
        }
        catch (IOException ioe) {
            throw new DNSException("Failed to register DNS-SD Service", ioe);
        }
    }

    /**
     * Reregister the DNS-SD service with the daemon.
     *
     * Note: This method does not accommodate changes to the mDNS Service Name!
     * This method may be used to announce changes to the DNS TXT record.
     * @throws XMPPException
     */
    @Override 
    protected void reannounceService() throws XMPPException {
        logger.fine("reannounceService");
        try {
            jmdns.unregisterService(serviceInfo);
            jmdns.registerService(serviceInfo);
            // Note that because ServiceInfo objects are tracked
            // within JmDNS by service name, if that value has changed
            // we won't be able to successfully remove the 'old' service.
            // Previously, jmdns exposed the following method:
            //jmdns.reannounceService(serviceInfo);
        }
        catch (IOException ioe) {
            throw new DNSException("Exception occured when reannouncing mDNS presence.", ioe);
        }
    }

    /**
     * Unregister the DNS-SD service, making the client unavailable.
     */
    public void unregisterService() {
        logger.fine("unregisterService");
        jmdns.unregisterService(serviceInfo);
        serviceInfo = null;
    }


    @Override
    public void spam() {
        super.spam();
        logger.info("Service name: " + serviceInfo.getName());
        
        if ( jmdns != null ) {
            try {
                logger.info(String.format("jMDNS %1$s %2$s %3$s " , Arrays.toString(jmdns.getHostNames()), Arrays.toString(jmdns.getNames()), Arrays.toString(jmdns.getInterfaces())));
            }
            catch (IOException e) {
                logger.info("" + e.getMessage());
                
            }
        }
        
        
    }


//    /**
//     * JmDNS may change the name of a requested service to enforce uniqueness
//     * within its DNS cache. This helper method can be called after {@link javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
//     * with the passed {@link javax.jmdns.ServiceInfo} to attempt to determine the actual service
//     * name registered. e.g: "test@example" may become "test@example (2)"
//     *
//     * @param requestedInfo the ServiceInfo instance passed to {@link javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
//     * @return the unique service name actually being advertised by JmDNS. If no
//     *         match found, return requestedInfo.getName()
//     */
//    private String getRealizedServiceName(ServiceInfo requestedInfo) {
//        
//        ServiceInfo[] services = jmdns.getServiceInfos(SERVICE_TYPE, requestedInfo.getName());
//        
//        if ( services.length > 0 ) 
//            return services[0].getName();
//        
//        return requestedInfo.getName();
//    }
}
