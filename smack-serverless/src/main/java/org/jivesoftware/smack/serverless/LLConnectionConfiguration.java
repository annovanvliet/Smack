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

package org.jivesoftware.smack.serverless;

import java.net.InetAddress;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * Link-local connection configuration settings. Two general cases exists, one where the we want to connect to a remote
 * peer, and one when o remote peer has connected to us.
 */
public class LLConnectionConfiguration extends ConnectionConfiguration implements Cloneable {
    private static final String SERVICE_NAME = "locallink";

    private final LLPresence localPresence;
    //private final Socket socket;

    /**
     * Holds the socket factory that is used to generate the socket in the connection
     */
    private final InetAddress inetAddress;
    private final String bindName;

    /**
     * Configuration used for connecting to remote peer.
     * 
     * @param builder
     */
    public LLConnectionConfiguration(Builder builder) {
        super(builder);
        this.localPresence = builder.localPresence;
        //this.socket = builder.socket;
        this.inetAddress = builder.inetAddress;
        this.bindName = builder.bindName;
    }

    // /**
    // * Instantiate a link-local configuration when the connection is acting as
    // * the host.
    // *
    // * @param local the local link-local presence class.
    // * @param remoteSocket the socket which the new connection is assigned to.
    // */
    // public LLConnectionConfiguration(LLPresence local, Socket remoteSocket) {
    // super(builder);
    // this.localPresence = local;
    // this.socket = remoteSocket;
    // }
    //
    // @Override
    // public void setServiceName(String serviceName) {
    // // ConnectionConfiguration#setServiceName extracts the domain from the serviceName
    // // e.g "david@guardian" -> "guardian"
    // // This is not the behavior we want for XEP-0174 clients
    // this.serviceName = serviceName;
    // }

    /**
     * Return this clients link-local presence information.
     * 
     * @return this clients link-local presence information.
     */
    public LLPresence getLocalPresence() {
        return localPresence;
    }

    /**
     * @return
     */
    public InetAddress getInetAddress() {
        return inetAddress;
    }
    
    /**
     * @return the bindName
     */
    public String getBindName() {
        return bindName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ConnectionConfiguration.Builder<Builder, LLConnectionConfiguration> {

        private final DomainBareJid LOCAL_DOMAIN;
        private LLPresence localPresence;
        //private Socket socket;
        private InetAddress inetAddress;
        private String bindName;

        private Builder() {
            try {
                LOCAL_DOMAIN = JidCreate.domainBareFrom("local");
            }
            catch (XmppStringprepException e) {
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public LLConnectionConfiguration build() {
            return new LLConnectionConfiguration(this);
        }

        @Override
        protected Builder getThis() {
            return this;
        }

        /**
         * LLPresence for the local user
         * 
         * @param localPresence the localPresence to set
         * @return this
         */
        public Builder setLocalPresence(LLPresence localPresence) {
            this.localPresence = localPresence;
            setXmppDomain(LOCAL_DOMAIN);
            return this;
        }

        /**
         * The bind IP address which is used
         * 
         * @param inetAddress the inetAddress to set
         * @return this
         */
        public Builder setInetAddress(InetAddress inetAddress) {
            this.inetAddress = inetAddress;
            return this;
        }
        
        /**
         * @param bindName the bindName to set
         * @return this
         */
        public Builder setBindName(String bindName) {
            this.bindName = bindName;
            return this;
        }
    }

}
