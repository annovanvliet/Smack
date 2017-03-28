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


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Date;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.jid.parts.Resourcepart;
import org.xmlpull.v1.XmlPullParser;


/**
 * Link-local XMPP connection according to XEP-0174 connection. Automatically
 * created by LLService and closed by inactivity.
 *
 */
public class OldXMPPLLConnection extends AbstractXMPPConnection {

    private OldLLService service;
    private LLPresence localPresence, remotePresence;
    private boolean initiator;
    private long lastActivity = 0;
    // FIXME this should become a boolean, it's purpose is to detect "inactive" connections
    protected OldXMPPLLConnection connection;
    private Thread timeoutThread;
    private Socket socket;

    /**
     * Protected access level because of unit test purposes
     */
    protected LLStanzaWriter stanzaWriter;

    /**
     * Protected access level because of unit test purposes
     */
    protected LLStanzaReader stanzaReader;

    /**
     * Instantiate a new link-local connection. Use the config parameter to
     * specify if the connection is acting as server or client.
     *
     * @param config specification about how the new connection is to be set up.
     */
    OldXMPPLLConnection(OldLLService service, LLConnectionConfiguration config) {
        super(config);
        // Always append the "from" attribute
        setFromMode(FromMode.USER);
        connection = this;
        this.service = service;
        updateLastActivity();

        // A timeout thread's purpose is to close down inactive connections
        // after a certain amount of seconds (defaults to 15).
        timeoutThread = new Thread() {
            public void run() {
                try {
                    while (connection != null) {
                        //synchronized (connection) {
                            Thread.sleep(14000);
                            long currentTime = new Date().getTime();
                            if (currentTime - lastActivity > 15000) {
                                shutdown();
                                break;
                            }
                        //}
                    }
                } catch (InterruptedException ie) {
                    shutdown();
                }
            }
        };

        timeoutThread.setName("Smack Link-local Connection Timeout (" + connection.connectionCounterValue + ")");
        timeoutThread.setDaemon(true);

        // Move to LLConnectionConfiguration#init
//        if (config.isInitiator()) {
//            // we are connecting to remote host
//            localPresence = config.getLocalPresence();
//            //remotePresence = config.getRemotePresence();
//            initiator = true;
//        } else {
            // a remote host connected to us
            localPresence = config.getLocalPresence();
            remotePresence = null;
            initiator = false;
            //socket = config.getSocket();
//        }
    }

    /**
     * Return this connection's LLService
     * @return
     */
    public OldLLService getService() {
        return service;
    }

    /**
     * Tells if this connection instance is the initiator.
     *
     * @return true if this instance is the one connecting to a remote peer.
     */
    public boolean isInitiator() {
        return initiator;
    }

//    /**
//     * Return the user name of the remote peer (service name).
//     *
//     * @return the remote hosts service name / username
//     */
//    public String getUser() {
//        // username is the service name of the local presence
//        return localPresence.getServiceName();
//    }

    /**
     * Sets the name of the service provided in the <stream:stream ...> from the remote peer.
     *
     * @param serviceName the name of the service
     */
    public void setServiceName(String serviceName) {
//        ((LLConnectionConfiguration)config).setServiceName(serviceName);
        //((LLConnectionConfiguration)config).setServiceName(remotePresence.getServiceName());
        //LLConnectionConfiguration llconfig = new LLConnectionConfiguration(localPresence, remotePresence);
        //llconfig.setServiceName("Test");

    }


    /**
     * Set the remote presence. Used when being connected,
     * will not know the remote service name until stream is initiated.
     *
     * @param remotePresence presence information about the connecting client.
     */
    void setRemotePresence(LLPresence remotePresence) {
        this.remotePresence = remotePresence;
    }

    /**
     * Start listen for data and a stream tag.
     * @throws SmackException 
     * @throws IOException 
     * @throws XMPPErrorException 
     */
    void initListen() throws XMPPErrorException, IOException, SmackException {
        initConnection();
    }

    /**
     * Create a socket, connect to the remote peer and initiate a XMPP stream session.
     * @throws SmackException 
     * @throws IOException 
     * @throws XMPPErrorException 
     */
    public void connectInternal() throws SmackException, XMPPErrorException, IOException {
        String[] host = remotePresence.getHost();
        int port = remotePresence.getPort();

        try {
            socket = new Socket(host[0], port);
        } catch (Exception e) {
            // TODO
            throw new SmackException(e);
        }
//        catch (UnknownHostException uhe) {
//            String errorMessage = "Could not connect to " + host + ":" + port + ".";
//            throw new XMPPException.XMPPErrorException(errorMessage, new XMPPError(
//                    XMPPError.Condition.remote_server_timeout, errorMessage),
//                    uhe);
//        }
//        catch (IOException ioe) {
//            String errorMessage = "Error connecting to " + host + ":"
//                    + port + ".";
//            throw new XMPPException.XMPPErrorException(errorMessage, new XMPPError(
//                    XMPPError.Condition.remote_server_error, errorMessage), ioe);
//        }
        initConnection();

    }


    /**
     * Handles the opening of a stream after a remote client has connected and opened a stream.
     * @throws XMPPException if service name is missing or service is unknown to the mDNS daemon.
     */
    public void streamInitiatingReceived() throws XMPPException {
        if (config.getXMPPServiceDomain() == null) {
            shutdown();
        } else {
            stanzaWriter = new LLStanzaWriter();
            if (debugger != null) {
                if (debugger.getWriterListener() != null) {
                    addAsyncStanzaListener(debugger.getWriterListener(), null);
                }
            }
            // TODO
            stanzaWriter.startup();
            
            callConnectionConnectedListener();
        }
    }

    /**
     * Update the timer telling when the last activity happend. Used by timeout
     * thread to tell how long the connection has been inactive.
     */
    void updateLastActivity() {
        lastActivity = new Date().getTime();
    }

    /**
     * Sends the specified stanza to the remote peer.
     *
     * @param stanza the stanza to send
     * @throws InterruptedException 
     * @throws NotConnectedException 
     */
    @Override
    public void sendStanza(Stanza stanza) throws InterruptedException, NotConnectedException {
        updateLastActivity();
        super.sendStanza(stanza);
    }

    /**
     * Initializes the connection by creating a stanza reader and writer and opening a
     * XMPP stream to the server.
     * @throws SmackException 
     * @throws IOException 
     * @throws XMPPErrorException 
     */
    private void initConnection() throws IOException, SmackException, XMPPErrorException {
        try {
            // Set the reader and writer instance variables
            initReaderAndWriter();
            timeoutThread.start();
            // Don't initialize stanza writer until we know it's a valid connection
            // unless we are the initiator. If we are NOT the initializer, we instead
            // wait for a stream initiation before doing anything.
            if (isInitiator())
                stanzaWriter = new LLStanzaWriter();

            // Initialize stanza reader
            stanzaReader = new LLStanzaReader();

            // If debugging is enabled, we should start the thread that will listen for
            // all stanzas and then log them.
            // XXX FIXME debugging enabled not working
            if (false) {//configuration.isDebuggerEnabled()) {
                addAsyncStanzaListener(debugger.getReaderListener(), null);
            }

            // Make note of the fact that we're now connected.
            connected = true;

            // If we are the initiator start the stanza writer. This will open a XMPP
            // stream to the server. If not, a stanza writer will be started after
            // receiving an initial stream start tag.
            // TODO
            if (isInitiator())
                stanzaWriter.init();
            // Start the stanza reader. The startup() method will block until we
            // get an opening stream stanza back from server.
            stanzaReader.startup();
        }
        catch (XMPPException.XMPPErrorException ex) {
            // An exception occurred in setting up the connection. Make sure we shut down the
            // readers and writers and close the socket.

            shutdownStanzaReadersAndWritersAndCloseSocket();

            throw ex;        // Everything stopped. Now throw the exception.
        }
    }

    private void shutdownStanzaReadersAndWritersAndCloseSocket() {
        if (stanzaWriter != null) {
            try {
                stanzaWriter.shutdown();
            }
            catch (Throwable ignore) { /* ignore */ }
            stanzaWriter = null;
        }
        if (stanzaReader != null) {
            try {
                stanzaReader.shutdown();
            }
            catch (Throwable ignore) { /* ignore */ }
            stanzaReader = null;
        }
        if (socket != null) {
            try {
                socket.close();
            }
            catch (Exception e) { /* ignore */ }
            socket = null;
        }
        // closing reader after socket since reader.close() blocks otherwise
        if (reader != null) {
            try {
                reader.close();
            }
            catch (Throwable ignore) { /* ignore */ }
            reader = null;
        }
        if (writer != null) {
            try {
                writer.close();
            }
            catch (Throwable ignore) {  /* ignore */ }
            writer = null;
        }
        connected = false;
    }

    private void initReaderAndWriter() throws XMPPException.XMPPErrorException {
        try {
            reader =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        }
        catch (IOException ioe) {
            // TODO
            throw new RuntimeException(ioe);
//            throw new XMPPException.XMPPErrorException(
//                    "XMPPError establishing connection with server.",
//                    new XMPPError(XMPPError.Condition.remote_server_error,
//                            "XMPPError establishing connection with server."),
//                    ioe);
        }

        // If debugging is enabled, we open a window and write out all network traffic.
        initDebugger();
    }

    protected void shutdown() {
        connection = null;

        if (stanzaReader != null)
            stanzaReader.shutdown();
        if (stanzaWriter != null)
            stanzaWriter.shutdown();

        // Wait 150 ms for processes to clean-up, then shutdown.
        try {
            Thread.sleep(150);
        }
        catch (Exception e) {
            // Ignore.
        }

        // Close down the readers and writers.
        if (reader != null) {
            try {
                reader.close();
            }
            catch (Throwable ignore) { /* ignore */ }
            reader = null;
        }
        if (writer != null) {
            try {
                writer.close();
            }
            catch (Throwable ignore) { /* ignore */ }
            writer = null;
        }

        try {
            socket.close();
        }
        catch (Exception e) {
            // Ignore.
        }
    } 

    public void disconnect() {
        // If not connected, ignore this request.
        if (stanzaReader == null || stanzaWriter == null) {
            return;
        }

        shutdown();

        stanzaWriter = null;
        stanzaReader = null;
    }

    protected class LLStanzaReader {

        private boolean mGotStreamOpenedStanza = false;


        public synchronized void startup() throws IOException, SmackException {
            //readerThread.start();

//            try {
//                // Wait until either:
//                // - the remote peer's stream initialization stanza has been parsed
//                // - an exception is thrown while parsing
//                // - the timeout occurs
//                if (connection.isInitiator())
//                    wait(getReplyTimeout());
//            }
//            catch (InterruptedException ie) {
//                // Ignore.
//                ie.printStackTrace();
//            }
            if (connection.isInitiator() && !mGotStreamOpenedStanza) {
                //throwConnectionExceptionOrNoResponse();
            }
        }

        /**
         * 
         */
        public void shutdown() {
            // TODO Auto-generated method stub
            
        }

        protected void handleStreamOpened(XmlPullParser parser) throws Exception {
            //super.handleStreamOpened(parser);

            // if we are the initiator, this means stream has been initiated
            // if we aren't the initiator, this means we have to respond with
            // stream initiator.
            if (connection.isInitiator()) {
                mGotStreamOpenedStanza = true;
                //connection.connectionID = connection.getServiceName();
                //releaseConnectionIDLock();
            }
            else {
                // Check if service name is a known entity
                // if it is, open the stream and keep it open
                // otherwise open and immediately close it
                if (connection.getServiceName() == null) {
                    System.err.println("No service name specified in stream initiation, canceling.");
                    shutdown();
                } else {
                    // Check if service name is known, if so
                    // we will continue the session
                    LLPresence presence = service.getPresenceByServiceName(connection.getServiceName());
                    if (presence != null) {
                        connection.setRemotePresence(presence);
                        //connectionID = connection.getServiceName();
                        connection.streamInitiatingReceived();
                        //releaseConnectionIDLock();
                    } else {
                        System.err.println("Unknown service name '" +
                                connection.getServiceName() +
                                "' specified in stream initation, canceling.");
                        shutdown();
                    }
                }
            }
        }
    }

    protected class LLStanzaWriter {


        protected void openStream() throws IOException {
            // Unlike traditional XMPP Stream initiation,
            // we must provide our XEP-0174 Service Name
            // in a "from" attribute
            StringBuilder stream = new StringBuilder();
            stream.append("<stream:stream");
            stream.append(" to=\"").append(getServiceName()).append("\"");
            if (initiator)
                stream.append(" from=\"").append(((LLConnectionConfiguration) config).getLocalPresence().getServiceName()).append("\"");
            else {
                // TODO: We should be able to access the service name from the
                // stream opening stanza that this is a response to.
                String localServiceName = ((LLConnectionConfiguration) config).getLocalPresence().getJID();
                localServiceName = localServiceName.substring(0, localServiceName.lastIndexOf("."));
                stream.append(" from=\"").append(localServiceName).append("\"");
            }
            stream.append(" xmlns=\"jabber:client\"");
            stream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            stream.append(" version=\"1.0\">");
            writer.write(stream.toString());
            writer.flush();
        }

        /**
         * 
         */
        public void shutdown() {
            // TODO Auto-generated method stub
            
        }

        /**
         * 
         */
        public void init() {
            // TODO Auto-generated method stub
            
        }

        /**
         * 
         */
        public void startup() {
            // TODO Auto-generated method stub
            
        }

        /**
         * @param element
         */
        public void sendStreamElement(Nonza element) {
            // TODO Auto-generated method stub
            
        }

        /**
         * @param stanza
         */
        public void sendStreamElement(Stanza stanza) {
            // TODO Auto-generated method stub
            
        }
    }

	@Override
	public boolean isSecureConnection() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void sendStanzaInternal(Stanza stanza)
			throws NotConnectedException {
	    stanzaWriter.sendStreamElement(stanza);
		
	}

	@Override
	public boolean isUsingCompression() {
		// TODO Auto-generated method stub
		return false;
	}

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendNonza(org.jivesoftware.smack.packet.Nonza)
     */
    @Override
    public void sendNonza(Nonza element) throws NotConnectedException, InterruptedException {
        stanzaWriter.sendStreamElement(element);
        
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#loginInternal(java.lang.String, java.lang.String, org.jxmpp.jid.parts.Resourcepart)
     */
    @Override
    protected void loginInternal(String username, String password, Resourcepart resource)
                    throws XMPPException, SmackException, IOException, InterruptedException {
        // TODO Auto-generated method stub
        
    }
}
