/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.SynchronizationPoint;
import org.jivesoftware.smack.compress.packet.Compress;
import org.jivesoftware.smack.compress.packet.Compressed;
import org.jivesoftware.smack.compression.XMPPInputOutputStream;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamOpen;
import org.jivesoftware.smack.serverless.packet.Failure;
import org.jivesoftware.smack.serverless.packet.Failure.CompressError;
import org.jivesoftware.smack.serverless.packet.StreamFeatures;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;

/**
 * @author anno
 *
 */
public class LLStream extends LLStreamModel {

    private static final Logger logger = Logger.getLogger(LLStream.class.getName());

    /**
     * 
     */
    final SynchronizationPoint<SmackException> compressSyncPoint;


    private XMPPInputOutputStream compressionHandler;

    /**
     * @param connection
     * @param remotePresence
     */
    public LLStream(XMPPLLConnection connection, LLPresence remotePresence) {
        super(connection, remotePresence);
        compressSyncPoint = new SynchronizationPoint<>(connection, "stream compression");
    }
    
    public void openOutgoingStream() throws InterruptedException, SmackException {
        openStream();
        getReader().waitStreamOpened();
        
        // If compression is enabled then request the server to use stream compression. XEP-170
        // recommends to perform stream compression before resource binding.
        maybeEnableCompression();

        
    }

    public void openStream() throws NotConnectedException, InterruptedException {
        logger.fine("openStream" );

       StreamOpen streamOpen = new StreamOpen(getRemotePresence().getServiceName(), connection.getMe(), null);
       send(streamOpen);
    }

    public void sendFeatures() throws NotConnectedException, InterruptedException {
        
        StreamFeatures features = new StreamFeatures();
        
        if ( connection.getConfiguration().isCompressionEnabled() && !isUsingCompression() ) {
            List<String> methods = new ArrayList<>();
            for (XMPPInputOutputStream handler : SmackConfiguration.getCompresionHandlers()) {
                 methods.add(handler.getCompressionMethod());
            }
            if ( !methods.isEmpty() )
                features.addFeature(new Compress.Feature(methods));
        }
        
        send(features);

    }
    
    public void send(Stanza packet) throws InterruptedException, NotConnectedException {
        logger.fine("send" );
        
        if ( getChannel() == null ) {
          throw new NotConnectedException("No Channel");
        } else {
            
            if ( !getReader().isRFC6120Compatible() ) {
                if ( packet instanceof IQ ) {
                    connection.sendToDebug(packet);
                    connection.sendAndReplyOnIQ( (IQ) packet);
                    return;
                }    
                if ( packet instanceof Presence ) {
                    logger.finest("Probably No support for presences. Trying...");
                }
            }
            packet.setFrom(packet.getFrom().asBareJid());
            getChannel().writeAndFlush(packet).await(connection.getPacketReplyTimeout());

        }
        
    }
    
    public void send(Nonza packet) throws NotConnectedException, InterruptedException {
        
        if ( getChannel() != null ) {
            getChannel().writeAndFlush(packet).await(connection.getPacketReplyTimeout());
        } else {
            throw new NotConnectedException("No Channel");
        }
        
    }

    public void closeChannel() {
        try {
            if ( getChannel() != null ) {
                getChannel().closeFuture().sync();
            } else {
                logger.fine("Closing empty channel");
            }
        }
        catch (InterruptedException e) {
            logger.warning("Close channel Interrupted");;
        }
        setChannel(null);
        compressSyncPoint.init();
    }

    public boolean isUsingCompression() {
        return compressionHandler != null && compressSyncPoint.wasSuccessful();
    }

    /**
     * <p>
     * Starts using stream compression that will compress network traffic. Traffic can be
     * reduced up to 90%. Therefore, stream compression is ideal when using a slow speed network
     * connection. However, the server and the client will need to use more CPU time in order to
     * un/compress network data so under high load the server performance might be affected.
     * </p>
     * <p>
     * Stream compression has to have been previously offered by the server. Currently only the
     * zlib method is supported by the client. Stream compression negotiation has to be done
     * before authentication took place.
     * </p>
     *
     * @throws NotConnectedException 
     * @throws SmackException
     * @throws NoResponseException 
     * @throws InterruptedException 
     */
    private void maybeEnableCompression() throws NotConnectedException, NoResponseException, SmackException, InterruptedException {
        if (!connection.getConfiguration().isCompressionEnabled()) {
            return;
        }
        Compress.Feature compression = getFeature(Compress.Feature.ELEMENT, Compress.NAMESPACE);
        if (compression == null) {
            // Server does not support compression
            return;
        }
        // If stream compression was offered by the server and we want to use
        // compression then send compression request to the server
        compressionHandler = maybeGetCompressionHandler(compression);
        if (compressionHandler != null) {
            send(new Compress(compressionHandler.getCompressionMethod()));
            compressSyncPoint.checkIfSuccessOrWaitOrThrow();
        } else {
            logger.warning("Could not enable compression because no matching handler/method pair was found");
        }
    }

    /**
     * Returns the compression handler that can be used for one compression methods offered by the server.
     * 
     * @return a instance of XMPPInputOutputStream or null if no suitable instance was found
     * 
     */
    private static XMPPInputOutputStream maybeGetCompressionHandler(Compress.Feature compression) {
        for (XMPPInputOutputStream handler : SmackConfiguration.getCompresionHandlers()) {
                String method = handler.getCompressionMethod();
                if (compression.getMethods().contains(method))
                    return handler;
        }
        return null;
    }

    private static XMPPInputOutputStream maybeGetCompressionHandler(Compress compression) {
        for (XMPPInputOutputStream handler : SmackConfiguration.getCompresionHandlers()) {
            if (compression.method.equalsIgnoreCase(handler.getCompressionMethod()))
                return handler;
        }
        return null;
    }

    /**
     * @param compression
     * @throws InterruptedException 
     * @throws NotConnectedException 
     */
    public void handleRequestCompression(Compress compression) throws NotConnectedException, InterruptedException {
        logger.finest("handleRequestCompression");
        Nonza result;
        if ( connection.getConfiguration().isCompressionEnabled()) {
            compressionHandler = maybeGetCompressionHandler(compression);
            if ( compressionHandler != null ) {
                result = Compressed.INSTANCE;
                send(result);
                addCompressionToChannel();
                compressSyncPoint.reportSuccess();
            } else {
                result = new Failure(CompressError.unsupportedMethod);
                send(result);
            }
        } else {
            result = new Failure(CompressError.setupFailed);
            send(result);
        }
    }

    /**
     * 
     */
    void addCompressionToChannel() {
        logger.finest("addCompressionToChannel");
        
        getChannel().pipeline().addFirst("zlib-inflater" , new JdkZlibDecoder());
        getChannel().pipeline().addFirst("zlib-deflater" , new JdkZlibEncoder());
        
    }



}
