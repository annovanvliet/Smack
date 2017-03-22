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
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.JmDNSImpl;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.LLService;
import org.jivesoftware.smack.serverless.SLService;
import org.jivesoftware.smack.serverless.XMPPSLConnection;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;

/**
 * Implements a LLService using JmDNS.
 *
 * @author Jonas Ådahl
 */
public class JmDNSService2 extends SLService {
    static JmDNS jmdns = null;
    private ServiceInfo serviceInfo;
    static final String SERVICE_TYPE = "_presence._tcp.local.";

    private JmDNSService2(LLPresence presence, LLPresenceDiscoverer presenceDiscoverer, XMPPSLConnection connection) {
        super(presence, presenceDiscoverer, connection);
    }

    /**
     * Instantiate a new JmDNSService and start to listen for connections.
     *
     * @param presence the mDNS presence information that should be used.
     * @param addr the INET Address to use.
     */
    public static SLService create(LLPresence presence, InetAddress addr, XMPPSLConnection connection) throws XMPPException {
        // Start the JmDNS daemon.
        initJmDNS(addr);

        // Start the presence discoverer
        JmDNSPresenceDiscoverer presenceDiscoverer = new JmDNSPresenceDiscoverer();

        // Start the presence service
        JmDNSService2 service = new JmDNSService2(presence, presenceDiscoverer, connection);

        return service;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        super.close();
        jmdns.close();
    }

    /**
     * Start the JmDNS daemon.
     */
    private static void initJmDNS(InetAddress addr) throws XMPPException {
        try {
            if (jmdns == null) {
                if (addr == null) {
                    jmdns = JmDNS.create();
                }
                else {
                    jmdns = JmDNS.create(addr);
                }
            }
        }
        catch (IOException ioe) {
            throw new DNSException("Failed to create a JmDNS instance", ioe);
        }
    }

    protected void updateText(LLPresence presence) {
        serviceInfo.setText(presence.toMap());
    }

    /**
     * Register the DNS-SD service with the daemon.
     * @return 
     */
    protected EntityBareJid registerService(LLPresence presence ) throws XMPPException {
        serviceInfo = ServiceInfo.create(SERVICE_TYPE,
                presence.getServiceName().toString(), presence.getPort(), 0, 0, presence.toMap());
        
        try {
            jmdns.registerService(serviceInfo);
            
            EntityBareJid realizedServiceName = JidCreate.entityBareFrom( getRealizedServiceName(serviceInfo));

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
     */
    protected void reannounceService() throws XMPPException {
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
    public void makeUnavailable() {
        jmdns.unregisterService(serviceInfo);
        serviceInfo = null;
    }


    @Override
    public void spam() {
        super.spam();
        System.out.println("Service name: " + serviceInfo.getName());
    }


    /**
     * JmDNS may change the name of a requested service to enforce uniqueness
     * within its DNS cache. This helper method can be called after {@link javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
     * with the passed {@link javax.jmdns.ServiceInfo} to attempt to determine the actual service
     * name registered. e.g: "test@example" may become "test@example (2)"
     *
     * @param requestedInfo the ServiceInfo instance passed to {@link javax.jmdns.JmDNS#registerService(javax.jmdns.ServiceInfo)}
     * @return the unique service name actually being advertised by JmDNS. If no
     *         match found, return requestedInfo.getName()
     */
    private String getRealizedServiceName(ServiceInfo requestedInfo) {
        Map<String, ServiceInfo> map = ((JmDNSImpl) jmdns).getServices();
        // Check if requested service name is used verbatim
        if (map.containsKey(requestedInfo.getKey())) {
            return map.get(requestedInfo.getKey()).getName();
        }

        // The service name was altered... Search registered services
        // e.g test@example.presence._tcp.local would match test@example (2).presence._tcp.local
        for (ServiceInfo info : map.values()) {
            if (info.getName().contains(requestedInfo.getName())
                    && info.getTypeWithSubtype().equals(requestedInfo.getTypeWithSubtype())) {
                return info.getName();
            }
        }

        // No match found! Return expected name
        return requestedInfo.getName();
    }
}
