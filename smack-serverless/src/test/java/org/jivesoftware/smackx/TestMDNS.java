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

package org.jivesoftware.smackx;

import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.serverless.LLPresence;
import org.jivesoftware.smack.serverless.LLService;
import org.jivesoftware.smack.serverless.LLServiceDiscoveryManager;
import org.jivesoftware.smack.serverless.service.LLServiceStateListener;
import org.jivesoftware.smack.serverless.service.jmdns.JmDNSService;
import org.jivesoftware.smack.test.util.SmackTestSuite;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

//import javax.jmdns.impl.SocketListener;

public class TestMDNS extends SmackTestSuite {
    LLService service;
    public static void main(String[] argv) {
        SmackConfiguration.DEBUG = true;
        Handler ch = new ConsoleHandler();
        ch.setLevel(Level.FINEST);
        //System.out.println(ConsoleHandler.class.getName());
        //Logger.global.addHandler(ch);
        Logger.getLogger("").addHandler(ch);
        //Logger.getLogger("").setLevel(Level.FINEST);
        //Logger.global.setLevel(Level.FINEST);
        //Logger.getLogger("javax.jmdns.impl.JmDNSImpl").addHandler(ch);
        //Logger.getLogger("javax.jmdns.impl.SocketListener").setLevel(Level.FINEST);
        Logger.getLogger("javax.jmdns").log(Level.FINE, "Fine?");
        Logger.getLogger("javax.jmdns").log(Level.WARNING, "Warning?");
        TestMDNS test = new TestMDNS();
        test.run();
    }

    public void run() {
        try {
            // Initiate stdin buffer for reading commands (the fancy UI)
            BufferedReader stdIn = new BufferedReader(
                    new InputStreamReader(System.in));

            int rnd = new Random().nextInt(30);
            
            // Create some kind of user name
            EntityJid name = JidCreate.entityBareFrom("smack-mdns@localhost");
            try {   /// System.getenv("USERNAME")
                name = JidCreate.entityBareFrom( "Tester" + rnd + "@" + java.net.InetAddress.getLocalHost().getHostName());
            } catch (Exception e) {}
            

            System.out.println("Link-local presence name set to '" + name + "'");
            // Create a basic presence (only set name, and status to available)
            LLPresence presence = new LLPresence(name);
            System.out.println("Initiating Link-local service...");
            // Create a XMPP Link-local service.
            service = JmDNSService.create(presence);
            service.addServiceStateListener(new LLServiceStateListener() {
                public void serviceNameChanged(Jid newName, Jid oldName) {
                    System.out.println("Service named changed from " + oldName + " to " + newName + "");
                }

                public void serviceClosed() {
                    System.out.println("Service closed");
                }

                public void serviceClosedOnError(Exception e) {
                    System.out.println("Service closed due to an exception");
                    e.printStackTrace();
                }

                public void unknownOriginMessage(Message m) {
                    System.out.println("This message has unknown origin:");
                    System.out.println(m.toXML());
                }
            });

            // Adding presence listener.
            service.addPresenceListener(new MDNSListener());

            System.out.println("Prepering link-local service discovery...");

            // Note that an LLServiceDiscoveryManager is not created
            // until an actual XMPPLLConnection is created. Instead we now
            // specify default features to LLServiceDiscoveryManager's static methods
            // As we want to play with service discovery, initiate the wrapper
//            LLServiceDiscoveryManager disco = LLServiceDiscoveryManager.getInstanceFor(service);
//
//            if (disco == null) {
//                System.err.println("Failed to initiate Service Discovery Manager.");
//                System.exit(1);
//            }

            System.out.println("Adding three default features to service discovery manager...");
//            LLServiceDiscoveryManager.addDefaultFeature("http://www.jabber.org/extensions/lalal");
//            LLServiceDiscoveryManager.addDefaultFeature("http://www.jabber.org/extenions/thetetteet");
//            LLServiceDiscoveryManager.addDefaultFeature("urn:xmpp:hejhoppextension");

            // Start listen for Link-local chats
            service.addLLChatListener(new MyChatListener());

            // Add hook for doing a clean shut down
            Runtime.getRuntime().addShutdownHook(new CloseDownService(service));

            // Initiate Link-local message session
            service.init();

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
                        service.spam();
                    }
                    else if ("msg".equals(line)) {
                        System.out.print("Enter user: ");
                        String user = stdIn.readLine();
                        Jid userjid = JidCreate.from(user);
                        System.out.print("Enter message: ");
                        String message = stdIn.readLine();
                        
                        Chat chat = service.getChat(userjid);
                        chat.send(message);
                        System.out.println("Message sent.");
                    }
                    else if ("addfeature".equals(line)) {
                        System.out.print("Enter new feature: ");
                        String feature = stdIn.readLine();
                        //disco.addFeature(feature);
//                        LLServiceDiscoveryManager.addDefaultFeature(feature);
                    }
                    else if ("disco".equals(line)) {
                        System.out.print("Enter user service name e.g (dave@service): ");
                        String user = stdIn.readLine();
                        
                        Jid userjid = JidCreate.from(user);
                        
                        DiscoverInfo info = LLServiceDiscoveryManager.getInstanceFor(service).discoverInfo( userjid );
                        System.out.println(" # Discovered: " + info.toXML());
                    }
                    else if ("status".equals(line)) {
                        System.out.print("Enter new status: ");
                        String status = stdIn.readLine();
                        try {
                            presence.setStatus(LLPresence.Mode.valueOf(status));
                            service.updateLocalPresence(presence);
                        }
                        catch (IllegalArgumentException iae) {
                            System.err.println("Illegal status: " + status);
                        }
                    }
                }
                catch (XMPPException xe) {
                    System.out.println("Caught XMPPException: " + xe);
                    xe.printStackTrace();
                    //done = true;
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
            }
            System.exit(0);
        } catch (XMPPException xe) {
            System.err.println(xe);
        }
        catch (XmppStringprepException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    private class CloseDownService extends Thread {
        LLService service;
        
        public CloseDownService(LLService service) {
            this.service = service;
        }

        public void run () {
            System.out.println("### Unregistering service....");
            //service.makeUnavailable();
            System.out.println("### Done, now closing daemon...");

            try { Thread.sleep(1000); } catch (Exception e) { }
            try {
                service.close();
            } catch (IOException e) {
                System.out.println("Error closing service");
                e.printStackTrace();
            }
            System.out.println("### Done.");
            try { Thread.sleep(2000); } catch (Exception e) { }
            Thread.currentThread().getThreadGroup().list();
        }
    }

    private class MyChatListener implements IncomingChatMessageListener {
        public MyChatListener() {}

//        @Override
//        public void chatCreated(Chat chat, boolean createdLocally) {
//            System.out.println("Discovered new chat being created.");
//            chat.addMessageListener(new MyMessageListener(chat));
//        }

        /* (non-Javadoc)
         * @see org.jivesoftware.smack.chat2.IncomingChatMessageListener#newIncomingMessage(org.jxmpp.jid.EntityBareJid, org.jivesoftware.smack.packet.Message, org.jivesoftware.smack.chat2.Chat)
         */
        @Override
        public void newIncomingMessage(EntityBareJid from, Message message, org.jivesoftware.smack.chat2.Chat chat) {
            System.out.println("new Incoming Message: " + message + " from " + from);
          try {
              if (message.getBody().equals("ping")) {
                  chat.send("pong");
                  System.out.println("### received a ping, replied with pong.");
              }
              else if (message.getBody().equals("spam")) {
                  service.spam();
              }
              else {
                  System.out.println("### <" + chat.getXmppAddressOfChatPartner() +
                          "> " + message.getBody());
              }
          }
          catch (SmackException | InterruptedException xe) {
              System.out.println("Caught XMPPException in message listener: " + xe);
              xe.printStackTrace();
          }
       }
    }
//
//    private class MyMessageListener implements ChatMessageListener {
//        Chat chat;
//
//        MyMessageListener(Chat chat) {
//            this.chat = chat;
//        }
//
//        @Override
//        public void processMessage(Chat chat, Message message) {
//            try {
//                if (message.getBody().equals("ping")) {
//                    chat.sendMessage("pong");
//                    System.out.println("### received a ping, replied with pong.");
//                }
//                else if (message.getBody().equals("spam")) {
//                    service.spam();
//                }
//                else {
//                    System.out.println("### <" + chat.getParticipant() +
//                            "> " + message.getBody());
//                }
//            }
//            catch (SmackException | InterruptedException xe) {
//                System.out.println("Caught XMPPException in message listener: " + xe);
//                xe.printStackTrace();
//            }
//        }
//    }
}
