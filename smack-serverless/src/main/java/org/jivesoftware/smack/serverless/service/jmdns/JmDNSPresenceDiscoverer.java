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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;


/**
 * An implementation of LLPresenceDiscoverer using JmDNS.
 *
 * @author Jonas Ådahl
 */
class JmDNSPresenceDiscoverer extends LLPresenceDiscoverer {
    
    private static final Logger logger = Logger.getLogger(JmDNSPresenceDiscoverer.class.getName());
    
    protected static final int SERVICE_REQUEST_TIMEOUT = 10000; 
    protected static JmDNS jmdns;

    JmDNSPresenceDiscoverer() throws XMPPException {
        jmdns = JmDNSService2.jmdns;
        if (jmdns == null)
            throw new DNSException( "Failed to fully initiate mDNS daemon.");

        jmdns.addServiceListener(JmDNSService.SERVICE_TYPE, new PresenceServiceListener());
    }

    /**
     * Convert raw TXT fields to a list of strings.
     * The raw TXT fields are encoded as follows:
     * <ul>
     *  <li>Byte 0 specifies the length of the first field (which starts at byte 1).</li>
     *  <li>If the last byte of the previous field is the last byte of the array,
     *  all TXT fields has been read.</li>
     *  <li>If there are more bytes following, the next byte after the last of the
     *  previous field specifies the length of the next field.</li>
     * </ul>
     *
     * @param bytes raw TXT fields as an array of bytes.
     * @return TXT fields as a list of strings.
     */
    private static List<String> TXTToList(byte[] bytes) {
        List<String> list = new LinkedList<String>();
            int size_i = 0;
            while (size_i < bytes.length) {
                int s = (int)(bytes[size_i]);
                try {
                    list.add(new String(bytes, ++size_i, s, "UTF-8"));
                } catch (UnsupportedEncodingException uee) {
                    // ignore
                }
                size_i += s;
            }
        return list;
    }

    /**
     * Convert a TXT field list bundled with a '_presence._tcp' service to a
     * Map of Strings to Strings. The TXT field list looks as following:
     * "key=value" which is converted into the a map of (key, value).
     *
     * @param strings the TXT fields.
     * @return a map from key to value.
     */
    private static Map<String,String> TXTListToXMPPRecords(List<String> strings) {
        Map<String,String> records = new HashMap<>(strings.size());
        for (String s : strings) {
            String[] record = s.split("=", 2);
            // check if valid
            if (record.length == 2) {
                records.put(record[0], record[1]);
            }
        }
        return records;
    }

    /**
     * Implementation of a JmDNS ServiceListener. Listens to service resolved and
     * service information resolved events.
     */
    private class PresenceServiceListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            logger.fine("serviceAdded");
            // XXX
            // To reduce network usage, we should only request information
            // when needed.
            new RequestInfoThread(event).start();
        }
        
        @Override
        public void serviceRemoved(ServiceEvent event) {
            logger.fine("serviceRemoved");
            try {
                presenceRemoved(JidCreate.from(event.getName()));
            }
            catch (XmppStringprepException e) {
                logger.warning("service not Removed:" + e.getMessage());
            }
        }
        
        @Override
        public void serviceResolved(ServiceEvent event) {
            try {
                EntityBareJid jid = JidCreate.entityBareFrom(event.getName());
                Map<String, String> records = TXTListToXMPPRecords(TXTToList(event.getInfo().getTextBytes()));
                
                logger.fine("serviceResolved:" + jid + " - TXT:" + records);
                
                presenceInfoAdded( jid ,
                        new LLPresence(jid,
                            event.getInfo().getHostAddress(), event.getInfo().getPort(),records));
                
                
            }
            catch (XmppStringprepException e) {
                logger.warning("service not Added:" + e.getMessage());
            }
        }

        private class RequestInfoThread extends Thread {
            ServiceEvent event;

            RequestInfoThread(ServiceEvent event) {
                this.event = event;
            }

            public void run() {
                jmdns.requestServiceInfo(event.getType(), event.getName(),
                        true, SERVICE_REQUEST_TIMEOUT);
            }
        }
    }
}
