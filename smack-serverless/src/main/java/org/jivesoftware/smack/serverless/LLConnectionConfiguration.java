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
import org.xbill.DNS.Name;
import org.xbill.DNS.TextParseException;

/**
 * Link-local connection configuration settings. Two general cases exists, one where the we want to connect to a remote
 * peer, and one when o remote peer has connected to us.
 */
public class LLConnectionConfiguration extends ConnectionConfiguration implements Cloneable {
    private static final String SERVICE_NAME = "locallink";

    /**
     * The default connect timeout in milliseconds. Preinitialized with 30000 (30 seconds). If this value is changed,
     * new Builder instances will use the new value as default.
     */
    public static int DEFAULT_CONNECT_TIMEOUT = 30000;

    private final LLPresence localPresence;
    
    private final boolean compressionEnabled;

    /**
     * How long the socket will wait until a TCP connection is established (in milliseconds).
     */
    private final int connectTimeout;

    /**
     * Holds the socket factory that is used to generate the socket in the connection
     */
    private final InetAddress inetAddress;
    private final String bindName;
    
    private final Name[] domains;

    /**
     * Configuration used for connecting to remote peer.
     * 
     * @param builder
     */
    public LLConnectionConfiguration(Builder builder) {
        super(builder);
        compressionEnabled = builder.compressionEnabled;
        connectTimeout = builder.connectTimeout;

        this.localPresence = builder.localPresence;
        //this.socket = builder.socket;
        this.inetAddress = builder.inetAddress;
        this.bindName = builder.bindName;
        this.domains = builder.domains;
    }

    /**
     * Returns true if the connection is going to use stream compression. Stream compression
     * will be requested after TLS was established (if TLS was enabled) and only if the server
     * offered stream compression. With stream compression network traffic can be reduced
     * up to 90%. By default compression is disabled.
     *
     * @return true if the connection is going to use stream compression.
     */
    @Override
    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    /**
     * How long the socket will wait until a TCP connection is established (in milliseconds). Defaults to {@link #DEFAULT_CONNECT_TIMEOUT}.
     *
     * @return the timeout value in milliseconds.
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

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

    /**
     * @return
     */
    public Name[] getDomains() {
        return domains;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ConnectionConfiguration.Builder<Builder, LLConnectionConfiguration> {

        /**
         * 
         */
        public static final String LOCAL_DOMAIN_NAME = "local.";
        public Name[] domains;
        private boolean compressionEnabled = false;
        private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

        private final DomainBareJid LOCAL_DOMAIN;
        private LLPresence localPresence;
        private InetAddress inetAddress;
        private String bindName;

        private Builder() {
            try {
                LOCAL_DOMAIN = JidCreate.domainBareFrom(LOCAL_DOMAIN_NAME);
                domains = new Name[]{new Name(LOCAL_DOMAIN_NAME)};
            }
            catch (XmppStringprepException | TextParseException e) {
                throw new IllegalArgumentException(e);
            }
        }

        /**
         * Sets if the connection is going to use stream compression. Stream compression
         * will be requested after TLS was established (if TLS was enabled) and only if the server
         * offered stream compression. With stream compression network traffic can be reduced
         * up to 90%. By default compression is disabled.
         *
         * @param compressionEnabled if the connection is going to use stream compression.
         * @return a reference to this object.
         */
        public Builder setCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /**
         * Set how long the socket will wait until a TCP connection is established (in milliseconds).
         *
         * @param connectTimeout the timeout value to be used in milliseconds.
         * @return a reference to this object.
         */
        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
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
        
        /**
         * @param domains the domains to set
         * @return this
         */
        public Builder setDomains(Name[] domains) {
            this.domains = domains;
            return this;
        }
        
        @Override
        protected Builder getThis() {
            return this;
        }

        @Override
        public LLConnectionConfiguration build() {
            return new LLConnectionConfiguration(this);
        }
    }

}
