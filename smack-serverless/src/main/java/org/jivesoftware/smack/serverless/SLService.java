/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.Logger;

import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.serverless.service.LLPresenceDiscoverer;
import org.jivesoftware.smack.serverless.service.LLPresenceListener;
import org.jivesoftware.smack.serverless.service.jmdns.DNSException;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.Jid;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * @author anno
 *
 */
public abstract class SLService {
    
    private static final Logger LOGGER = Logger.getLogger(SLService.class.getName());


    static final int DEFAULT_MIN_PORT = 2300;
    static final int DEFAULT_MAX_PORT = 2400;
    static final AttributeKey<Jid> JID_KEY = AttributeKey.newInstance("jid.key");

    static {
        SmackConfiguration.getVersion();
    }

    protected LLPresence presence;
    private LLPresenceDiscoverer presenceDiscoverer;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final ChannelGroup channels;
    private XMPPSLConnection xmppslConnection;
    
    
    protected SLService(LLPresence presence, LLPresenceDiscoverer discoverer) {
        this.presence = presence;
        this.presenceDiscoverer = discoverer;
        
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        

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



    
    public void init(XMPPSLConnection connection) throws XMPPException, InterruptedException {
        // allocate a new port for remote clients to connect to
        int socketPort = bindRange(DEFAULT_MIN_PORT, DEFAULT_MAX_PORT);
        presence.setPort(socketPort);
        this.xmppslConnection = connection;

        // register service on the allocated port
        registerService();
        
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).handler(
                        new LoggingHandler(LogLevel.TRACE)).childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                            @Override
                            public void initChannel(SocketChannel ch) throws Exception {
                                
                                LOGGER.fine("initChannel:" + ch);
                                
                                ChannelPipeline pipeline = ch.pipeline();
                                
                                pipeline.addLast("xmpp-parser", new XMLFrameDecoder( xmppslConnection ));
                                pipeline.addLast("xmppDecoder", new XmppStanzaDecoder());
                                pipeline.addLast("nonzaDecoder", new XmppNonzaDecoder());
                                
                                
                            }
                        });

        Channel channel = b.bind(socketPort).sync().channel();
        LOGGER.fine("init:" + channel);
        
        //f.closeFuture().sync()

    }

    
    /**
     * @param to
     * @return
     * @throws InterruptedException 
     * @throws NotConnectedException 
     * @throws SmackException 
     * @throws DNSException 
     */
    private Channel createNewOutgoingChannel(Jid to) throws InterruptedException, NotConnectedException {
        LOGGER.fine("createNewOutgoingChannel:" + to);
        // If no connection exists, look up the presence and connect according to.
        final LLPresence remotePresence = getPresenceByServiceName(to);

        if (remotePresence == null) {
            throw  new NotConnectedException("Can't initiate connection, remote peer is not available.");
        }

        Bootstrap b = new Bootstrap(); // (1)
        b.group(workerGroup); // (2)
        b.channel(NioSocketChannel.class); // (3)
        b.option(ChannelOption.SO_KEEPALIVE, true); // (4)
        b.handler(new LoggingHandler(LogLevel.TRACE));
        b.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("xmpp-parsero", new XMLFrameDecoder( xmppslConnection, remotePresence) );
                pipeline.addLast("nonzaDecoder", new XmppNonzaDecoder());
                pipeline.addLast("xmppDecodero", new XmppStanzaDecoder());
            }
        });

        // Start the client.
        Channel channel = b.connect(remotePresence.getHost(), remotePresence.getPort()).sync().channel(); // (5)

        addChannel(to, channel);
        
        channel.flush();
        
        return channel;
    }



    
    
    
    public void close() throws IOException {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }


    /**
     * Bind one socket to any port within a given range.
     *
     * @param min the minimum port number allowed
     * @param max hte maximum port number allowed
     * @throws XMPPException if binding failed on all allowed ports.
     */
    private static int bindRange(int min, int max) throws XMPPException {
        // TODO this method exists also for the local socks5 proxy code and should be factored out into a util
        int port = 0;
        for (int try_port = min; try_port <= max; try_port++) {
            try {
                ServerSocket socket = new ServerSocket(try_port);
                socket.close();
                return try_port;
            }
            catch (IOException e) {
                // failed to bind, try next
            }
        }
        throw new DNSException("Unable to bind port, no ports available.");
    }

    /**
     * Registers the service to the mDNS/DNS-SD daemon.
     * Should be implemented by the class extending this, for mDNS/DNS-SD library specific calls.
     */
    protected abstract void registerService() throws XMPPException;

    /**
     * Re-announce the presence information by using the mDNS/DNS-SD daemon.
     */
    protected abstract void reannounceService() throws XMPPException;

    protected void serviceNameChanged(EntityJid newName, EntityJid oldName) {
        // update our own presence with the new name, for future connections
        presence.setServiceName(newName);

        // clean up connections
//        XMPPLLConnection c;
//        c = getConnectionTo(oldName);
//        if (c != null)
//            c.disconnect();
//        c = getConnectionTo(newName);
//        if (c != null)
//            c.disconnect();
//
//        // notify listeners
//        for (LLServiceStateListener listener : stateListeners) {
//            listener.serviceNameChanged(newName, oldName);
//        }
    }

    
    /**
     * Spam stdout with some debug information.
     */
    public void spam() {
        //System.out.println("Number of ingoing connection in map: " + ingoing.size());
        //System.out.println("Number of outgoing connection in map: " + outgoing.size());

        System.out.println("Active chats:");
//        for (LLChat chat : chats.values()) {
//            System.out.println(" * " + chat.getServiceName());
//        }

        System.out.println("Known presences:");
        for (LLPresence presence : presenceDiscoverer.getPresences()) {
            System.out.println(" * " + presence.getServiceName() + "(" + presence.getStatus() + ", " + presence.getHost() + ":" + presence.getPort() + ")");
        }
        Thread.currentThread().getThreadGroup().list();
    }


    /**
     * @param to
     * @return
     * @throws InterruptedException 
     * @throws NotConnectedException 
     * @throws SmackException 
     */
    public Channel getChannel(Jid to) throws InterruptedException, NotConnectedException {
        LOGGER.fine("getChannel:" + to);
        for (Channel channel : channels) {
            if ( to.equals(channel.attr(JID_KEY).get()) )
                return channel;
        }

        return createNewOutgoingChannel(to);
        
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

    /**
     * @param jid
     * @param channel
     */
    public void addChannel(Jid jid, Channel channel) {
        LOGGER.fine("addChannel:" + jid);
        
        channel.attr(SLService.JID_KEY).set(jid);
        channels.add(channel);

        
    }

}
