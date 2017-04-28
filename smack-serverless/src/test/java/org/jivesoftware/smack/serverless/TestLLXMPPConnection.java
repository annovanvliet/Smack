/**
 * 
 */
package org.jivesoftware.smack.serverless;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.serverless.LLConnectionConfiguration.Builder;
import org.jivesoftware.smack.test.util.SmackTestSuite;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * This test defines use casses for the behaviour af Link Local Connection using Smack.
 * 
 * 
 * @author Anno van Vliet
 *
 */
public class TestLLXMPPConnection extends SmackTestSuite {
    
    private static final Logger log = Logger.getLogger(TestLLXMPPConnection.class.getName());
    
    public static void main(String[] argv) {
        
        SmackConfiguration.DEBUG = true;
        
        TestLLXMPPConnection test = new TestLLXMPPConnection();
        
        if ( test.start() ) {
            
            test.run();
        }
            
    }

    private XMPPLLConnection connection;
    private Roster roster;

    /**
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SmackException 
     * @throws XMPPException 
     * 
     */
    private boolean start() {

        try {
            int rnd = 1; //new Random().nextInt(30);
            
            // Create some kind of user name
            EntityBareJid name = JidCreate.entityBareFrom("smack-mdns@localhost");
            try {   /// System.getenv("USERNAME")
                name = JidCreate.entityBareFrom( "Tester" + rnd + "@" + java.net.InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {}
            

            System.out.println("Link-local presence name set to '" + name + "'");
            // Create a basic presence (only set name, and status to available)
            LLPresence presence = new LLPresence(name);
            System.out.println("Initiating Link-local service...");
            // Create a XMPP Link-local service.
            Builder config = LLConnectionConfiguration.builder().setLocalPresence(presence);

            
            connection = new XMPPLLConnection(config.build());
            
            connection.addAsyncStanzaListener(new StanzaListener() {
                
                @Override
                public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException {
                    log.info("stanzas received:" + packet.getFrom() + " " + packet.toXML());
                    if ( packet instanceof Message ) {
                        Message m = (Message)packet;
                        System.out.println("A Message received from " + m.getFrom() + " - " + m.getBody());
                    }
                    
                }
            }, new StanzaFilter() {
                
                @Override
                public boolean accept(Stanza stanza) {
                    // TODO Auto-generated method stub
                    return true;
                }
            } );

            // Add hook for doing a clean shut down
            Runtime.getRuntime().addShutdownHook(new CloseDownService(connection));

            roster = Roster.getInstanceFor(connection);
            
            // Initiate Link-local message session
            connection.connect().login(name.getLocalpart().toString(), null);

            connection.getDNSService().addPresenceListener(new MDNSListener());
            

            return true;
        }
        catch (XMPPException | SmackException | IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            log.log(Level.WARNING, "Start", e);
        }
        
        return false;
        
    }

    public void run() {
            // Initiate stdin buffer for reading commands (the fancy UI)
            BufferedReader stdIn = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));


            // Implement a user friendly interface.
            String line;
            boolean done = false;

            System.out.println("Welcome to the Smack Link-local sample client interface!");
            System.out.println("========================================================");
            while (!done) {
                try {
                    System.out.print("> ");
                    line = stdIn.readLine();
                    if ("quit".equals(line))
                        done = true;
                    else if ("spam".equals(line)) {
                        
                        spam(connection);
                    }
                    else if ("msg".equals(line)) {
                        System.out.print("Enter user: ");
                        String user = stdIn.readLine();
                        EntityBareJid userjid = JidCreate.entityBareFrom(user);
                        System.out.print("Enter message: ");
                        String message = stdIn.readLine();

                        chat( connection,  userjid , message );
                        
                        System.out.println("Message sent.");
                    }
                    else if ("addfeature".equals(line)) {
                        System.out.print("Enter new feature: ");
                        String feature = stdIn.readLine();
                        
                        ServiceDiscoveryManager.getInstanceFor(connection).addFeature(feature);
                    }
                    else if ("disco".equals(line)) {
                        System.out.print("Enter user service name e.g (dave@service): ");
                        String user = stdIn.readLine();
                        
                        Jid userjid = JidCreate.from(user);
                        
                        DiscoverInfo info = ServiceDiscoveryManager.getInstanceFor(connection).discoverInfo( userjid );
                        System.out.println(" # Discovered: " + info.toXML());
                    }
                    else if ("status".equals(line)) {
                        System.out.print("Enter new status: ");
                        String status = stdIn.readLine();
                        try {
                            
                            Presence pres = new Presence(Type.available,"", 0, Presence.Mode.valueOf(status));
                            connection.sendStanza(pres);
                        }
                        catch (IllegalArgumentException iae) {
                            System.err.println("Illegal status: " + status);
                        }
                    }
                    else if ("disconnect".equals(line)) {
                        System.out.print("disconnect connction");
                        connection.disconnect();
                    }
                    else if ("connect".equals(line)) {
                        System.out.print("connect connction");
                        connection.connect();
                    }
                    else if ("shutdown".equals(line)) {
                        System.out.print("shutdown connction");
                        connection.shutdown();
                    }
                    else if ("connect".equals(line)) {
                        System.out.print("connect connction");
                        connection.connect();
                    }
                }
                catch (XMPPException xe) {
                    System.out.println("Caught XMPPException: " + xe);
                    xe.printStackTrace();
                    //done = true; 
                }
                catch (XmppStringprepException ioe) {
                    System.out.println("Caught XmppStringprepException: " + ioe);
                    ioe.printStackTrace();
                }
                catch (IOException ioe) {
                    System.out.println("Caught IOException: " + ioe);
                    ioe.printStackTrace();
                    done = true;
                }
                catch (SmackException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            System.exit(0);
    }
    
    /**
     * @param connection 
     * @param userjid
     * @param message
     * @throws InterruptedException 
     * @throws NotConnectedException 
     */
    private void chat(XMPPConnection connection, EntityBareJid userjid, String message) throws NotConnectedException, InterruptedException {
        
        Chat chat = ChatManager.getInstanceFor(connection).chatWith(userjid);
        
        chat.send(message);
        
    }

    /**
     * @param connection
     */
    private void spam(XMPPLLConnection connection) {

        connection.spam();
        
    }

    private static class CloseDownService extends Thread {
        AbstractXMPPConnection service;
        
        public CloseDownService(AbstractXMPPConnection service) {
            this.service = service;
        }

        @Override
        public void run () {
            System.out.println("### Unregistering service....");
            //service.makeUnavailable();
            System.out.println("### Done, now closing daemon...");

            try { Thread.sleep(1000); } catch (Exception e) { }
                service.disconnect();
            System.out.println("### Done.");
            try { Thread.sleep(2000); } catch (Exception e) { }
            Thread.currentThread().getThreadGroup().list();
        }
    }



}
