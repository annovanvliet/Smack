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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.LLService;
import org.jivesoftware.smack.serverless.XMPPLLConnection;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xbill.DNS.MulticastDNSUtils;
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;

import net.posick.mDNS.MulticastDNSService;
import net.posick.mDNS.ServiceInstance;
import net.posick.mDNS.ServiceName;

/**
 * Implements a LLService using JmDNS.
 *
 * @author Jonas Ådahl
 */
public class JmDNSService extends LLService {
    
    private static Logger logger = Logger.getLogger(JmDNSService.class.getName());
    //static JmmDNS jmdns = null;
    private final MulticastDNSService mDNSService;
    private ServiceInstance service = null;
    private ServiceInstance registeredService = null;
    private LLPresenceDiscoverer presenceDiscoverer;
    //private ServiceInfo serviceInfo;
    static final String SERVICE_TYPE = "_presence._tcp";

    private JmDNSService(LLPresence presence, LLPresenceDiscoverer presenceDiscoverer, XMPPLLConnection connection, MulticastDNSService aMDNSService) throws IOException {
        super(presence, presenceDiscoverer, connection);
        this.mDNSService = aMDNSService;
        this.presenceDiscoverer = presenceDiscoverer;
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     * @param connection
     * @param browseDomains 
     * @return
     * @throws XMPPException
     */
    public static LLService create(LLPresence presence, XMPPLLConnection connection, Name[] browseDomains) throws XMPPException {

        MulticastDNSService aMDNSService = null;
        try {
            aMDNSService = new MulticastDNSService();
            
            Name[] serviceTypes = new Name[browseDomains.length];
            for (int i = 0; i < browseDomains.length; i++)
            {
                serviceTypes[i] = new Name(SERVICE_TYPE, browseDomains[i]);
            }

            // Start the presence discoverer
            JmDNSPresenceDiscoverer presenceDiscoverer = new JmDNSPresenceDiscoverer( aMDNSService , serviceTypes );

            // Start the presence service
            JmDNSService service = new JmDNSService(presence, presenceDiscoverer, connection, aMDNSService);

            return service;
        }
        catch (IOException e) {
            
            if (aMDNSService != null) {
                try {
                    aMDNSService.close();
                }
                catch (IOException e1) {
                    logger.fine("closing on error:" + e.getMessage());
                }
            }
            
            throw new DNSException("create" , e);
            
        }
    }

    @Override
    public void close() throws IOException, InterruptedException {
        super.close();
        
        presenceDiscoverer.close();
        unregisterService();
        
        mDNSService.close();
        
        
//        jmdns.close();
//        jmdns = null;
    }

//    /**
//     * Start the JmDNS daemon.
//     * @throws XMPPException
//     */
//    private static void initJmDNS() {
//        if (jmdns == null) {
//            jmdns = JmmDNS.Factory.getInstance();
//
//            //give some time to start
//            try {
//                Thread.sleep(5000);
//            }                       
//            catch (InterruptedException e) {
//            }
//            
//            try {
//                logger.info(String.format("initJmDNS %1$s %2$s %3$s " , Arrays.toString(jmdns.getHostNames()), Arrays.toString(jmdns.getNames()), Arrays.toString(jmdns.getInterfaces())));
//            }
//            catch (IOException e) {
//                logger.info("" + e.getMessage());
//                
//            }
//            
//        }
//    }

    /**
     * Register the DNS-SD service with the daemon.
     * 
     * @param presence
     * @return
     * @throws XMPPException
     */
    @Override
    protected EntityBareJid registerService(LLPresence presence) throws XMPPException {
        logger.fine("registerService");

        try {
            ServiceName serviceName = jidToServiceName(presence.getServiceName(), presence.getDomain());

            Name host = presence.getHost();

            if (host == null || host.length() == 0) {
                String machineName = MulticastDNSUtils.getMachineName();
                if (machineName == null) {
                    machineName = MulticastDNSUtils.getHostName();
                }

                machineName = (machineName.endsWith(".") ? machineName.substring(0, machineName.length() -1 ) : machineName );
                // A host name with "." is illegal. so strip off everything and append .local.
                if ( machineName.contains(".") ) {
                    machineName = machineName.substring(0, machineName.indexOf('.'));
                }

                host = new Name(machineName + "." + presence.getDomain());
            }

            InetAddress[] addresses = null;
            try {
                addresses = InetAddress.getAllByName(host.toString());
            }
            catch (UnknownHostException e) {
                throw new DNSException("Failed to register DNS-SD Service", e);
            }

            if (addresses == null || addresses.length == 0) {
                addresses = MulticastDNSUtils.getLocalAddresses();
            }

            service = new ServiceInstance(serviceName, 0, 0, presence.getPort(), host, addresses, presence.toMap());

            registeredService = mDNSService.register(service);
            if (registeredService != null) {
                logger.info("Services Successfully Registered: " + registeredService);

                EntityBareJid realizedServiceName = serviceNameToJid(registeredService.getName());

                return realizedServiceName;

            }
            else {
                throw new DNSException("Failed to register DNS-SD Service");
            }
        }
        catch (TextParseException e) {
            throw new DNSException("registerService", e);

        }
        catch (IOException e) {
            throw new DNSException("registerService", e);

        }

        // serviceInfo = ServiceInfo.create(SERVICE_TYPE,
        // presence.getServiceName().toString(), presence.getPort(), 0, 0, presence.toMap());
        //
        // try {
        // jmdns.registerService(serviceInfo);
        //
        // ServiceInfo[] result = jmdns.getServiceInfos(SERVICE_TYPE, serviceInfo.getName());
        // logger.fine("getServiceInfos:" + Arrays.toString(result));
        //
        // EntityBareJid realizedServiceName = JidCreate.entityBareFrom(serviceInfo.getName());
        //
        // return realizedServiceName;
        // }
        // catch (IOException ioe) {
        // throw new DNSException("Failed to register DNS-SD Service", ioe);
        // }
    }

    @Override
    protected void updateText(LLPresence presence) {
        logger.fine("updateText");
        
        if ( registeredService != null) {
            try {
                registeredService.addText(presence.toMap());

                mDNSService.register(registeredService);
            }
            catch (IOException e) {
                logger.log(Level.INFO, "updateText failed", e);

            }
        }

        // serviceInfo.setText(presence.toMap());
        //
        // ((JmmDNSImpl)jmdns).textValueUpdated(serviceInfo,serviceInfo.getTextBytes());
    }

    /**
     * Reregister the DNS-SD service with the daemon. Note: This method does not accommodate changes to the mDNS Service
     * Name! This method may be used to announce changes to the DNS TXT record.
     * 
     * @throws XMPPException
     */
    @Override
    protected void reannounceService() throws XMPPException {
        logger.fine("reannounceService");
        try {
            mDNSService.unregister(registeredService);
            mDNSService.register(service);

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

        try {
            if ( registeredService == null || mDNSService.unregister(registeredService)) {
                logger.info("Services Successfully Unregistered: " + service);
            }
            else {
                logger.warning("Services Unregistration Failed!");
            }
        }
        catch (IOException e) {
            logger.log(Level.INFO, "unregisterService failed", e);
        }

        // jmdns.unregisterService(serviceInfo);
        // serviceInfo = null;
    }

    @Override
    public void spam() {
        super.spam();
        logger.info("Service: " + registeredService);

        // if ( jmdns != null ) {
        // try {
        // logger.info(String.format("jMDNS %1$s %2$s %3$s " , Arrays.toString(jmdns.getHostNames()),
        // Arrays.toString(jmdns.getNames()), Arrays.toString(jmdns.getInterfaces())));
        // }
        // catch (IOException e) {
        // logger.info("" + e.getMessage());
        //
        // }
        // }

    }

    // /**
    // * JmDNS may change the name of a requested service to enforce uniqueness
    // * within its DNS cache. This helper method can be called after {@link
    // javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
    // * with the passed {@link javax.jmdns.ServiceInfo} to attempt to determine the actual service
    // * name registered. e.g: "test@example" may become "test@example (2)"
    // *
    // * @param requestedInfo the ServiceInfo instance passed to {@link
    // javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
    // * @return the unique service name actually being advertised by JmDNS. If no
    // * match found, return requestedInfo.getName()
    // */
    // private String getRealizedServiceName(ServiceInfo requestedInfo) {
    //
    // ServiceInfo[] services = jmdns.getServiceInfos(SERVICE_TYPE, requestedInfo.getName());
    //
    // if ( services.length > 0 )
    // return services[0].getName();
    //
    // return requestedInfo.getName();
    // }
    
    
    /**
     * @param name
     * @return
     * @throws XmppStringprepException 
     */
    public static EntityBareJid serviceNameToJid(ServiceName name) throws XmppStringprepException {
        return JidCreate.entityBareFrom(name.getInstance());
    }
    
    /**
     * @param name
     * @return
     * @throws TextParseException 
     * @throws XmppStringprepException 
     */
    public static ServiceName jidToServiceName( BareJid bareJid , String domain ) throws TextParseException {
        return new ServiceName(bareJid.toString() + "." + SERVICE_TYPE  + "." + domain );
    }

    
}
