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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.roster.packet.RosterPacket.ItemType;
import org.jxmpp.jid.BareJid;
import org.xbill.DNS.Name;

/**
 * Class for describing a Link-local presence information according to XEP-0174.
 * XEP-0174 describes how to represent XMPP presences using mDNS/DNS-SD.
 * The presence information is stored as TXT fields; example from the documentation
 * follows:
 * <pre>
 *        juliet IN TXT "txtvers=1"
 *        juliet IN TXT "1st=Juliet"
 *        juliet IN TXT "email=juliet@capulet.lit"
 *        juliet IN TXT "hash=sha-1"
 *        juliet IN TXT "jid=juliet@capulet.lit"
 *        juliet IN TXT "last=Capulet"
 *        juliet IN TXT "msg=Hanging out downtown"
 *        juliet IN TXT "nick=JuliC"
 *        juliet IN TXT "node=http://www.adiumx.com"
 *        juliet IN TXT "phsh=a3839614e1a382bcfebbcf20464f519e81770813"
 *        juliet IN TXT "port.p2pj=5562"
 *        juliet IN TXT "status=avail"
 *        juliet IN TXT "vc=CA!"
 *        juliet IN TXT "ver=66/0NaeaBKkwk85efJTGmU47vXI="
 * </pre>
 */
public class LLPresence {
    private static final String LOCAL_DOMAIN = "local.";
    // Service info, gathered from the TXT fields
    private String firstName, lastName, email, msg, nick, jid;
    // caps version
    private String hash, ver, node;

    /**
     * XEP-0174 specifies that if status is not specified it is equal to "avail".
     */
    private Mode status = Mode.avail;

    /**
     *  The unknown
     */
    private final Map<String,String> rest = new HashMap<>();

    public static enum Mode {
        avail,
        away,
        dnd
    }

    // Host details
    private int port;
    private final Name host;
    private final String domain;
    private BareJid serviceName;
    private List<String> groups = new ArrayList<>();
    private final InetAddress[] addresses;

    public LLPresence(BareJid serviceName) {
        this(serviceName, LOCAL_DOMAIN, null, null, 0,Collections.<String, String> emptyMap());
    }
//
//    public LLPresence(BareJid serviceName, Name host, int port) {
//        this(serviceName, )
//    }

    public LLPresence(BareJid serviceName, String domain, InetAddress[] addresses, Name host, int port,
            Map<String,String> records) {
        this.serviceName = serviceName;
        this.host = host;
        this.port = port;
        this.domain = domain;
        this.addresses = addresses;

        // Parse the map (originating from the TXT fields) and put them
        // in variables
        for (Map.Entry<String, String> entry : records.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
            case "1st":
                setFirstName(value);
                break;
            case "last":
                setLastName(value);
                break;
            case "email":
                setEMail(value);
                break;
            case "jid":
                setJID(value);
                break;
            case "nick":
                setNick(value);
                break;
            case "hash":
                setHash(value);
                break;
            case "node":
                setNode(value);
                break;
            case "ver":
                setVer(value);
                break;
            case "status":
                setStatus(Mode.valueOf(value));
                break;
            case "msg":
                setMsg(value);
                break;
            default:
                rest.put(key, value);
            }
        }
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(rest.size() + 20);
        map.put("txtvers", "1");
        map.put("1st", firstName);
        map.put("last", lastName);
        map.put("email", email);
        if (jid != null) map.put("jid", jid);
        if (nick != null) map.put("nick", nick);
        map.put("status", status.toString());
        if (msg != null) map.put("msg", msg);
        map.put("hash", hash);
        map.put("node", node);
        map.put("ver", ver);
        map.put("port.p2ppj", Integer.toString(port));

        map.putAll(rest);

        return map;
    }

    /**
     * Update all the values of the presence.
     */
    void update(LLPresence p) {
        setFirstName(p.getFirstName());
        setLastName(p.getLastName());
        setEMail(p.getEMail());
        setMsg(p.getMsg());
        setNick(p.getNick());
        setStatus(p.getStatus());
        setJID(p.getJID());
    }

    public void setServiceName(BareJid serviceName) {
        this.serviceName = serviceName;
    }

    /**
     * @return
     */
    public String getDomain() {
        return domain;
    }


    
    public void setFirstName(String name) {
        firstName = name;
    }

    public void setLastName(String name) {
        lastName = name;
    }

    public void setEMail(String email) {
        this.email = email;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setStatus(Mode status) {
        this.status = status;
    }

    public void setJID(String jid) {
        this.jid = jid;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    void setPort(int port) {
        this.port = port;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /**
     * @return a display name
     */
    public String getName() {
      StringBuilder sb = new StringBuilder();
      if ( firstName != null )
         sb.append(firstName);
      if ( sb.length() > 0 )
        sb.append(" ");
      if ( lastName != null )
        sb.append(lastName);
      if ( sb.length() == 0 )
        if ( jid != null )
          sb.append(jid);
      if ( sb.length() == 0 )
        if ( serviceName != null )
          sb.append(serviceName);
      
      return sb.toString();
    }

    
    public String getEMail() {
        return email;
    }

    public String getMsg() {
        return msg;
    }

    public String getNick() {
        return nick;
    }

    public Mode getStatus() {
        return status;
    }

    public String getJID() {
        return jid;
    }

    public BareJid getServiceName() {
        return serviceName;
    }

    public Name getHost() {
        return host;
    }

//    /**
//     * @param string
//     */
//    public void addHosts(Collection<String> hosts) {
//        host.addAll(hosts);
//        
//    }
//

    
    public String getHash() {
        return hash;
    }

    public String getNode() {
        return node;
    }

    public String getVer() {
        return ver;
    }

    public String getNodeVer() {
        return node + "#" + hash;
    }

    public int getPort() {
        return port;
    }

    public String getValue(String key) {
        return rest.get(key);
    }

    public void putValue(String key, String value) {
        rest.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LLPresence) {
            LLPresence p = (LLPresence)o;
            return p.serviceName.equals(serviceName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return serviceName.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("LLPresence: user %1$s - %2$s (%3$s, %4$s:%5$d)", getServiceName(), getName() , getStatus(), getHost(), getPort());
    }
    
    /**
     * @return
     */
    public Presence getPresenceStanza() {
        
        Presence stanza = new Presence(Type.available, getMsg(), 0, convertStatus( getStatus()));
        
        stanza.setFrom(serviceName);
        return stanza;
    }

    /**
     * @return
     */
    public RosterPacket getRosterPacket() {
        
        RosterPacket rosterPacket = new RosterPacket();
        rosterPacket.setType(IQ.Type.set);
        rosterPacket.addRosterItem(getRosterPacketItem());
        return rosterPacket;
    }

    /**
     * @return
     */
    public RosterPacket.Item getRosterPacketItem() {
        
        RosterPacket.Item item = new RosterPacket.Item(serviceName, getName());
        if (groups != null) {
            for (String group : groups) {
                if (group != null && group.trim().length() > 0) {
                    item.addGroupName(group);
                }
            }
        }
        item.setItemType(ItemType.both);
        return item;
    }

    /**
     * @param statusMode
     * @return
     */
    public static org.jivesoftware.smack.packet.Presence.Mode convertStatus(Mode statusMode) {
        
        switch (statusMode) {
        case avail:
            return org.jivesoftware.smack.packet.Presence.Mode.available;
        case away:
            return org.jivesoftware.smack.packet.Presence.Mode.away;
        case dnd:
            return org.jivesoftware.smack.packet.Presence.Mode.dnd;
        default:
            break;
        }
        return org.jivesoftware.smack.packet.Presence.Mode.available;
    }

    /**
     * @param statusMode
     * @return
     */
    public static Mode convertStatus(org.jivesoftware.smack.packet.Presence.Mode statusMode) {
        
        switch (statusMode) {
        case available:
            return Mode.avail;
        case away:
            return Mode.away;
        case dnd:
            return Mode.dnd;
        default:
            break;
        }
        return Mode.avail;
    }


}
