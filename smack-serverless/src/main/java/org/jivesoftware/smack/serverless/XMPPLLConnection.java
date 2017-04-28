/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SmackException.SecurityRequiredException;
import org.jivesoftware.smack.SynchronizationPoint;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.StreamErrorException;
import org.jivesoftware.smack.compress.packet.Compress;
import org.jivesoftware.smack.compress.packet.Compressed;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamError;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.Challenge;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.SASLFailure;
import org.jivesoftware.smack.sasl.packet.SaslStreamElements.Success;
import org.jivesoftware.smack.serverless.service.LLPresenceListener;
import org.jivesoftware.smack.serverless.service.jmdns.JmDNSService;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.caps.CapsVersionAndHash;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
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
 */
public class XMPPLLConnection extends AbstractXMPPConnection {

    private static final Logger logger = Logger.getLogger(XMPPLLConnection.class.getName());

    /** 
     * Counter to uniquely identify streams within a connection that are created.
     */
    private final AtomicInteger streamCounter = new AtomicInteger(0);

    private LLService service;
    private final LLConnectionConfiguration configuration;
    private final LLPresence localPresence;

    private DomainBareJid serviceDomain;

    private Map<Jid, LLStream> streams = new TreeMap<>();

    private EntityCapsManager capsManager;
    
    private LLPresenceListener myPresenceListener = new MyPresenceListener();
 

    /**
     * @param configuration
     */
    public XMPPLLConnection(LLConnectionConfiguration configuration) {
        super(configuration);
        
        setFromMode(FromMode.USER);

        this.configuration = configuration;
        this.localPresence = configuration.getLocalPresence();

        // Entity Capabilities
        capsManager = EntityCapsManager.getInstanceFor(this);
        capsManager.addCapsVerListener(new CapsPresenceRenewer());

    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#getXMPPServiceDomain()
     */
    @Override
    public DomainBareJid getXMPPServiceDomain() {
        // TODO Auto-generated method stub
        return serviceDomain;
    }

    /**
     * @return
     */
    public CharSequence getMe() {
    
        return localPresence.getServiceName();
    }

    /**
     * @return
     */
    public LLService getDNSService() {
        return service;
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#isSecureConnection()
     */
    @Override
    public boolean isSecureConnection() {
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#isUsingCompression()
     */
    @Override
    public boolean isUsingCompression() {
        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#connectInternal()
     */
    @Override
    protected void connectInternal() throws SmackException, IOException, XMPPException, InterruptedException {
        logger.fine("connectInternal");

        initDebugPipe();

        // Start Service
        capsManager.updateLocalEntityCaps();

        // Create a basic presence (only set name, and status to available)
        service = JmDNSService.create(localPresence, this);

        service.prepareBind(localPresence);

        service.startServerSocket(configuration.getInetAddress(), localPresence.getPort());

        // Add presence listener. The presence listener will gather
        // entity caps data
        service.addPresenceListener(myPresenceListener);

        tlsHandled.reportSuccess();
        saslFeatureReceived.reportSuccess();

        EntityBareJid jid = JidCreate.entityBareFrom(localPresence.getServiceName());
        user = JidCreate.entityFullFrom(jid, Resourcepart.from("local"));
        serviceDomain = user.asDomainBareJid();

    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#loginInternal(java.lang.String, java.lang.String,
     * org.jxmpp.jid.parts.Resourcepart)
     */
    @Override
    protected void loginInternal(String username, String password, Resourcepart resource)
                    throws XMPPException, SmackException, IOException, InterruptedException {

        EntityBareJid realizedServiceName = service.registerService(localPresence);

        JidCreate.entityBareFrom(localPresence.getServiceName());

        localPresence.setServiceName(realizedServiceName);

        user = JidCreate.entityFullFrom(realizedServiceName, Resourcepart.from("local"));
        serviceDomain = user.asDomainBareJid();

        afterSuccessfulLogin(false);

    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#afterFeaturesReceived()
     */
    @Override
    protected void afterFeaturesReceived()
                    throws SecurityRequiredException, NotConnectedException, InterruptedException {

        if (hasFeature(DiscoverInfo.ELEMENT, DiscoverInfo.NAMESPACE)) {

        }

        logger.fine("afterFeaturesReceived");
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#shutdown()
     */
    @Override
    protected void shutdown() {
        logger.fine("shutdown");
        
        //capsManager.removeCapsVerListener(new CapsPresenceRenewer());

        if (service != null) {
            try {
                service.close();
            }
            catch (IOException | InterruptedException e) {
                logger.log(Level.FINE, "Service close not succesfull", e);
            }
        }

    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendStanzaInternal(org.jivesoftware.smack.packet.Stanza)
     */
    @Override
    protected void sendStanzaInternal(Stanza packet) throws InterruptedException, NotConnectedException {
        logger.fine("sendStanzaInternal " + packet);

        if (packet.getTo() != null && !packet.getTo().equals(getServiceName()) ) {

            LLStream stream = getStreamByJid(packet.getTo().asBareJid());
            if (stream != null) {

                //We delegate to the stream
                stream.send(packet);

                firePacketSendingListeners(packet);
                return;

            }
            else {
                logger.warning("no stream found for " + packet.getTo());
            }

        }
        
        if ( packet.toXML().toString().startsWith("SPAM") ) {
            spam();
            return;
        }
        
        //There is no real world destination, but we can generate some auto responses
        // Handle these as successful sent messages
        sendToDebug(packet);
        firePacketSendingListeners(packet);

        if (packet instanceof Presence) {

            try {
                Presence pres = (Presence) packet;

                localPresence.setStatus(LLPresence.convertStatus(pres.getMode()));
                localPresence.setMsg(pres.getStatus());

                service.updateLocalPresence(localPresence);

            }
            catch (XMPPException e) {
                logger.warning("Presence update not succesfull:" + e.getMessage());
                logger.log(Level.FINER, "", e);

            }
            return;

        }

        if (packet instanceof RosterPacket) {
            
            rosterRequest((RosterPacket) packet);
            return;
        }

        if (packet.getTo() == null || packet.getTo().equals( getServiceName() ) ) {
            if ( packet instanceof DiscoverInfo ) {
                
                //return my supported features
                sendSupportedFeatures((DiscoverInfo) packet );
                return;
            }
        }

        if ( packet instanceof IQ ) {
            sendAndReplyOnIQ( (IQ) packet);
            return;
        }

        logger.info("no addressee or reponse for " + packet);

    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.smack.AbstractXMPPConnection#sendNonza(org.jivesoftware.smack.packet.Nonza)
     */
    @Override
    public void sendNonza(Nonza element) throws NotConnectedException, InterruptedException {
        logger.info("no addressee for " + element);
    
    }

    /**
     * @param packet
     * @throws InterruptedException
     */
    private void rosterRequest(RosterPacket req) {
        logger.finest("rosterRequest");
        if (req.getType() == Type.get) {
            RosterPacket response = new RosterPacket();
            response.setStanzaId(req.getStanzaId());
            response.setType(Type.result);
            for (LLPresence pres : service.getPresences()) {
                if (!pres.getServiceName().equals(localPresence.getServiceName())) {
                    response.addRosterItem(pres.getRosterPacketItem());
                }
            }

            autoRespond(response);

            // send presences
            for (LLPresence pres : service.getPresences()) {
                if (!pres.getServiceName().equals(localPresence.getServiceName())) {

                    autoRespond(pres.getPresenceStanza());
                }
            }

        }
        else {
            logger.fine("unhandled roster packet:" + req);
        }

    }

    /**
     * @param packet
     * @throws InterruptedException 
     */
    private void sendSupportedFeatures(DiscoverInfo request) throws InterruptedException {
        logger.finest("sendSupportedFeatures");
        
        if (request.getNode() == null) {
            
            if ( request.getType().equals(Type.get) ) {
                DiscoverInfo response = new DiscoverInfo();
                response.setStanzaId(request.getStanzaId());
                response.setFrom(request.getTo());
                response.setTo(request.getFrom());
                response.setType(IQ.Type.result);
                response.setNode(capsManager.getLocalNodeVer());

                // Add discover info
                // Set this client identity
                DiscoverInfo.Identity identity = new DiscoverInfo.Identity("gateway", "Serverless", "xmpp");
                response.addIdentity(identity);
                // Add the registered features to the response
                // Add Entity Capabilities (XEP-0115) feature node.
                response.addFeature("http://jabber.org/protocol/caps");
                response.addFeature("http://jabber.org/protocol/disco#info");
                autoRespond(response);
                return;
            }
            
        }

        sendAndReplyOnIQ(request);
                
    }


    
    /**
     * @param packet
     * @throws InterruptedException 
     */
    void sendAndReplyOnIQ(IQ iq) throws InterruptedException {
        logger.finest("sendAndReplyOnIQ");
        
        switch (iq.getType()) {
        case get:
            sendIQError(iq);
            break;
            
        case set:
            sendIQError(iq);
            break;

        default:
            break;
        }
        
    }

    /**
     * @param iq
     * @throws InterruptedException 
     */
    private void sendIQError(IQ iq) throws InterruptedException {
        logger.finest("sendIQError");

        IQ err = IQ.createErrorResponse(iq, XMPPError.getBuilder((
                        XMPPError.Condition.feature_not_implemented)));
        
        autoRespond(err);        
    }



    // private final SynchronizationPoint<Exception> initalOpenStreamSend = new SynchronizationPoint<>(
    // this, "initial open stream element send to server");
    //
    // /**
    // *
    // */
    // private final SynchronizationPoint<XMPPException> maybeCompressFeaturesReceived = new
    // SynchronizationPoint<XMPPException>(
    // this, "stream compression feature");
    //
    // /**
    // *
    // */
    // private final SynchronizationPoint<SmackException> compressSyncPoint = new SynchronizationPoint<>(
    // this, "stream compression");
    //
    // /**
    // * A synchronization point which is successful if this connection has received the closing
    // * stream element from the remote end-point, i.e. the server.
    // */
    // private final SynchronizationPoint<Exception> closingStreamReceived = new SynchronizationPoint<>(
    // this, "stream closing element received");

    private void initReaderAndWriter() throws IOException {
        // InputStream is = socket.getInputStream();
        // OutputStream os = socket.getOutputStream();
        // if (compressionHandler != null) {
        // is = compressionHandler.getInputStream(is);
        // os = compressionHandler.getOutputStream(os);
        // }
        // // OutputStreamWriter is already buffered, no need to wrap it into a BufferedWriter
        // writer = new OutputStreamWriter(os, "UTF-8");
        // reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        //
        // // If debugging is enabled, we open a window and write out all network traffic.
        // initDebugger();
    }

    /**
     * Sends out a notification that there was an error with the connection and closes the connection. Also prints the
     * stack trace of the given exception
     *
     * @param e the exception that causes the connection close event.
     */
    private synchronized void notifyConnectionError(Exception e) {
        // Listeners were already notified of the exception, return right here.
        // if ((packetReader == null || packetReader.done) &&
        // (packetWriter == null || packetWriter.done())) return;

        // Closes the connection temporary. A reconnection is possible
        // Note that a connection listener of XMPPTCPConnection will drop the SM state in
        // case the Exception is a StreamErrorException.
        // instantShutdown();

        // Notify connection listeners of the error.
        callConnectionClosedOnErrorListener(e);
    }

    class PacketReader implements XMPPReader {

        /**
         * Set to success if the last features stanza from the server has been parsed. A XMPP connection handshake can
         * invoke multiple features stanzas, e.g. when TLS is activated a second feature stanza is send by the server.
         * This is set to true once the last feature stanza has been parsed.
         */
        protected final SynchronizationPoint<InterruptedException> lastStreamFeaturesReceived = new SynchronizationPoint<InterruptedException>(
                        XMPPLLConnection.this, "last stream features received from remote");

        protected final SynchronizationPoint<InterruptedException> streamOpenConfirmed = new SynchronizationPoint<InterruptedException>(
                        XMPPLLConnection.this, "Stream open confirmed");

        XmlPullParser parser;

        volatile boolean done = true;

        private Channel channel = null;
        private LLStream stream = null;
        private Boolean outgoing = null;

        private boolean queueWasShutdown = false;

        /**
         * two flavors of stream open, with or without version >= '1.0'
         */
        private boolean streamVersion1 = false;

        /**
         * @param ch
         */
        public PacketReader(LLStream stream) {
            this.stream = stream;
            streamOpenConfirmed.init();
            lastStreamFeaturesReceived.init();
        }

        /*
         * (non-Javadoc)
         * @see org.jivesoftware.smack.serverless.XMPPReader#setInput(java.io.InputStream)
         */
        @Override
        public void setInput(Channel channel, InputStream stream, boolean outgoing) throws SmackException {

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

        /*
         * (non-Javadoc)
         * @see org.jivesoftware.smack.serverless.XMPPReader#waitStreamOpened()
         */
        @Override
        public void waitStreamOpened() throws InterruptedException, NoResponseException {
            // TODO Auto-generated method stub
            streamOpenConfirmed.checkIfSuccessOrWait();

            if (streamVersion1) {
                lastStreamFeaturesReceived.checkIfSuccessOrWait();
            }

        }

        /**
         * Initializes the reader in order to be used. The reader is initialized during the first connection and when
         * reconnecting due to an abruptly disconnection.
         */
        @Override
        public void init() {
            
            if ( done ) {
                done = false;

                Async.go(new Runnable() {
                    @Override
                    public void run() {
                        parsePackets();
                    }
                }, "Smack Packet Reader (" + streamCounter.getAndIncrement() + ")");
            }

        }

        /**
         * Shuts the stanza(/packet) reader down. This method simply sets the 'done' flag to true.
         */
        void shutdown() {
            done = true;
        }

        /*
         * (non-Javadoc)
         * @see org.jivesoftware.smack.serverless.XMPPReader#isRFC6120Compatible()
         */
        @Override
        public boolean isRFC6120Compatible() {
            logger.finest("isRFC6120Compatible");
            // TODO Auto-generated method stub
            return streamVersion1;
        }

        /**
         * Parse top-level packets in order to process them further.
         *
         * @param thread the thread that is being used by the reader to parse incoming packets.
         */
        @SuppressWarnings("FutureReturnValueIgnored")
        private void parsePackets() {
            try {
                // initalOpenStreamSend.checkIfSuccessOrWait();
                int eventType = parser.getEventType();
                while (!done) {
                    switch (eventType) {
                    case XmlPullParser.START_TAG:
                        final String name = parser.getName();
                        logger.fine("start_tag:" + name);

                        switch (name) {
                        case Message.ELEMENT:
                        case IQ.IQ_ELEMENT:
                        case Presence.ELEMENT:
                            try {
                                parseAndProcessStanza(parser);
                            }
                            finally {
                                // clientHandledStanzasCount = SMUtils.incrementHeight(clientHandledStanzasCount);
                            }
                            break;
                        case "stream":
                            // We found an opening stream.
                            if ("jabber:client".equals(parser.getNamespace(null))) {
                                streamId = parser.getAttributeValue("", "id");
                                String fromAddress = parser.getAttributeValue("", "from");
                                String version = parser.getAttributeValue("", "version");
                                streamVersion1 = (version != null && version.equals("1.0"));
                                logger.fine("stream from:" + fromAddress);

                                if (!outgoing) {

                                    streamInitiatingReceived(fromAddress);
                                }
                                else {

                                    streamOpenConfirmed.reportSuccess();
                                }

                                // assert(config.getXMPPServiceDomain().equals(reportedServerDomain));
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
                            this.parseFeatures(parser);
                            
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
                                if ( stream != null ) {
                                    stream.compressSyncPoint.reportFailure(new SmackException("Could not establish compression"));
                                }
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
                        case Compress.ELEMENT:
                            // Other client want to initialize a compressed stream
                            Compress compress = LLPacketParserUtils.parseCompress(parser);
                            if ( stream != null ) {
                                stream.handleRequestCompression(compress);
                            }
                            break;
                        case Compressed.ELEMENT:
                            // Server confirmed that it's possible to use stream compression. Start
                            // stream compression
                            if ( stream != null ) {
                                // Initialize the reader and writer with the new compressed version
                                stream.addCompressionToChannel();
                                // Send a new opening stream to the server
                                stream.openStream();
                                // Notify that compression is being used
                                stream.compressSyncPoint.reportSuccess();
                            }
                            break;
                        // case Enabled.ELEMENT:
                        // Enabled enabled = ParseStreamManagement.enabled(parser);
                        // if (enabled.isResumeSet()) {
                        // smSessionId = enabled.getId();
                        // if (StringUtils.isNullOrEmpty(smSessionId)) {
                        // SmackException xmppException = new SmackException("Stream Management 'enabled' element with
                        // resume attribute but without session id received");
                        // smEnabledSyncPoint.reportFailure(xmppException);
                        // throw xmppException;
                        // }
                        // smServerMaxResumptimTime = enabled.getMaxResumptionTime();
                        // } else {
                        // // Mark this a non-resumable stream by setting smSessionId to null
                        // smSessionId = null;
                        // }
                        // clientHandledStanzasCount = 0;
                        // smWasEnabledAtLeastOnce = true;
                        // smEnabledSyncPoint.reportSuccess();
                        // logger.fine("Stream Management (XEP-198): succesfully enabled");
                        // break;
                        // case Failed.ELEMENT:
                        // Failed failed = ParseStreamManagement.failed(parser);
                        // FailedNonzaException xmppException = new FailedNonzaException(failed,
                        // failed.getXMPPErrorCondition());
                        // // If only XEP-198 would specify different failure elements for the SM
                        // // enable and SM resume failure case. But this is not the case, so we
                        // // need to determine if this is a 'Failed' response for either 'Enable'
                        // // or 'Resume'.
                        // if (smResumedSyncPoint.requestSent()) {
                        // smResumedSyncPoint.reportFailure(xmppException);
                        // }
                        // else {
                        // if (!smEnabledSyncPoint.requestSent()) {
                        // throw new IllegalStateException("Failed element received but SM was not previously enabled");
                        // }
                        // smEnabledSyncPoint.reportFailure(new SmackException(xmppException));
                        // // Report success for last lastFeaturesReceived so that in case a
                        // // failed resumption, we can continue with normal resource binding.
                        // // See text of XEP-198 5. below Example 11.
                        // lastFeaturesReceived.reportSuccess();
                        // }
                        // break;
                        // case Resumed.ELEMENT:
                        // Resumed resumed = ParseStreamManagement.resumed(parser);
                        // if (!smSessionId.equals(resumed.getPrevId())) {
                        // throw new StreamIdDoesNotMatchException(smSessionId, resumed.getPrevId());
                        // }
                        // // Mark SM as enabled and resumption as successful.
                        // smResumedSyncPoint.reportSuccess();
                        // smEnabledSyncPoint.reportSuccess();
                        // // First, drop the stanzas already handled by the server
                        // processHandledCount(resumed.getHandledCount());
                        // // Then re-send what is left in the unacknowledged queue
                        // List<Stanza> stanzasToResend = new ArrayList<>(unacknowledgedStanzas.size());
                        // unacknowledgedStanzas.drainTo(stanzasToResend);
                        // for (Stanza stanza : stanzasToResend) {
                        // sendStanzaInternal(stanza);
                        // }
                        // // If there where stanzas resent, then request a SM ack for them.
                        // // Writer's sendStreamElement() won't do it automatically based on
                        // // predicates.
                        // if (!stanzasToResend.isEmpty()) {
                        // requestSmAcknowledgementInternal();
                        // }
                        // logger.fine("Stream Management (XEP-198): Stream resumed");
                        // break;
                        // case AckAnswer.ELEMENT:
                        // AckAnswer ackAnswer = ParseStreamManagement.ackAnswer(parser);
                        // processHandledCount(ackAnswer.getHandledCount());
                        // break;
                        // case AckRequest.ELEMENT:
                        // ParseStreamManagement.ackRequest(parser);
                        // if (smEnabledSyncPoint.wasSuccessful()) {
                        // sendSmAcknowledgementInternal();
                        // } else {
                        // logger.warning("SM Ack Request received while SM is not enabled");
                        // }
                        // break;
                        default:
                            logger.warning("Unknown top level stream element: " + name);
                            break;
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        logger.fine("end_tag:" + parser.getName());
                        if (parser.getName().equals("stream")) {
                            if (!parser.getNamespace().equals("http://etherx.jabber.org/streams")) {
                                logger.warning(this + " </stream> but different namespace " + parser.getNamespace());
                                break;
                            }

                            // Check if the queue was already shut down before reporting success on closing stream tag
                            // received. This avoids a race if there is a disconnect(), followed by a connect(), which
                            // did re-start the queue again, causing this writer to assume that the queue is not
                            // shutdown, which results in a call to disconnect().
                            // final boolean queueWasShutdown = false; //packetWriter.queue.isShutdown();
                            // closingStreamReceived.reportSuccess();

                            if (queueWasShutdown) {
                                // We received a closing stream element *after* we initiated the
                                // termination of the session by sending a closing stream element to
                                // the server first

                                // We can now close the channel
                                channel.close();
                                return;
                            }
                            else {
                                // We received a closing stream element from the server without us
                                // sending a closing stream element first. This means that the
                                // server wants to terminate the session, therefore disconnect
                                // the connection
                                logger.info(this + " received closing </stream> element from " + stream
                                                + ". Remote wants to terminate the connection, calling disconnect()");
                                disconnectStream();
                                return;
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
                // closingStreamReceived.reportFailure(e);
                // The exception can be ignored if the the connection is 'done'
                // or if the it was caused because the socket got closed
                if (!(done || !channel.isActive())) {
                    // Close the connection and notify connection listeners of the
                    // error.
                    notifyConnectionError(e);
                }
            }
            finally {
                if (stream != null) {
                    streams.remove(stream.getRemotePresence().getServiceName());
                    stream.closeChannel();
                }
                else {
                    if (channel != null)
                        channel.close();
                }
            }
        }

        /**
         * Parse fEatures for this stream
         * @throws Exception 
         */
        private void parseFeatures(XmlPullParser parser) throws Exception {
            logger.finest("parseFeatures");
            
            List<ExtensionElement> features = LLPacketParserUtils.parseFeatures(parser);
            if ( stream != null ) {
                stream.addStreamFeatures(features);
                
            }
            lastStreamFeaturesReceived.reportSuccess();

        }

        /**
         * 
         */
        @SuppressWarnings("FutureReturnValueIgnored")
        public void disconnectStream() {

            channel.writeAndFlush("</stream:stream>");
            queueWasShutdown = true;

        }

        /**
         * @param fromAddress
         * @param streamVersion1
         * @throws XmppStringprepException
         * @throws InterruptedException
         * @throws SmackException
         * @throws NoResponseException
         */
        private void streamInitiatingReceived(String fromAddress)
                        throws XmppStringprepException, InterruptedException, NoResponseException, SmackException {
            logger.fine("streamInitiatingReceived:" + fromAddress);
            Jid jid = JidCreate.from(fromAddress);

            stream = streams.get(jid);

            if (stream == null) {
                LLPresence presence = service.getPresenceByServiceName(jid);
                if (presence != null) {

                    stream = new LLStream(XMPPLLConnection.this, presence);

                    streams.put(jid, stream);
                }
            }

            if (stream == null) {
                logger.warning("Unknown service name '" + fromAddress + "' specified in stream initation, canceling.");
                shutdown();
            }
            else {

                stream.setReader(this);
                stream.setChannel(channel);

                stream.openStream();
                if (streamVersion1)
                    stream.sendFeatures();

            }
        }
    }

    /**
     * @param partner
     * @return
     */
    public XMPPReader createOutgoingXMPPReader(LLStream partner) {

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
     */
    private LLStream getStreamByJid(Jid jid) throws InterruptedException, NotConnectedException {

        LLStream stream = streams.get(jid);

        if (stream != null) {
            
            if ( stream.getChannel() == null || !stream.getChannel().isOpen() ) {
                throw new InterruptedException("Channel not open for user:" + jid);
            }
            return stream;
        }

        LLPresence presence = service.getPresenceByServiceName(jid);
        if (presence == null) {
            throw new NotConnectedException("Unknown user:" + jid);
        }

        LLStream streamImpl = new LLStream(this, presence);
        streamImpl.setReader(createOutgoingXMPPReader(streamImpl));

        service.createNewOutgoingChannel(streamImpl);

        streams.put(jid, streamImpl);

        try {
            streamImpl.openOutgoingStream();
        }
        catch (SmackException e) {
            logger.log(Level.WARNING, "Cannot open Stream", e);
            throw new NotConnectedException("Cannot open Stream");
        }

        return streamImpl;
    }

    /**
     * Setup an outgoing stream
     * 
     * @param channel
     * @param to
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    public void setUpStream(Channel channel, Jid to) {

        StreamOpen stream = new StreamOpen(to, getMe(), null);

        channel.writeAndFlush(stream);

        // add listener to the answer

    }
    
    private class CapsPresenceRenewer implements EntityCapsManager.CapsVerListener {

        @Override
        public void capsVerUpdated(CapsVersionAndHash ver) {
            try {
                LLPresence presence = localPresence;
                presence.setHash(EntityCapsManager.DEFAULT_HASH);
                presence.setNode(capsManager.getEntityNode());
                presence.setVer(ver.version);
                if (service != null)
                    service.updateLocalPresence(presence);
            }
            catch (XMPPException xe) {
                logger.log(Level.INFO, "not able to udate local presence", xe);
            }
        }
    }

    private class MyPresenceListener implements LLPresenceListener {

        @Override
        public void presenceNew(LLPresence presence) {
            logger.fine("presenceNew:" + presence);
            if (presence.getHash() != null && presence.getNode() != null && presence.getVer() != null) {
                // Add presence to caps manager
                capsManager.addUserCapsNode(presence.getServiceName(), presence.getNode(), presence.getVer());
            }

            if (isAuthenticated()) {
                // Add/modify user to Roster if not already and not myself
                if (!presence.getServiceName().equals(localPresence.getServiceName())) {

                    RosterPacket rosterPacket = presence.getRosterPacket();
                    autoRespond(rosterPacket);

                    // simulate the reception of a presence update
                    Presence packet = presence.getPresenceStanza();

                    autoRespond(packet);
                }

            }
        }

        @Override
        public void presenceRemove(LLPresence presence) {
            logger.fine("presenceRemove:" + presence);
            // simulate the reception of a presence update
            Presence packet = new Presence(org.jivesoftware.smack.packet.Presence.Type.unavailable);
            packet.setFrom(presence.getServiceName());

            autoRespond(packet);
            
            //TODO Maybe also remove from Roster?
            
        }
    }

    /**
     * 
     */
    public void spam() {

        // Dump some state
        logger.info("==== DUMP Connection State (start) ====");
        logger.info("Local presence:" + localPresence.getServiceName());
        logger.info("serviceDomain:" + serviceDomain);
        logger.info("streams:" + streams.size());
        for (LLStream stream : streams.values()) {
            logger.info("stream:" + stream );
        }

        getDNSService().spam();
        
        // get Roster cq. all locally known users
        Roster roster = Roster.getInstanceFor(this);
        Set<RosterEntry> list = roster.getEntries();
        if ( list.size() > 0 ) {
            logger.info("get Roster:");
        
          for (RosterEntry rosterEntry : list) {
              logger.info("Roster entry: " + rosterEntry.toString() + " " + roster.getPresence(rosterEntry.getJid()));
          }
        } else {
            logger.info("Empty roster");
        }
        
        logger.info("==== DUMP Connection State (end) ======");
        
    }

    /**
     * 
     */
    private void initDebugPipe() {
    
        reader = new Reader() {
    
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                return (len < cbuf.length - off ? len : cbuf.length - off);
            }
    
            @Override
            public void close() throws IOException {
    
            }
        };
    
        writer = new Writer() {
    
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
            }
    
            @Override
            public void flush() throws IOException {
            }
    
            @Override
            public void close() throws IOException {
            }
    
        };
    
        initDebugger();
    
        logger.finest("initDebugPipe");
        
        if (debugger != null) {
            if (debugger.getReaderListener() != null) {
                addAsyncStanzaListener(debugger.getReaderListener(), null);
            }
            if (debugger.getWriterListener() != null) {
                addPacketSendingListener(debugger.getWriterListener(), null);
            }
        }
    
    }

    /**
     * @return
     */
    public Writer getDebugWriter() {
        return writer;
    }

    /**
     * @return
     */
    public Reader getDebugReader() {
        return reader;
    }

    /**
     * @param stanza
     */
    void sendToDebug(Stanza stanza) {
        logger.finest("sendToDebug");
        if (debugger != null) {
            // TODO mark stanza as auto generated
            String xml = "AUTO:" + stanza.toXML().toString();
            char[] arr = xml.toCharArray();
            try {
                writer.write(arr, 0, arr.length);
                writer.flush();
            }
            catch (IOException e) {
                logger.fine("debug write failed:" + e.getMessage());
            }
        }
    }

    
    /**
     * @param stanza
     */
    public void autoRespond(Stanza stanza) {
        logger.finest("autoRespond");
        
        if (debugger != null) {
            String xml = "AUTO:" + stanza.toXML().toString();
            char[] arr = xml.toCharArray();
            try {
                reader.read(arr, 0, arr.length);
            }
            catch (IOException e) {
                logger.fine("debug read failed:" + e.getMessage());
            }
        }

        try {
            processStanza(stanza);
        }
        catch (InterruptedException e) {
            logger.log(Level.INFO, "process Presence", e);
        }

    }

}
