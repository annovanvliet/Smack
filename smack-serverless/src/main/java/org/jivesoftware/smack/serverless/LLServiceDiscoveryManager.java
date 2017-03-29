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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.serverless.service.LLPresenceListener;
import org.jivesoftware.smack.serverless.service.LLServiceConnectionListener;
import org.jivesoftware.smack.serverless.service.LLServiceStateListener;
import org.jivesoftware.smack.serverless.service.ServiceException;
import org.jivesoftware.smackx.caps.CapsVersionAndHash;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.disco.NodeInformationProvider;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.Jid;

/**
 * LLServiceDiscoveryManager acts as a wrapper around ServiceDiscoveryManager as ServiceDiscoveryManager only creates an
 * interface for requesting service information on existing connections. Simply said it creates new connections when
 * needed, uses already active connections when appropriate and applies values to new connections.
 *
 * @author Jonas Ådahl
 */
@SuppressWarnings("MissingOverride")
@Deprecated
public class LLServiceDiscoveryManager extends ServiceDiscoveryManager {
    private static Map<OldLLService, LLServiceDiscoveryManager> serviceManagers = new ConcurrentHashMap<OldLLService, LLServiceDiscoveryManager>();

    private final OldLLService service;

    /*
     * We'll create a new LLServiceDiscoveryManager each time a new XMPPLLConnection is created. The issue with the
     * above attempt to create a LLServiceDiscoveryManager on each LLService creation is that, by my reading, we must
     * have both an XMPPLLConnection and LLService to construct a meaningful LLServiceDiscoveryManager. If a client
     * would like to specify features to be advertised in advance of an XMPPLLConnection being created, they should
     * register those features with ServiceDiscoveryManager#addDefaultFeature(String) This way we manage advertised
     * features in one spot, not per individual XMPPLLConnection. Perhaps an even better solution would be for each
     * LLService to manage the list of features to be provided to each LLServiceDiscoveryManager whenever an
     * XMPPLLConnection is initiated. Please let me know (dbro@dbro.pro) if you've any thoughts on this matter.
     */
    static {
        // XMPPLLConnection.addLLConnectionListener(new AbstractConnectionListener<XMPPLLConnection>() {
        //
        // @Override
        // public void connected(XMPPLLConnection connection) {
        // addLLServiceDiscoveryManager(getInstanceFor(connection));
        // }
        // });
    }

    protected LLServiceDiscoveryManager(OldLLService llservice, XMPPConnection connection) {
        super(connection);
        this.service = llservice;

        // Add LLService state listener
        service.addServiceStateListener(new LLServiceStateListener() {
            private void removeEntry() {
                removeLLServiceDiscoveryManager(service);
            }

            public void serviceClosed() {
                removeEntry();
            }

            public void serviceClosedOnError(Exception e) {
                removeEntry();
            }

            public void unknownOriginMessage(Message e) {
                // ignore
            }

            public void serviceNameChanged(Jid n, Jid o) {
                // mDNS service names should not change after connections
                // are established, so may remove this logic

                // Remove entries
                EntityCapsManager.removeUserCapsNode(n.toString());
                EntityCapsManager.removeUserCapsNode(o.toString());
                LLPresence np = service.getPresenceByServiceName(n);
                LLPresence op = service.getPresenceByServiceName(o);

                // Add existing values, if any
                if (np != null && np.getNode() != null && np.getVer() != null) {
                    capsManager.addUserCapsNode(n, np.getNode(), np.getVer());
                }
                if (op != null && op.getNode() != null && op.getVer() != null)
                    capsManager.addUserCapsNode(o, op.getNode(), op.getVer());
            }
        });

        // Entity Capabilities
        capsManager = EntityCapsManager.getInstanceFor(connection);
        capsManager.addCapsVerListener(new CapsPresenceRenewer());
        // Provide EntityCaps features, identities & node to own DiscoverInfo
        // capsManager.calculateEntityCapsVersion(getOwnDiscoverInfo(),
        // getIdentityType(),
        // getIdentityName(),
        // extendedInfo);

        capsManager.updateLocalEntityCaps();

        // Add presence listener. The presence listener will gather
        // entity caps data
        service.addPresenceListener(new LLPresenceListener() {
            public void presenceNew(LLPresence presence) {
                if (presence.getHash() != null && presence.getNode() != null && presence.getVer() != null) {
                    // Add presence to caps manager
                    capsManager.addUserCapsNode(presence.getServiceName(), presence.getNode(), presence.getVer());
                }
            }

            public void presenceRemove(LLPresence presence) {

            }
        });

        service.addLLServiceConnectionListener(new ConnectionServiceMaintainer());
    }

    // /**
    // * Add LLServiceDiscoveryManager to the map of existing ones.
    // */
    // private static void addLLServiceDiscoveryManager(LLServiceDiscoveryManager manager) {
    // serviceManagers.put(manager.service, manager);
    // }

    /**
     * Remove LLServiceDiscoveryManager from the map of existing ones.
     */
    private static void removeLLServiceDiscoveryManager(OldLLService service) {
        serviceManagers.remove(service);
    }

    /**
     * Get the LLServiceDiscoveryManager instance for a specific Link-local service.
     *
     * @param service
     * @return instance
     */
    public static LLServiceDiscoveryManager getInstanceFor(OldLLService service) {
        return serviceManagers.get(service);
    }

    public static LLServiceDiscoveryManager getInstanceFor(OldXMPPLLConnection connection) {
        LLServiceDiscoveryManager llsdm = serviceManagers.get(connection.getService());
        if (llsdm == null) {
            llsdm = new LLServiceDiscoveryManager(connection.getService(), connection);
        }
        return llsdm;
    }

    // When would we change the Identity type?
    // use ServiceDiscoveryManager#setIdentity(Identity)
    // /**
    // * Sets the type of client that will be returned when asked for the client identity in a
    // * disco request. The valid types are defined by the category client. Follow this link to learn
    // * the possible types: <a
    // href="http://www.jabber.org/registrar/disco-categories.html#client">Jabber::Registrar</a>.
    // *
    // * @param type the type of client that will be returned when asked for the client identity in a
    // * disco request.
    // */
    // public static void setIdentityType(String type) {
    // ServiceDiscoveryManager.setIdentityType(type);
    // }

    /**
     * Add discover info response data.
     *
     * @param response the discover info response packet
     */
    @Override
    public synchronized void addDiscoverInfoTo(DiscoverInfo response) {
        // Set this client identity
        DiscoverInfo.Identity identity = new DiscoverInfo.Identity("client", getIdentityName(), getIdentityType());
        response.addIdentity(identity);
        // Add the registered features to the response
        // Add Entity Capabilities (XEP-0115) feature node.
        response.addFeature("http://jabber.org/protocol/caps");

        for (String feature : getFeatures()) {
            response.addFeature(feature);
        }
        // if (extendedInfo != null) {
        // response.addExtension(extendedInfo);
        // }
    }

    /**
     * Get a DiscoverInfo for the current entity caps node.
     *
     * @return a DiscoverInfo for the current entity caps node
     */
    public DiscoverInfo getOwnDiscoverInfo() {
        DiscoverInfo di = new DiscoverInfo();
        di.setType(IQ.Type.result);
        di.setNode(capsManager.getLocalNodeVer());

        // Add discover info
        addDiscoverInfoTo(di);

        // for (String feature : features) {
        // di.addFeature(feature);
        // }

        return di;
    }

    /**
     * Returns a new or already established connection to the given service name.
     *
     * @param serviceName remote service to which we wish to be connected to.
     * @throws InterruptedException
     * @throws XMPPException
     * @returns an established connection to the given service name.
     */
    private OldXMPPLLConnection getConnection(Jid serviceName)
                    throws IOException, SmackException, XMPPException, InterruptedException {
        return service.getConnection(serviceName);
    }

    /**
     * Returns a ServiceDiscoveryManager instance for a new or already established connection to the given service name.
     *
     * @param serviceName the name of the service we wish to get the ServiceDiscoveryManager instance for.
     * @throws InterruptedException
     * @throws XMPPException
     * @returns the ServiceDiscoveryManager instance.
     */
    private ServiceDiscoveryManager getInstance(Jid serviceName)
                    throws SmackException, IOException, XMPPException, InterruptedException {
        return ServiceDiscoveryManager.getInstanceFor(getConnection(serviceName));
    }

    /**
     * Registers extended discovery information of this XMPP entity. When this client is queried for its information
     * this data form will be returned as specified by XEP-0128.
     * <p>
     * Since no packet is actually sent to the server it is safe to perform this operation before logging to the server.
     * In fact, you may want to configure the extended info before logging to the server so that the information is
     * already available if it is required upon login.
     *
     * @param info the data form that contains the extend service discovery information.
     */
    @Override
    public synchronized void setExtendedInfo(DataForm info) {
        // extendedInfo = info;

        // set for already active connections
        for (OldXMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).setExtendedInfo(info);

        renewEntityCapsVersion();
    }

    /**
     * Removes the dataform containing extended service discovery information from the information returned by this XMPP
     * entity.
     * <p>
     * Since no packet is actually sent to the server it is safe to perform this operation before logging to the server.
     */
    @Override
    public synchronized void removeExtendedInfo() {
        // extendedInfo = null;

        // remove for already active connections
        for (OldXMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).removeExtendedInfo();

        renewEntityCapsVersion();
    }

    @Override
    public DiscoverInfo discoverInfo(Jid serviceName, String node) throws XMPPException.XMPPErrorException,
                    SmackException.NotConnectedException, SmackException.NoResponseException, InterruptedException {
        // Discover the entity's info
        DiscoverInfo disco = new DiscoverInfo();
        disco.setType(IQ.Type.get);
        disco.setTo(serviceName);
        disco.setNode(node);

        IQ result = null;
        try {
            result = service.getIQResponse(disco);
        }
        catch (XMPPException | IOException | SmackException e) {
            // throw new SmackException.NoResponseException();
        }
        if (result == null) {
            throw new ServiceException("No response from the server.", XMPPError.Condition.remote_server_timeout);
        }
        if (result.getType() == IQ.Type.error) {
            XMPPException.XMPPErrorException.ifHasErrorThenThrow(result);
        }
        if (result instanceof DiscoverInfo) {
            return (DiscoverInfo) result;
        }
        throw new ServiceException("Result was not a disco info reply.");
    }

    @Override
    public DiscoverItems discoverItems(Jid serviceName, String node) throws SmackException.NoResponseException,
                    XMPPException.XMPPErrorException, SmackException.NotConnectedException, InterruptedException {
        // Discover the entity's items
        DiscoverItems disco = new DiscoverItems();
        disco.setType(IQ.Type.get);
        disco.setTo(serviceName);
        disco.setNode(node);

        IQ result = null;
        try {
            result = service.getIQResponse(disco);
        }
        catch (XMPPException | IOException | SmackException e) {
            // throw new SmackException.NoResponseException();
        }
        if (result == null) {
            throw new ServiceException("No response from the server.", XMPPError.Condition.remote_server_timeout);
        }
        if (result.getType() == IQ.Type.error) {
            XMPPException.XMPPErrorException.ifHasErrorThenThrow(result);
        }
        if (result instanceof DiscoverInfo) {
            return (DiscoverItems) result;
        }
        throw new ServiceException("Result was not a disco info reply.");
    }

    @Override
    public void setNodeInformationProvider(String node, NodeInformationProvider listener) {
        super.setNodeInformationProvider(node, listener);

        // set for already active connections
        Collection<OldXMPPLLConnection> connections = service.getConnections();
        for (OldXMPPLLConnection connection : connections)
            ServiceDiscoveryManager.getInstanceFor(connection).setNodeInformationProvider(node, listener);
    }

    /**
     * Removes the NodeInformationProvider responsible for providing information (ie items) related to a given node.
     * This means that no more information will be available for the specified node. In MUC, a node could be
     * 'http://jabber.org/protocol/muc#rooms' which means that the NodeInformationProvider will provide information
     * about the rooms where the user has joined.
     * 
     * @param node the node to remove the associated NodeInformationProvider.
     */
    @Override
    public void removeNodeInformationProvider(String node) {
        super.removeNodeInformationProvider(node);

        // remove from existing connections
        for (OldXMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).removeNodeInformationProvider(node);
    }

    /**
     * Removes the specified feature from the supported features set for all XMPPLL entities.
     * <p>
     * Since no packet is actually sent to the server it is safe to perform this operation before logging to the server.
     *
     * @param feature the feature to remove from the supported features.
     */
    @Override
    public synchronized void removeFeature(String feature) {
        for (OldXMPPLLConnection connection : service.getConnections())
            ServiceDiscoveryManager.getInstanceFor(connection).removeFeature(feature);

        super.removeFeature(feature);
    }

    // /**
    // * Returns true if the specified feature is registered in the ServiceDiscoveryManager.
    // *
    // * @param feature the feature to look for.
    // * @return a boolean indicating if the specified featured is registered or not.
    // */
    // @Override
    // public boolean includesFeature(String feature) {
    // return features.contains(feature);
    // }

    /**
     * Returns true if the server supports publishing of items. A client may wish to publish items to the server so that
     * the server can provide items associated to the client. These items will be returned by the server whenever the
     * server receives a disco request targeted to the bare address of the client (i.e. user@host.com).
     * 
     * @param entityID the address of the XMPP entity.
     * @return true if the server supports publishing of items.
     * @throws InterruptedException
     * @throws NotConnectedException 
     * @throws XMPPErrorException 
     * @throws NoResponseException 
     */
    @Override
    public boolean canPublishItems(Jid entityID) throws InterruptedException, NoResponseException, XMPPErrorException, NotConnectedException {
        DiscoverInfo info = discoverInfo(entityID);
        return ServiceDiscoveryManager.canPublishItems(info);
    }

    /**
     * Publishes new items to a parent entity. The item elements to publish MUST have at least a 'jid' attribute
     * specifying the Entity ID of the item, and an action attribute which specifies the action being taken for that
     * item. Possible action values are: "update" and "remove".
     * 
     * @param entityID the address of the XMPP entity.
     * @param discoverItems the DiscoveryItems to publish.
     * @throws InterruptedException
     * @throws NotConnectedException 
     * @throws XMPPErrorException 
     * @throws NoResponseException 
     */
    @Override
    public void publishItems(Jid entityID, DiscoverItems discoverItems) throws InterruptedException, NoResponseException, XMPPErrorException, NotConnectedException {
        publishItems(entityID, null, discoverItems);
    }

    /**
     * Publishes new items to a parent entity and node. The item elements to publish MUST have at least a 'jid'
     * attribute specifying the Entity ID of the item, and an action attribute which specifies the action being taken
     * for that item. Possible action values are: "update" and "remove".
     * 
     * @param entityID the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @param discoverItems the DiscoveryItems to publish.
     * @throws InterruptedException
     * @throws NotConnectedException 
     * @throws XMPPErrorException 
     * @throws NoResponseException 
     */
    @Override
    public void publishItems(Jid entityID, String node, DiscoverItems discoverItems)
                    throws SmackException.NoResponseException, XMPPException.XMPPErrorException,
                    SmackException.NotConnectedException, InterruptedException {
        try {
            getInstance(entityID).publishItems(entityID, node, discoverItems);
        }
        catch (XMPPException | SmackException | IOException e) {
            e.printStackTrace();
            // An exception specific to the Serverless stack occurred.
            // IOException : We were unable to complete #getConnection in LLService at XMPPLLConnection#connect()
            //
            throw new SmackException.NotConnectedException();
        }
    }

    // private String getEntityCapsVersion() {
    // if (capsManager != null) {
    // return capsManager.getCapsVersion();
    // }
    // else {
    // return null;
    // }
    // }

    /**
     * In case that a connection is unavailable we create a new connection and push the service discovery procedure
     * until the new connection is established.
     */
    private class ConnectionServiceMaintainer implements LLServiceConnectionListener {

        public void connectionCreated(OldXMPPLLConnection connection) {
            // Add service discovery for Link-local connections.\
            ServiceDiscoveryManager manager = ServiceDiscoveryManager.getInstanceFor(connection);

            // Set Entity Capabilities Manager
            manager.setEntityCapsManager(capsManager);

            // // Set extended info
            // manager.setExtendedInfo(extendedInfo);
            //
            // // Set node information providers
            // for (Map.Entry<String,NodeInformationProvider> entry :
            // nodeInformationProviders.entrySet()) {
            // manager.setNodeInformationProvider(entry.getKey(), entry.getValue());
            // }
            //
            // // add features
            // for (String feature : features) {
            // manager.addFeature(feature);
            // }
        }
    }

    private class CapsPresenceRenewer implements EntityCapsManager.CapsVerListener {
        public void capsVerUpdated(CapsVersionAndHash ver) {
            synchronized (service) {
                try {
                    LLPresence presence = service.getLocalPresence();
                    presence.setHash(EntityCapsManager.DEFAULT_HASH);
                    presence.setNode(capsManager.getEntityNode());
                    presence.setVer(ver.version);
                    service.updateLocalPresence(presence);
                }
                catch (XMPPException xe) {
                    // ignore
                }
            }
        }
    }
}
