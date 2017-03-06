/**
 * 
 */
package org.jivesoftware.smack.serverless.packet;

import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * @author anno
 *
 */
public class StreamFeatures implements Nonza {

    public static final String ELEMENT = "stream:features";
    
    private List<ExtensionElement> features = new ArrayList<>();

    public StreamFeatures() {
    }
    
    @Override
    public String getNamespace() {
        return "http://etherx.jabber.org/streams";
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }
    
    public void addFeature( ExtensionElement feature ) {
        features.add(feature);
    }

    @Override
    public XmlStringBuilder toXML() {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        for (ExtensionElement feature : features) {
            xml.append(feature.toXML());
        }
        xml.closeElement(this);
        return xml;
    }

}
