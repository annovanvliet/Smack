/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SynchronizationPoint;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException.SecurityRequiredException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.compress.packet.Compressed;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamError;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.Challenge;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.SASLFailure;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.Success;
import org.jivesoftware.smack.serverless.service.jmdns.JmDNSService2;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import io.netty.channel.Channel;

/**
 * @author anno
 *
 */
public class XMPPSLConnection extends AbstractXMPPConnection implements XMPPConnection {

    private static final Logger LOGGER = Logger.getLogger(XMPPSLConnection.class.getName());

    private SLService service;
    private final LLConnectionConfiguration configuration;

    private DomainBareJid serviceDomain;

    private Map<Jid, LLStream> streams = new TreeMap<>();

    /**
     * @param build
     */
    public XMPPSLConnection(LLConnectionConfiguration configuration) {
        super(configuration);
        
        this.configuration = configuration;
        
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
    protected void sendStanzaInternal(Stanza packet) throws InterruptedException, NotConnectedException {
        LOGGER.fine("sendStanzaInternal " + packet );
        
        if ( packet.getTo() != null ) {
            
            LLStream stream = getStreamByJid(packet.getTo());
            if ( stream != null ) {
                stream.send(packet);
            }
            
            if (packet != null) {
                firePacketSendingListeners(packet);
            }
        } else {
            LOGGER.info("no addressee for " + packet );
        }
        
        
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendNonza(org.jivesoftware.smack.packet.Nonza)
     */
    @Override
    public void sendNonza(Nonza element) throws NotConnectedException, InterruptedException {
        LOGGER.info("no addressee for " + element );

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
        LOGGER.fine("connectInternal");

        // Start Service

        // Create a basic presence (only set name, and status to available)
        service = JmDNSService2.create( configuration.getLocalPresence(), configuration.getInetAddress());
        
        service.init(this);

        tlsHandled.reportSuccess();
        saslFeatureReceived.reportSuccess();
        
        EntityBareJid jid = JidCreate.entityBareFrom(configuration.getLocalPresence().getServiceName());
        user = JidCreate.entityFullFrom(jid, Resourcepart.from("local"));
        serviceDomain = user.asDomainBareJid();

    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#getXMPPServiceDomain()
     */
    @Override
    public DomainBareJid getXMPPServiceDomain() {
        // TODO Auto-generated method stub
        return serviceDomain;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#loginInternal(java.lang.String, java.lang.String, org.jxmpp.jid.parts.Resourcepart)
     */
    @Override
    protected void loginInternal(String username, String password, Resourcepart resource)
                    throws XMPPException, SmackException, IOException, InterruptedException {


    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#afterFeaturesReceived()
     */
    @Override
    protected void afterFeaturesReceived()
                    throws SecurityRequiredException, NotConnectedException, InterruptedException {
        LOGGER.fine("afterFeaturesReceived" );
    }
    
    /* (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#shutdown()
     */
    @Override
    protected void shutdown() {
        LOGGER.fine("shutdown" );

        if ( service != null ) {
            try {
                service.close();
            }
            catch (IOException | InterruptedException e) {
                LOGGER.log(Level.FINE, "Service close not succesfull", e);
            }
        }

    }

//    private final SynchronizationPoint<Exception> initalOpenStreamSend = new SynchronizationPoint<>(
//                    this, "initial open stream element send to server");
//
//    /**
//     * 
//     */
//    private final SynchronizationPoint<XMPPException> maybeCompressFeaturesReceived = new SynchronizationPoint<XMPPException>(
//                    this, "stream compression feature");
//
//    /**
//     * 
//     */
//    private final SynchronizationPoint<SmackException> compressSyncPoint = new SynchronizationPoint<>(
//                    this, "stream compression");
//
//    /**
//     * A synchronization point which is successful if this connection has received the closing
//     * stream element from the remote end-point, i.e. the server.
//     */
//    private final SynchronizationPoint<Exception> closingStreamReceived = new SynchronizationPoint<>(
//                    this, "stream closing element received");

    private void initReaderAndWriter() throws IOException {
//        InputStream is = socket.getInputStream();
//        OutputStream os = socket.getOutputStream();
//        if (compressionHandler != null) {
//            is = compressionHandler.getInputStream(is);
//            os = compressionHandler.getOutputStream(os);
//        }
//        // OutputStreamWriter is already buffered, no need to wrap it into a BufferedWriter
//        writer = new OutputStreamWriter(os, "UTF-8");
//        reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
//
//        // If debugging is enabled, we open a window and write out all network traffic.
//        initDebugger();
    }

    /**
     * Sends out a notification that there was an error with the connection
     * and closes the connection. Also prints the stack trace of the given exception
     *
     * @param e the exception that causes the connection close event.
     */
    private synchronized void notifyConnectionError(Exception e) {
        // Listeners were already notified of the exception, return right here.
//        if ((packetReader == null || packetReader.done) &&
//                (packetWriter == null || packetWriter.done())) return;

        // Closes the connection temporary. A reconnection is possible
        // Note that a connection listener of XMPPTCPConnection will drop the SM state in
        // case the Exception is a StreamErrorException.
//        instantShutdown();

        // Notify connection listeners of the error.
        callConnectionClosedOnErrorListener(e);
    }

    
    class PacketReader implements XMPPReader {
        
        /**
         * Set to success if the last features stanza from the server has been parsed. A XMPP connection
         * handshake can invoke multiple features stanzas, e.g. when TLS is activated a second feature
         * stanza is send by the server. This is set to true once the last feature stanza has been
         * parsed.
         */
        protected final SynchronizationPoint<InterruptedException> lastStreamFeaturesReceived = new SynchronizationPoint<InterruptedException>(
                        XMPPSLConnection.this, "last stream features received from server");


        XmlPullParser parser;

        volatile boolean done;

        private Channel channel = null;
        private LLStream stream = null;
        private Boolean outgoing = null;

        private boolean queueWasShutdown = false; //packetWriter.queue.isShutdown();

        /**
         * @param ch
         */
        public PacketReader(LLStream stream ) {
            this.stream = stream;
            lastStreamFeaturesReceived.init();
        }

        /* (non-Javadoc)
         * @see org.jivesoftware.smack.serverless.XMPPReader#setInput(java.io.InputStream)
         */
        @Override
        public void setInput(Channel channel, InputStream stream, boolean outgoing ) throws SmackException {
            
            this.channel = channel;
            this.outgoing = outgoing;
            try {
                parser = PacketParserUtils.newXmppParser();
                parser.setInput(stream, "UTF-8");
            }
            catch (XmlPullParserException e) {
                throw new SmackException(e);
            }
            
        }
 
        /* (non-Javadoc)
         * @see org.jivesoftware.smack.serverless.XMPPReader#waitStreamOpened()
         */
        @Override
        public void waitStreamOpened() throws InterruptedException, NoResponseException {
            // TODO Auto-generated method stub
            lastStreamFeaturesReceived.checkIfSuccessOrWait();
            
        }
 
        /**
         * Initializes the reader in order to be used. The reader is initialized during the
         * first connection and when reconnecting due to an abruptly disconnection.
         */
        public void init() {
            done = false;

            Async.go(new Runnable() {
                public void run() {
                    parsePackets();
                }
            }, "Smack Packet Reader (" + getConnectionCounter() + ")");

         }

        /**
         * Shuts the stanza(/packet) reader down. This method simply sets the 'done' flag to true.
         */
        void shutdown() {
            done = true;
        }

        /**
         * Parse top-level packets in order to process them further.
         *
         * @param thread the thread that is being used by the reader to parse incoming packets.
         */
        private void parsePackets() {
            try {
                //initalOpenStreamSend.checkIfSuccessOrWait();
                int eventType = parser.getEventType();
                while (!done) {
                    switch (eventType) {
                    case XmlPullParser.START_TAG:
                        final String name = parser.getName();
                        LOGGER.fine("start_tag:" + name);
                        
                        switch (name) {
                        case Message.ELEMENT:
                        case IQ.IQ_ELEMENT:
                        case Presence.ELEMENT:
                            try {
                                parseAndProcessStanza(parser);
                            } finally {
                                //clientHandledStanzasCount = SMUtils.incrementHeight(clientHandledStanzasCount);
                            }
                            break;
                        case "stream":
                            // We found an opening stream.
                            if ("jabber:client".equals(parser.getNamespace(null))) {
                                streamId = parser.getAttributeValue("", "id");
                                String fromAddress = parser.getAttributeValue("", "from");
                                
                                LOGGER.fine("stream from:" + fromAddress );
                                
                                if ( !outgoing ) {
                                    streamInitiatingReceived(fromAddress);
                                }
                                
                                //assert(config.getXMPPServiceDomain().equals(reportedServerDomain));
                            }
                            break;
                        case "error":
                            StreamError streamError = PacketParserUtils.parseStreamError(parser);
                            saslFeatureReceived.reportFailure(new StreamErrorException(streamError));
                            // Mark the tlsHandled sync point as success, we will use the saslFeatureReceived sync
                            // point to report the error, which is checked immediately after tlsHandled in
                            // connectInternal().
                            tlsHandled.reportSuccess();
                            throw new StreamErrorException(streamError);
                        case "features":
                            parseFeatures(parser);
                            
                            lastStreamFeaturesReceived.reportSuccess();
                            break;
                        case "proceed":
                            try {
                                // Secure the connection by negotiating TLS
                                // TODO proceedTLSReceived();
                                // Send a new opening stream to the server
                                stream.openStream();
                            }
                            catch (Exception e) {
                                SmackException smackException = new SmackException(e);
                                tlsHandled.reportFailure(smackException);
                                throw e;
                            }
                            break;
                        case "failure":
                            String namespace = parser.getNamespace(null);
                            switch (namespace) {
                            case "urn:ietf:params:xml:ns:xmpp-tls":
                                // TLS negotiation has failed. The server will close the connection
                                // TODO Parse failure stanza
                                throw new SmackException("TLS negotiation has failed");
                            case "http://jabber.org/protocol/compress":
                                // Stream compression has been denied. This is a recoverable
                                // situation. It is still possible to authenticate and
                                // use the connection but using an uncompressed connection
                                // TODO Parse failure stanza
//                                compressSyncPoint.reportFailure(new SmackException(
//                                                "Could not establish compression"));
                                break;
                            case SaslStreamElements.NAMESPACE:
                                // SASL authentication has failed. The server may close the connection
                                // depending on the number of retries
                                final SASLFailure failure = PacketParserUtils.parseSASLFailure(parser);
                                getSASLAuthentication().authenticationFailed(failure);
                                break;
                            }
                            break;
                        case Challenge.ELEMENT:
                            // The server is challenging the SASL authentication made by the client
                            String challengeData = parser.nextText();
                            getSASLAuthentication().challengeReceived(challengeData);
                            break;
                        case Success.ELEMENT:
                            Success success = new Success(parser.nextText());
                            // We now need to bind a resource for the connection
                            // Open a new stream and wait for the response
                            stream.openStream();
                            // The SASL authentication with the server was successful. The next step
                            // will be to bind the resource
                            getSASLAuthentication().authenticated(success);
                            break;
                        case Compressed.ELEMENT:
                            // Server confirmed that it's possible to use stream compression. Start
                            // stream compression
                            // Initialize the reader and writer with the new compressed version
                            initReaderAndWriter();
                            // Send a new opening stream to the server
                            stream.openStream();
                            // Notify that compression is being used
//                            compressSyncPoint.reportSuccess();
                            break;
//                        case Enabled.ELEMENT:
//                            Enabled enabled = ParseStreamManagement.enabled(parser);
//                            if (enabled.isResumeSet()) {
//                                smSessionId = enabled.getId();
//                                if (StringUtils.isNullOrEmpty(smSessionId)) {
//                                    SmackException xmppException = new SmackException("Stream Management 'enabled' element with resume attribute but without session id received");
//                                    smEnabledSyncPoint.reportFailure(xmppException);
//                                    throw xmppException;
//                                }
//                                smServerMaxResumptimTime = enabled.getMaxResumptionTime();
//                            } else {
//                                // Mark this a non-resumable stream by setting smSessionId to null
//                                smSessionId = null;
//                            }
//                            clientHandledStanzasCount = 0;
//                            smWasEnabledAtLeastOnce = true;
//                            smEnabledSyncPoint.reportSuccess();
//                            LOGGER.fine("Stream Management (XEP-198): succesfully enabled");
//                            break;
//                        case Failed.ELEMENT:
//                            Failed failed = ParseStreamManagement.failed(parser);
//                            FailedNonzaException xmppException = new FailedNonzaException(failed, failed.getXMPPErrorCondition());
//                            // If only XEP-198 would specify different failure elements for the SM
//                            // enable and SM resume failure case. But this is not the case, so we
//                            // need to determine if this is a 'Failed' response for either 'Enable'
//                            // or 'Resume'.
//                            if (smResumedSyncPoint.requestSent()) {
//                                smResumedSyncPoint.reportFailure(xmppException);
//                            }
//                            else {
//                                if (!smEnabledSyncPoint.requestSent()) {
//                                    throw new IllegalStateException("Failed element received but SM was not previously enabled");
//                                }
//                                smEnabledSyncPoint.reportFailure(new SmackException(xmppException));
//                                // Report success for last lastFeaturesReceived so that in case a
//                                // failed resumption, we can continue with normal resource binding.
//                                // See text of XEP-198 5. below Example 11.
//                                lastFeaturesReceived.reportSuccess();
//                            }
//                            break;
//                        case Resumed.ELEMENT:
//                            Resumed resumed = ParseStreamManagement.resumed(parser);
//                            if (!smSessionId.equals(resumed.getPrevId())) {
//                                throw new StreamIdDoesNotMatchException(smSessionId, resumed.getPrevId());
//                            }
//                            // Mark SM as enabled and resumption as successful.
//                            smResumedSyncPoint.reportSuccess();
//                            smEnabledSyncPoint.reportSuccess();
//                            // First, drop the stanzas already handled by the server
//                            processHandledCount(resumed.getHandledCount());
//                            // Then re-send what is left in the unacknowledged queue
//                            List<Stanza> stanzasToResend = new ArrayList<>(unacknowledgedStanzas.size());
//                            unacknowledgedStanzas.drainTo(stanzasToResend);
//                            for (Stanza stanza : stanzasToResend) {
//                                sendStanzaInternal(stanza);
//                            }
//                            // If there where stanzas resent, then request a SM ack for them.
//                            // Writer's sendStreamElement() won't do it automatically based on
//                            // predicates.
//                            if (!stanzasToResend.isEmpty()) {
//                                requestSmAcknowledgementInternal();
//                            }
//                            LOGGER.fine("Stream Management (XEP-198): Stream resumed");
//                            break;
//                        case AckAnswer.ELEMENT:
//                            AckAnswer ackAnswer = ParseStreamManagement.ackAnswer(parser);
//                            processHandledCount(ackAnswer.getHandledCount());
//                            break;
//                        case AckRequest.ELEMENT:
//                            ParseStreamManagement.ackRequest(parser);
//                            if (smEnabledSyncPoint.wasSuccessful()) {
//                                sendSmAcknowledgementInternal();
//                            } else {
//                                LOGGER.warning("SM Ack Request received while SM is not enabled");
//                            }
//                            break;
                         default:
                             LOGGER.warning("Unknown top level stream element: " + name);
                             break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        LOGGER.fine("end_tag:" + parser.getName());
                        if (parser.getName().equals("stream")) {
                            if (!parser.getNamespace().equals("http://etherx.jabber.org/streams")) {
                                LOGGER.warning(this +  " </stream> but different namespace " + parser.getNamespace());
                                break;
                            }

                            // Check if the queue was already shut down before reporting success on closing stream tag
                            // received. This avoids a race if there is a disconnect(), followed by a connect(), which
                            // did re-start the queue again, causing this writer to assume that the queue is not
                            // shutdown, which results in a call to disconnect().
                            //final boolean queueWasShutdown = false; //packetWriter.queue.isShutdown();
//                            closingStreamReceived.reportSuccess();

                            if (queueWasShutdown) {
                                // We received a closing stream element *after* we initiated the
                                // termination of the session by sending a closing stream element to
                                // the server first
                                
                                //We can now close the channel
                                channel.close();
                                return;
                            } else {
                                // We received a closing stream element from the server without us
                                // sending a closing stream element first. This means that the
                                // server wants to terminate the session, therefore disconnect
                                // the connection
                                LOGGER.info(this
                                                + " received closing </stream> element."
                                                + " Server wants to terminate the connection, calling disconnect()");
                                disconnectStream();
                            }
                        }
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        // END_DOCUMENT only happens in an error case, as otherwise we would see a
                        // closing stream element before.
                        throw new SmackException(
                                        "Parser got END_DOCUMENT event. This could happen e.g. if the server closed the connection without sending a closing stream element");
                    }
                    eventType = parser.next();
                }
            }
            catch (Exception e) {
                //closingStreamReceived.reportFailure(e);
                // The exception can be ignored if the the connection is 'done'
                // or if the it was caused because the socket got closed
                if (!(done  
                         || !channel.isActive() 
                                )) {
                    // Close the connection and notify connection listeners of the
                    // error.
                    notifyConnectionError(e);
                }
            }
            finally {
                if (stream != null) {
                    stream.closeChannel();
                } else {
                    if (channel != null)
                        channel.close();
                }
            }
        }

        /**
         * 
         */
        public void disconnectStream() {
            
            channel.writeAndFlush("</stream:stream>");
            queueWasShutdown = true;
            
        }

        /**
         * @param fromAddress
         * @throws XmppStringprepException 
         * @throws InterruptedException 
         * @throws SmackException 
         * @throws NoResponseException 
         */
        private void streamInitiatingReceived(String fromAddress) throws XmppStringprepException, InterruptedException, NoResponseException, SmackException {
            LOGGER.fine("streamInitiatingReceived:" + fromAddress);
            Jid jid = JidCreate.from(fromAddress);
            
            LLStream stream = streams.get(jid);

            if ( stream == null ) {
                LLPresence presence = service.getPresenceByServiceName(jid);
                if (presence != null) {

                    stream = new LLStreamImpl(XMPPSLConnection.this, presence);
                    
                    streams.put(jid, stream);
                }
            }
            
            if ( stream == null ) {
                LOGGER.warning("Unknown service name '" +
                                fromAddress +
                                "' specified in stream initation, canceling.");
                shutdown();
            } else {
                
                stream.setReader(this);
                stream.setChannel(channel);
                
                stream.openStream();
                stream.sendFeatures();
                
            }
        }
    }


    /**
     * @param ch
     * @param to 
     * @param from 
     * @param outgoing 
     * @return
     */
    public XMPPReader createOutgoingXMPPReader(LLStream partner ) {
        
        return new PacketReader(partner);
        
    }
    
    public XMPPReader createIncomingXMPPReader() {
    
        return new PacketReader(null);
    }

    /**
     * @param jid
     * @return
     * @throws InterruptedException 
     * @throws NotConnectedException 
     * @throws SmackException 
     * @throws NoResponseException 
     */
    public LLStream getStreamByJid(Jid jid) throws InterruptedException, NotConnectedException {
        
        LLStream stream = streams.get(jid);

        if ( stream != null ) {
            return stream;                
        }

        LLPresence presence = service.getPresenceByServiceName(jid);
        if (presence != null) {

            stream = new LLStreamImpl(this, presence);
            
            service.createNewOutgoingChannel(stream);
            
            streams.put(jid, stream);
            
            try {
                stream.openOutgoingStream();
            }
            catch (NoResponseException e) {
                LOGGER.log(Level.WARNING, "Cannot open Stream" , e );
                throw new NotConnectedException();
            }
            
        }

        return stream;
    }

    /**
     * @return
     */
    public CharSequence getMe() {
        
        return configuration.getLocalPresence().getServiceName();
    }

    /**
     * @return
     */
    public SLService getDNSService() {
        return service;
    }

    /**
     * Setup an outgoing stream
     * 
     * @param channel
     * @param to 
     */
    public void setUpStream(Channel channel, Jid to) {
        
        StreamOpen stream = new StreamOpen(to, getMe(), null);
        
        channel.writeAndFlush(stream);
        
        // add listener to the answer

        
    }
    
    
}
