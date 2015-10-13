/**
 *
 * Copyright © 2015 Anno van Vliet
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
package org.jivesoftware.smackx.mam.provider;

import static org.junit.Assert.assertEquals;

import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.mam.packet.MamQueryIQ;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.packet.DataForm.Type;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

/**
 * TODO add a Description.
 *
 * @author Anno van Vliet
 *
 */
public class MamQueryIQProviderTest {

  /**
   * Test method for {@link org.jivesoftware.smackx.mam.provider.MamQueryIQProvider#parse(org.xmlpull.v1.XmlPullParser, int)}.
   * @throws Exception 
   */
  @Test
  public void testParseXmlPullParserInt() throws Exception {
    DataForm form = new DataForm(Type.submit);
    FormField f = new FormField("FORM_TYPE");
    f.setType(FormField.Type.hidden);
    f.addValue("urn:xmpp:mam:0");
    form.addField(f);
    
    MamQueryIQ l = new MamQueryIQ("queryId", form );
    l.setStanzaId("stanzaId");
    l.setNode("NodeId");
    
    
    XmlPullParser parser = PacketParserUtils.getParserFor(l.toXML().toString());
    while (true) {
      if (parser.next() == XmlPullParser.START_TAG && parser.getName().equals(MamQueryIQ.ELEMENT)) {
        break;
      }
    }
    MamQueryIQProvider prov = new MamQueryIQProvider();
    MamQueryIQ out = prov.parse(parser);
    out.setStanzaId(l.getStanzaId());
    assertEquals(l.toXML(), out.toXML());
  }

  @Test
  public void testParseBare() throws Exception {
    
    MamQueryIQ l = new MamQueryIQ("queryId");
    
    
    XmlPullParser parser = PacketParserUtils.getParserFor(l.toXML().toString());
    while (true) {
      if (parser.next() == XmlPullParser.START_TAG && parser.getName().equals(MamQueryIQ.ELEMENT)) {
        break;
      }
    }
    MamQueryIQProvider prov = new MamQueryIQProvider();
    MamQueryIQ out = prov.parse(parser);
    out.setStanzaId(l.getStanzaId());
    assertEquals( l.toXML(), out.toXML());
  }

  
}
