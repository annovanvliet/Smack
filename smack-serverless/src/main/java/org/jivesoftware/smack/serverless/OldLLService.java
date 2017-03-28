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


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.serverless.LLConnectionConfiguration.Builder;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jivesoftware.smack.serverless.service.LLPresenceListener;
import org.jivesoftware.smack.serverless.service.LLServiceConnectionListener;
import org.jivesoftware.smack.serverless.service.LLServiceListener;
import org.jivesoftware.smack.serverless.service.LLServiceStateListener;
import org.jivesoftware.smack.serverless.service.jmdns.DNSException;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;

/**
 * LLService acts as an abstract interface to a Link-local XMPP service
 * according to XEP-0174. XEP-0174 describes how this is achieved using
 * mDNS/DNS-SD, and this class creates an implementation unspecific 
 * interface for doing this.
 *
 * The mDNS/DNS-SD is for example implemented by JmDNSService (using JmDNS).
 *
 * There is only one instance of LLService possible at one time.
 *
 * Tasks taken care of here are:
 * <ul>
 *   <li>Connection Management
 *     <ul>
 *       <li>Keep track of active connections from and to the local client</li>
 *       <li>Listen for connections on a semi randomized port announced by the
 *           mDNS/DNS-SD daemon</li>
 *       <li>Establish new connections when there is none to use and stanzas are
 *           to be sent</li>
 *     <ul>
 *   <li>Chat Management - Keep track of messaging sessions between users</li>
 * </ul>
 *
 * @author Jonas Ådahl
 */
public abstract class OldLLService {
    private static OldLLService service = null;

    // Listeners for new services
    private static Set<LLServiceListener> serviceCreatedListeners =
        new CopyOnWriteArraySet<LLServiceListener>();

    static final int DEFAULT_MIN_PORT = 2300;
    static final int DEFAULT_MAX_PORT = 2400;
    protected LLPresence presence;
    private boolean done = false;
    private Thread listenerThread;

    private boolean initiated = false;

    private Map<EntityBareJid,Chat> chats = new ConcurrentHashMap<>();

    private final Map<DomainBareJid,OldXMPPLLConnection> ingoing =
        new ConcurrentHashMap<DomainBareJid,OldXMPPLLConnection>();
    private final Map<DomainBareJid,OldXMPPLLConnection> outgoing =
        new ConcurrentHashMap<DomainBareJid,OldXMPPLLConnection>();

    // Listeners for state updates, such as LLService closed down
    private Set<LLServiceStateListener> stateListeners =
        new CopyOnWriteArraySet<LLServiceStateListener>();

    // Listeners for XMPPLLConnections associated with this service
    private Set<LLServiceConnectionListener> llServiceConnectionListeners =
        new CopyOnWriteArraySet<LLServiceConnectionListener>();

    // Listeners for stanzas coming from this Link-local service
    private final Map<StanzaListener, ListenerWrapper> listeners =
            new ConcurrentHashMap<StanzaListener, ListenerWrapper>();

    // Presence discoverer, notifies of changes in presences on the network.
    private LLPresenceDiscoverer presenceDiscoverer;

    // chat listeners gets notified when new chats are created
    private Set<IncomingChatMessageListener> chatListeners = new CopyOnWriteArraySet<>();
    
    // Set of Stanza collector wrappers
    private Set<CollectorWrapper> collectorWrappers =
        new CopyOnWriteArraySet<CollectorWrapper>();

    // Set of associated connections.
    private final Set<OldXMPPLLConnection> associatedConnections =
        new HashSet<OldXMPPLLConnection>();

    private ServerSocket socket;

    static {
        SmackConfiguration.getVersion();
    }

    /**
     * Spam stdout with some debug information.
     */
    public void spam() {
        System.out.println("Number of ingoing connection in map: " + ingoing.size());
        System.out.println("Number of outgoing connection in map: " + outgoing.size());

        System.out.println("Active chats:");
//        for (LLChat chat : chats.values()) {
//            System.out.println(" * " + chat.getServiceName());
//        }

        System.out.println("Known presences:");
        for (LLPresence presence : presenceDiscoverer.getPresences()) {
            System.out.println(" * " + presence.getServiceName() + "(" + presence.getStatus() + ", " + Arrays.toString(presence.getHost()) + ":" + presence.getPort() + ")");
        }
        Thread.currentThread().getThreadGroup().list();
    }

    protected OldLLService(LLPresence presence, LLPresenceDiscoverer discoverer) {
        this.presence = presence;
        presenceDiscoverer = discoverer;
        service = this;

//        XMPPLLConnection.addLLConnectionListener(new AbstractConnectionListener() {
//
//            @Override
//            public void connected(XMPPConnection xmppConnection) {
//                if (! (xmppConnection instanceof XMPPLLConnection)) {
//                    return;
//                }
//                XMPPLLConnection connection = (XMPPLLConnection) xmppConnection;
//                // We only care about this connection if we were the one
//                // creating it
//                if (isAssociatedConnection(connection)) {
//                    if (connection.isInitiator()) {
//                        addOutgoingConnection(connection);
//                    }
//                    else {
//                        addIngoingConnection(connection);
//                    }
//
//                    connection.addConnectionListener(new ConnectionActivityListener(connection));
//
//                    // Notify listeners that a new connection associated with this
//                    // service has been created.
//                    notifyNewServiceConnection(connection);
//
//
//                    // add other existing stanza filters associated with this service
//                    for (ListenerWrapper wrapper : listeners.values()) {
//                        connection.addStanzaListener(wrapper.getStanzaListener(),
//                                wrapper.getStanzaFilter());
//                    }
//
//                    // add stanza collectors
//                    for (CollectorWrapper cw : collectorWrappers) {
//                        cw.createStanzaCollector(connection);
//                    }
//                }
//            }
//        });

        notifyServiceListeners(this);
    }

    /**
     * Add a LLServiceListener. The LLServiceListener is notified when a new
     * Link-local service is created.
     *
     * @param listener the LLServiceListener
     */
    public static void addLLServiceListener(LLServiceListener listener) {
        serviceCreatedListeners.add(listener);
    }

    /**
     * Remove a LLServiceListener.
     * @param listener
     */
    public static void removeLLServiceListener(LLServiceListener listener) {
        serviceCreatedListeners.remove(listener);
    }

    /**
     * Notify LLServiceListeners about a new Link-local service.
     *
     * @param service the new service.
     */
    public static void notifyServiceListeners(OldLLService service) {
        for (LLServiceListener listener : serviceCreatedListeners) {
            listener.serviceCreated(service);
        }
    }

    /**
     * Returns the running mDNS/DNS-SD XMPP instance. There can only be one
     * instance at a time.
     *
     * @return the active LLService instance.
     * @throws XMPPException if the LLService hasn't been instantiated.
     */
    public synchronized static OldLLService getServiceInstance() throws XMPPException {
        if (service == null)
            throw new DNSException("Link-local service not initiated.");
        return service;
    }

    /**
     * Registers the service to the mDNS/DNS-SD daemon.
     * Should be implemented by the class extending this, for mDNS/DNS-SD library specific calls.
     * @throws XMPPException
     */
    protected abstract void registerService() throws XMPPException;

    /**
     * Re-announce the presence information by using the mDNS/DNS-SD daemon.
     * @throws XMPPException
     */
    protected abstract void reannounceService() throws XMPPException;

    /**
     * Make the client unavailabe. Equivalent to sending unavailable-presence.
     */
    public abstract void makeUnavailable();

    /**
     * Update the text field information. Used for setting new presence information.
     */
    protected abstract void updateText();

    public void init() throws XMPPException {
        // allocate a new port for remote clients to connect to
        socket = bindRange(DEFAULT_MIN_PORT, DEFAULT_MAX_PORT);
        presence.setPort(socket.getLocalPort());

        // register service on the allocated port
        registerService();

        // start to listen for new connections
        listenerThread = new Thread() {
            public void run() {
                try {
                    // Listen for connections
                    listenForConnections();

                    // If listen for connections returns with no exception,
                    // the service has closed down
                    for (LLServiceStateListener listener : stateListeners)
                        listener.serviceClosed();
                } catch (XMPPException e) {
                    for (LLServiceStateListener listener : stateListeners)
                        listener.serviceClosedOnError(e);
                }
            }
        };
        listenerThread.setName("Smack Link-local Service Listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        initiated = true;
    }

    public void close() throws IOException {
        done = true;

//        // close incoming connections
//        for (XMPPLLConnection connection : ingoing.values()) {
//            try {
//                connection.shutdown();
//            } catch (Exception e) {
//                // ignore
//            }
//        }

//        // close outgoing connections
//        for (XMPPLLConnection connection : outgoing.values()) {
//            try {
//                connection.shutdown();
//            } catch (Exception e) {
//                // ignore
//            }
//        }
        try {
            socket.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    /**
     * Listen for new connections on socket, and spawn XMPPLLConnections
     * when new connections are established.
     *
     * @throws XMPPException whenever an exception occurs
     */
    private void listenForConnections() throws XMPPException {
        while (!done) {
            try {
                // wait for new connection
                Socket s = socket.accept();

                Builder config = LLConnectionConfiguration.builder().setLocalPresence(presence); //.setSocket(s);
                OldXMPPLLConnection connection = new OldXMPPLLConnection(this, config.build());

                // Associate the new connection with this service
                addAssociatedConnection(connection);

                // Spawn new thread to handle the connecting.
                // The reason for spawning a new thread is to let two remote clients
                // be able to connect at the same time.
                Thread connectionInitiatorThread =
                    new ConnectionInitiatorThread(connection);
                connectionInitiatorThread.setName("Smack Link-local Connection Initiator");
                connectionInitiatorThread.setDaemon(true);
                connectionInitiatorThread.start();
            }
            catch (SocketException se) {
                // If we are closing down, it's probably closed socket exception.
                if (!done) {
                    throw new DNSException("Link-local service unexpectedly closed down.", se);
                }
            }
            catch (IOException ioe) {
                throw new DNSException("Link-local service unexpectedly closed down.", ioe);
            }
        }
    }

    /**
     * Bind one socket to any port within a given range.
     *
     * @param min the minimum port number allowed
     * @param max hte maximum port number allowed
     * @throws XMPPException if binding failed on all allowed ports.
     */
    private static ServerSocket bindRange(int min, int max) throws XMPPException {
        // TODO this method exists also for the local socks5 proxy code and should be factored out into a util
        int port = 0;
        for (int try_port = min; try_port <= max; try_port++) {
            try {
                ServerSocket socket = new ServerSocket(try_port);
                return socket;
            }
            catch (IOException e) {
                // failed to bind, try next
            }
        }
        throw new DNSException("Unable to bind port, no ports available.");
    }

    protected void unknownOriginMessage(Message message) {
        for (LLServiceStateListener listener : stateListeners) {
            listener.unknownOriginMessage(message);
        }
    }

    protected void serviceNameChanged(EntityBareJid newName, EntityBareJid oldName) {
        // update our own presence with the new name, for future connections
        presence.setServiceName(newName);

        // clean up connections
        OldXMPPLLConnection c;
        c = getConnectionTo(oldName);
        if (c != null)
            c.disconnect();
        c = getConnectionTo(newName);
        if (c != null)
            c.disconnect();

        // notify listeners
        for (LLServiceStateListener listener : stateListeners) {
            listener.serviceNameChanged(newName, oldName);
        }
    }

    /**
     * Adds a listener that are notified when a new link-local connection
     * has been established.
     *
     * @param listener A class implementing the LLConnectionListener interface.
     */
    public void addLLServiceConnectionListener(LLServiceConnectionListener listener) {
        llServiceConnectionListeners.add(listener);
    }

    /**
     * Removes a listener from the new connection listener list.
     *
     * @param listener The class implementing the LLConnectionListener interface that
     * is to be removed.
     */
    public void removeLLServiceConnectionListener(LLServiceConnectionListener listener) {
        llServiceConnectionListeners.remove(listener);
    }

    private void notifyNewServiceConnection(OldXMPPLLConnection connection) {
        for (LLServiceConnectionListener listener : llServiceConnectionListeners) {
            listener.connectionCreated(connection);
        }
    }

    /**
     * Add the given connection to the list of associated connections.
     * An associated connection means it's a Link-Local connection managed
     * by this service.
     *
     * @param connection the connection to be associated
     */
    private void addAssociatedConnection(OldXMPPLLConnection connection) {
        synchronized (associatedConnections) {
            associatedConnections.add(connection);
        }
    }

    /**
     * Remove the given connection from the list of associated connections.
     *
     * @param connection the connection to be removed.
     */
    private void removeAssociatedConnection(OldXMPPLLConnection connection) {
        synchronized (associatedConnections) {
            associatedConnections.remove(connection);
        }
    }

    /**
     * Return true if the given connection is associated / managed by this
     * service.
     *
     * @param connection the connection to be checked
     * @return true if the connection is associated with this service or false
     * if it is not associated with this service.
     */
    private boolean isAssociatedConnection(OldXMPPLLConnection connection) {
        synchronized (associatedConnections) {
            return associatedConnections.contains(connection);
        }
    } 

    /**
     * Add a stanza listener.
     *
     * @param listener the StanzaListener
     * @param filter the Filter
     */
    public void addStanzaListener(StanzaListener listener, StanzaFilter filter) {
        ListenerWrapper wrapper = new ListenerWrapper(listener, filter);
        listeners.put(listener, wrapper);

        // Also add to existing connections
        synchronized (ingoing) {
            synchronized (outgoing) {
                for (OldXMPPLLConnection c : getConnections()) {
                    c.addAsyncStanzaListener(listener, filter);
                }
            }
        }
    }

    /** 
     * Remove a stanza listener.
     * @param listener
     */
    public void removeStanzaListener(StanzaListener listener) {
        listeners.remove(listener);

        // Also add to existing connections
        synchronized (ingoing) {
            synchronized (outgoing) {
                for (OldXMPPLLConnection c : getConnections()) {
                    c.removeAsyncStanzaListener(listener);
                }
            }
        }
    }

    /**
     * Add service state listener.
     *
     * @param listener the service state listener to be added.
     */
    public void addServiceStateListener(LLServiceStateListener listener) {
        stateListeners.add(listener);
    }

    /**
     * Remove service state listener.
     *
     * @param listener the service state listener to be removed.
     */
    public void removeServiceStateListener(LLServiceStateListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Add Link-local chat session listener. The chat session listener will
     * be notified when new link-local chat sessions are created.
     *
     * @param listener the listener to be added.
     */
    public void addLLChatListener(IncomingChatMessageListener listener) {
        chatListeners.add(listener);
    }

    /**
     * Remove Link-local chat session listener. 
     *
     * @param listener the listener to be removed.
     */
    public void removeLLChatListener(IncomingChatMessageListener listener) {
        chatListeners.remove(listener);
    }

    /**
     * Add presence listener. A presence listener will be notified of new
     * presences, presences going offline, and changes in presences.
     *
     * @param listener the listener to be added.
     */
    public void addPresenceListener(LLPresenceListener listener) {
        presenceDiscoverer.addPresenceListener(listener);
    }

    /**
     * Remove presence listener.
     *
     * @param listener presence listener to be removed.
     */
    public void removePresenceListener(LLPresenceListener listener) {
        presenceDiscoverer.removePresenceListener(listener);
    }

    /**
     * Get the presence information associated with the given service name.
     *
     * @param serviceName the service name which information should be returned.
     * @return the service information.
     */
    public LLPresence getPresenceByServiceName(Jid serviceName) {
        return presenceDiscoverer.getPresence(serviceName);
    }

    public CollectorWrapper createStanzaCollector(StanzaFilter filter) {
        CollectorWrapper wrapper = new CollectorWrapper(filter);
        collectorWrappers.add(wrapper);
        return wrapper;
    }

    /**
     * Return a collection of all active connections. This may be used if the
     * user wants to change a property on all connections, such as add a service
     * discovery feature or other.
     *
     * @return a collection of all active connections.
     */
    public Collection<OldXMPPLLConnection> getConnections() {
        Collection<OldXMPPLLConnection> connections =
            new ArrayList<OldXMPPLLConnection>(outgoing.values());
        connections.addAll(ingoing.values());
        return connections;
    }

    /**
     * Returns a connection to a given service name.
     * First checks for an outgoing connection, if noone exists,
     * try ingoing.
     *
     * @param serviceName the service name
     * @return a connection associated with the service name or null if no
     * connection is available.
     */
    OldXMPPLLConnection getConnectionTo(Jid serviceName) {
        OldXMPPLLConnection connection = outgoing.get(serviceName);
        if (connection != null)
            return connection;
        return ingoing.get(serviceName);
    }

    void addIngoingConnection(OldXMPPLLConnection connection) {
        ingoing.put(connection.getServiceName(), connection);
    }

    void removeIngoingConnection(OldXMPPLLConnection connection) {
        ingoing.remove(connection.getServiceName());
    }

    void addOutgoingConnection(OldXMPPLLConnection connection) {
        outgoing.put(connection.getServiceName(), connection);
    }

    void removeOutgoingConnection(OldXMPPLLConnection connection) {
        outgoing.remove(connection.getServiceName());
    }

//    Chat removeLLChat(String serviceName) {
//        return chats.remove(serviceName);
//    }
//
//    /**
//     * Returns a new {@link org.jivesoftware.smack.serverless.LLChat}
//     * at the request of the local client.
//     * This method should not be used to create Chat sessions
//     * in response to messages received from remote peers.
//     */
//    void newLLChat(Chat chat) {
//        chats.put(chat.getXmppAddressOfChatPartner(), chat);
//        for (IncomingChatManagerListener listener : chatListeners) {
//            listener.chatCreated(chat, true);
//        }
//    }

    /**
     * Get a LLChat associated with a given service name.
     * If no LLChat session is available, a new one is created.
     *
     * This method should not be used to create Chat sessions
     * in response to messages received from remote peers.
     *
     * @param serviceName the service name
     * @return a chat session instance associated with the given service name.
     * @throws InterruptedException 
     * @throws XMPPException 
     * @throws SmackException 
     * @throws IOException 
     */
    public Chat getChat(Jid serviceName) throws  InterruptedException, IOException, SmackException, XMPPException {
        Chat chat = chats.get(serviceName);
        if (chat == null) {
            LLPresence presence = getPresenceByServiceName(serviceName);
            if (presence == null)
                throw new DNSException("Can't initiate new chat to '" +
                        serviceName + "': mDNS presence unknown.");
            
            OldXMPPLLConnection connection = getConnection(presence.getServiceName());
            
            chat = ChatManager.getInstanceFor(connection).chatWith(
                    presence.getServiceName().asEntityBareJidOrThrow());
            //newLLChat(chat);
        }
        return chat;
    }

    /**
     * Returns a XMPPLLConnection to the serviceName.
     * If no established connection exists, a new connection is created.
     * 
     * @param serviceName Service name of the remote client.
     * @return A connection to the given service name.
     * @throws InterruptedException 
     * @throws XMPPException 
     * @throws IOException 
     * @throws SmackException 
     */
    public OldXMPPLLConnection getConnection(Jid serviceName) throws XMPPException, InterruptedException, SmackException, IOException {
        // If a connection exists, return it.
        OldXMPPLLConnection connection = getConnectionTo(serviceName);
        if (connection != null)
            return connection;

        // If no connection exists, look up the presence and connect according to.
        LLPresence remotePresence = getPresenceByServiceName(serviceName);

        if (remotePresence == null) {
            throw new DNSException("Can't initiate connection, remote peer is not available.");
        }

        Builder config =
            LLConnectionConfiguration.builder().setLocalPresence(remotePresence); //.setRemotePresence(remotePresence);
        
        connection = new OldXMPPLLConnection(this, config.build());
        // Associate the new connection with this service
        addAssociatedConnection(connection);
        connection.connect();
        addOutgoingConnection(connection);

        return connection;
    }

    /**
     * Send a message to the remote peer.
     *
     * @param message the message to be sent.
     * @throws XMPPException if the message cannot be sent.
     */
    void sendMessage(Message message) throws XMPPException {
        sendMessage(message);
    }


    /**
     * Send a stanza to the remote peer.
     *
     * @param stanza the stanza to be sent.
     * @throws XMPPException if the stanza cannot be sent.
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SmackException 
     * @throws NotConnectedException 
     */
    public void sendStanza(Stanza stanza) throws InterruptedException, NotConnectedException, XMPPException, SmackException, IOException {
        getConnection(stanza.getTo()).sendStanza(stanza);
    }

    /**
     * Send an IQ set or get and wait for the response. This function works
     * different from a normal one-connection IQ request where a stanza
     * collector is created and added to the connection. This function
     * takes care of (at least) two cases when this doesn't work:
     * <ul>
     *  <li>Consider client A requests something from B. This is done by
     *      A connecting to B (no existing connection is available), then
     *      sending an IQ request to B using the new connection and starts
     *      waiting for a reply. However the connection between them may be
     *      terminated due to inactivity, and for B to reply, it have to 
     *      establish a new connection. This function takes care of this
     *      by listening for the stanzas on all new connections.</li>
     *  <li>Consider client A and client B concurrently establishes
     *      connections between them. This will result in two parallell
     *      connections between the two entities and the two clients may
     *      choose whatever connection to use when communicating. This
     *      function takes care of the possibility that if A requests
     *      something from B using connection #1 and B replies using
     *      connection #2, the stanza will still be collected.</li>
     * </ul>
     * 
     * @param request
     * @return
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SmackException 
     * @throws XMPPException 
     */
    public IQ getIQResponse(IQ request) throws InterruptedException, XMPPException, SmackException, IOException {
        OldXMPPLLConnection connection = getConnection(request.getTo());

        // Create a stanza collector to listen for a response.
        // Filter: req.id == rpl.id ^ (rp.iqtype in (result, error))
        CollectorWrapper collector = createStanzaCollector(
                new AndFilter(
                    new StanzaIdFilter(request.getStanzaId()),
                    new OrFilter(
                        IQTypeFilter.RESULT,
                        IQTypeFilter.ERROR)));

        connection.sendStanza(request);

        // Wait up to 5 seconds for a result.
        IQ result = (IQ) collector.nextResult(
                SmackConfiguration.getDefaultReplyTimeout());

        // Stop queuing results
        collector.cancel();
        if (result == null) {
            throw new DNSException("No response from the remote host.");
        }

        return result;
    }

    /**
     * Update the presence information announced by the mDNS/DNS-SD daemon.
     * The presence object stored in the LLService class will be updated
     * with the new information and the daemon will reannounce the changes.
     *
     * @param presence the new presence information
     * @throws XMPPException if an error occurs
     */
    public void updateLocalPresence(LLPresence presence) throws XMPPException {
//        this.presence.update(presence);

        if (initiated) {
            updateText();
            reannounceService();
        }
    }

    /**
     * Get current Link-local presence.
     * @return
     */
    public LLPresence getLocalPresence() {
        return presence;
    }

    /**
     * ConnectionActivityListener listens for link-local connection activity
     * such as closed connection and broken connection, and keeps record of
     * what active connections exist up to date.
     */
    private class ConnectionActivityListener implements ConnectionListener {
        private OldXMPPLLConnection connection;

        ConnectionActivityListener(OldXMPPLLConnection connection) {
            this.connection = connection;
        }

        @Override
        public void connected(XMPPConnection connection) {

        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed ) {

        }

        public void connectionClosed() {
            removeConnectionRecord();
        }

        public void connectionClosedOnError(Exception e) {
            removeConnectionRecord();
        }

        public void reconnectingIn(int seconds) {
        }

        public void reconnectionSuccessful() {
        }

        public void reconnectionFailed(Exception e) {
        }

        private void removeConnectionRecord() {
            if (connection.isInitiator())
                removeOutgoingConnection(connection);
            else
                removeIngoingConnection(connection);

            removeAssociatedConnection(connection);
        }
    }

    /**
     * Initiates a connection in a seperate thread, controlling
     * it was established correctly and stream was initiated.
     */
    private class ConnectionInitiatorThread extends Thread {
        OldXMPPLLConnection connection;

        ConnectionInitiatorThread(OldXMPPLLConnection connection) {
            this.connection = connection;
        }

        public void run() {
//            try {
//                connection.initListen();
//            }
//            catch (XMPPException | SmackException | IOException e) {
//                // ignore, since its an incoming connection
//                // there is nothing to save
//            }
        }
    }

    /**
     * A wrapper class to associate a stanza filter with a listener.
     */
    private static class ListenerWrapper {

        private StanzaListener stanzaListener;
        private StanzaFilter stanzaFilter;

        public ListenerWrapper(StanzaListener stanzaListener, StanzaFilter stanzaFilter) {
            this.stanzaListener = stanzaListener;
            this.stanzaFilter = stanzaFilter;
        }
       
        public void notifyListener(Stanza stanza) throws SmackException.NotConnectedException, InterruptedException {
            if (stanzaFilter == null || stanzaFilter.accept(stanza)) {
                stanzaListener.processStanza(stanza);
            }
        }

        public StanzaListener getStanzaListener() {
            return stanzaListener;
        }

        public StanzaFilter getStanzaFilter() {
            return stanzaFilter;
        }
    }

    /**
     * Stanza Collector Wrapper which is used for collecting packages
     * from multiple connections as well as newly established connections (works
     * together with LLService constructor.
     */
    public class CollectorWrapper {
        // Existing collectors.
        private Set<StanzaCollector> collectors =
            new CopyOnWriteArraySet<StanzaCollector>();

        // Stanza filter for all the collectors.
        private StanzaFilter stanzaFilter;

        // A common object used for shared locking between
        // the collectors.
        private final Object lock = new Object();

        private CollectorWrapper(StanzaFilter stanzaFilter) {
            this.stanzaFilter = stanzaFilter;

            // Apply to all active connections
            for (OldXMPPLLConnection connection : getConnections()) {
                createStanzaCollector(connection);
            }
        }

        /**
         * Create a new per-connection stanza collector.
         *
         * @param connection the connection the collector should be added to.
         */
        private void createStanzaCollector(OldXMPPLLConnection connection) {
            synchronized (connection) {
                StanzaCollector collector =
                    connection.createStanzaCollector(stanzaFilter);
                //collector.setLock(lock);
                collectors.add(collector);
            }
        }

        /**
         * Returns the next available stanza. The method call will block (not return)
         * until a stanza is available or the <tt>timeout</tt> has elapsed. If the
         * timeout elapses without a result, <tt>null</tt> will be returned.
         *
         * @param timeout the amount of time to wait for the next stanza
         *                (in milleseconds).
         * @return the next available stanza.
         */
        public synchronized Stanza nextResult(long timeout) {
            Stanza stanza;
            long waitTime = timeout;
            long start = System.currentTimeMillis();

            try {
                while (true) {
                    for (StanzaCollector c : collectors) {
                        if (c.isCanceled())
                            collectors.remove(c);
                        else {
                            stanza = c.pollResult();
                            if (stanza != null)
                                return stanza;
                        }
                    }

                    if (waitTime <= 0) {
                        break;
                    }

                    // TODO: lock won't be notified bc it's no longer managed by StanzaCollector
                    // Perhaps we need a different mechanism here
                    // wait
                    synchronized (lock) {
                        lock.wait(waitTime);
                    }
                    long now = System.currentTimeMillis();
                    waitTime -= (now - start);
                }
            }
            catch (InterruptedException ie) {
                // ignore
            }

            return null;
        }

        public void cancel() {
            for (StanzaCollector c : collectors) {
                c.cancel();
            }
            collectorWrappers.remove(this);
        }
    }
}

