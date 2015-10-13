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
import org.jivesoftware.smackx.mam.packet.MamPacket.MamFinExtension;
import org.jivesoftware.smackx.rsm.packet.RSMSet;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

/**
 * TODO add a Description.
 *
 * @author Anno van Vliet
 *
 */
public class MamFinProviderTest {

  /**
   * Test method for {@link org.jivesoftware.smackx.mam.provider.MamFinProvider#parse(org.xmlpull.v1.XmlPullParser, int)}.
   * @throws Exception 
   */
  @Test
  public void testParseXmlPullParserInt() throws Exception {
    RSMSet rsmSet = new RSMSet("after", "before", 2, 1, "last", 99, "firstString", 1);
        ;
    
    MamFinExtension l = new MamFinExtension("queryId", rsmSet, false, true);
    
    XmlPullParser parser = PacketParserUtils.getParserFor(l.toXML().toString());
    MamFinProvider prov = new MamFinProvider();
    MamFinExtension out = prov.parse(parser);
    assertEquals(l.toXML(), out.toXML());
    
    assertEquals("firstString",out.getRSMSet().getFirst());
  }

  @Test
  public void testParseBare() throws Exception {
    
    MamFinExtension l = new MamFinExtension("queryId", null, true, true);
    
    XmlPullParser parser = PacketParserUtils.getParserFor(l.toXML().toString());
    MamFinProvider prov = new MamFinProvider();
    MamFinExtension out = prov.parse(parser);
    assertEquals(l.toXML(), out.toXML());
  }

  
}
