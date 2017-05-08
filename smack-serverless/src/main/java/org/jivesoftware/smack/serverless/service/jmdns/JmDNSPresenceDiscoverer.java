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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.ResolverListener;

import net.posick.mDNS.Browse;
import net.posick.mDNS.DNSSDListener;
import net.posick.mDNS.MulticastDNSService;
import net.posick.mDNS.ServiceInstance;


/**
 * An implementation of LLPresenceDiscoverer using JmDNS.
 *
 * @author Jonas Ådahl
 */
class JmDNSPresenceDiscoverer extends LLPresenceDiscoverer {
    
    private static final Logger logger = Logger.getLogger(JmDNSPresenceDiscoverer.class.getName());
    
    protected static final int SERVICE_REQUEST_TIMEOUT = 10000; 
    protected MulticastDNSService mDNSService;

    private final Object discoverId;

    @SuppressWarnings("resource")
    JmDNSPresenceDiscoverer( MulticastDNSService mDNSService, Name[] serviceTypes ) throws XMPPException, IOException {
        
        discoverId = mDNSService.startServiceDiscovery( new Browse(serviceTypes), new PresenceServiceListener());
        this.mDNSService = mDNSService;
        
//        jmdns = JmDNSService.jmdns;
//        if (jmdns == null)
//            throw new DNSException( "Failed to fully initiate mDNS daemon.");
//
//        jmdns.addServiceListener(JmDNSService.SERVICE_TYPE, new PresenceServiceListener());
//        
//        ServiceInfo[] res = jmdns.list(JmDNSService.SERVICE_TYPE);
//        logger.info("found:" + res.length);
    }
    
    public void close() {
        try {
            boolean result = mDNSService.stopServiceDiscovery(discoverId);
            logger.finest("stopServiceDiscovery of " + discoverId + (result ? " succesfull" : " failed"));
        }
        catch (IOException e) {
            logger.log(Level.INFO,"On close Browsing services",e);
        }
    }
 
//    /**
//     * Convert raw TXT fields to a list of strings.
//     * The raw TXT fields are encoded as follows:
//     * <ul>
//     *  <li>Byte 0 specifies the length of the first field (which starts at byte 1).</li>
//     *  <li>If the last byte of the previous field is the last byte of the array,
//     *  all TXT fields has been read.</li>
//     *  <li>If there are more bytes following, the next byte after the last of the
//     *  previous field specifies the length of the next field.</li>
//     * </ul>
//     *
//     * @param bytes raw TXT fields as an array of bytes.
//     * @return TXT fields as a list of strings.
//     */
//    private static List<String> TXTToList(byte[] bytes) {
//        List<String> list = new LinkedList<String>();
//            int size_i = 0;
//            while (size_i < bytes.length) {
//                int s = (int)(bytes[size_i]);
//                try {
//                    list.add(new String(bytes, ++size_i, s, "UTF-8"));
//                } catch (UnsupportedEncodingException uee) {
//                    // ignore
//                }
//                size_i += s;
//            }
//        return list;
//    }
//
//    /**
//     * Convert a TXT field list bundled with a '_presence._tcp' service to a
//     * Map of Strings to Strings. The TXT field list looks as following:
//     * "key=value" which is converted into the a map of (key, value).
//     *
//     * @param strings the TXT fields.
//     * @return a map from key to value.
//     */
//    private static Map<String,String> TXTListToXMPPRecords(List<String> strings) {
//        Map<String,String> records = new HashMap<>(strings.size());
//        for (String s : strings) {
//            String[] record = s.split("=", 2);
//            // check if valid
//            if (record.length == 2) {
//                records.put(record[0], record[1]);
//            }
//        }
//        return records;
//    }

    /**
     * Implementation of a JmDNS ServiceListener. Listens to service resolved and
     * service information resolved events.
     */
    private class PresenceServiceListener implements DNSSDListener, ResolverListener {
//        @Override
//        public void serviceAdded(ServiceEvent event) {
//            logger.finest("serviceAdded:" + event);
//            // XXX
//            // To reduce network usage, we should only request information
//            // when needed.
//            new RequestInfoThread(event).start();
//        }
//        
//        @Override
//        public void serviceRemoved(ServiceEvent event) {
//            logger.fine("serviceRemoved:" + event);
//            try {
//                presenceRemoved(JidCreate.from(event.getName()));
//            }
//            catch (XmppStringprepException e) {
//                logger.warning("service not Removed:" + e.getMessage());
//            }
//        }
//        
//        @Override
//        public void serviceResolved(ServiceEvent event) {
//            logger.finest("event:" + event);
//            logger.finest("ServiceInfo:" + event.getInfo());
//            try {
//                EntityBareJid jid = JidCreate.entityBareFrom(event.getName());
//                Map<String, String> records = TXTListToXMPPRecords(TXTToList(event.getInfo().getTextBytes()));
//                
//                logger.fine("serviceResolved:" + jid + " - TXT:" + records);
//                
//                presenceInfoAdded( jid ,
//                        new LLPresence(jid,
//                            event.getInfo().getHostAddresses(), event.getInfo().getPort(),records));
//                
//                
//            }
//            catch (XmppStringprepException e) {
//                logger.warning("service not Added:" + e.getMessage());
//            }
//        }
//
//        private class RequestInfoThread extends Thread {
//            ServiceEvent event;
//
//            RequestInfoThread(ServiceEvent event) {
//                this.event = event;
//            }
//
//            public void run() {
//                jmdns.requestServiceInfo(event.getType(), event.getName(),
//                        true, SERVICE_REQUEST_TIMEOUT);
//            }
//        }

        /* (non-Javadoc)
         * @see net.posick.mDNS.DNSSDListener#serviceDiscovered(java.lang.Object, net.posick.mDNS.ServiceInstance)
         */
        @Override
        public void serviceDiscovered(Object id, ServiceInstance service) {
            logger.finest("serviceDiscovered:" + service);

            try {
                EntityBareJid jid = JmDNSService.serviceNameToJid(service.getName());
                
                presenceInfoAdded( jid ,
                        new LLPresence(jid, service.getName().getDomain(),
                                        service.getAddresses(), service.getHost(), service.getPort(), service.getTextAttributes()));
                
            }
            catch (XmppStringprepException e) {
                logger.warning("service not Added:" + e.getMessage());
            }
            
        }

        /* (non-Javadoc)
         * @see net.posick.mDNS.DNSSDListener#serviceRemoved(java.lang.Object, net.posick.mDNS.ServiceInstance)
         */
        @Override
        public void serviceRemoved(Object id, ServiceInstance service) {
            logger.finest("serviceRemoved");
            try {
                presenceRemoved(JmDNSService.serviceNameToJid(service.getName()));
            }
            catch (XmppStringprepException e) {
                logger.warning("service not Removed:" + e.getMessage());
            }
            
        }

        /* (non-Javadoc)
         * @see net.posick.mDNS.DNSSDListener#receiveMessage(java.lang.Object, org.xbill.DNS.Message)
         */
        @Override
        public void receiveMessage(Object id, Message m) {
            //logger.finest("receiveMessage");
            // TODO Auto-generated method stub
            
        }

        /* (non-Javadoc)
         * @see net.posick.mDNS.DNSSDListener#handleException(java.lang.Object, java.lang.Exception)
         */
        @Override
        public void handleException(Object id, Exception e) {
            logger.finest("handleException");
            if (!(e instanceof IOException && "no route to host".equalsIgnoreCase(e.getMessage())))
            {
                logger.log(Level.INFO,"Browsing services",e);
            }
            
        }
    }

}
