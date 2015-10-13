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

import java.util.Date;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.mam.packet.MamPacket.MamResultExtension;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParser;

/**
 * TODO add a Description.
 *
 * @author Anno van Vliet
 *
 */
public class MamResultProviderTest {

  /**
   * Test method for {@link org.jivesoftware.smackx.mam.provider.MamResultProvider#parse(org.xmlpull.v1.XmlPullParser, int)}.
   * @throws Exception 
   */
  @Test
  public void testParseXmlPullParserInt() throws Exception {
    DelayInformation delay = new DelayInformation(new Date());
    Stanza fwdPacket = new Message();
    Forwarded forwarded = new Forwarded(delay, fwdPacket);
    MamResultExtension l = new MamResultExtension( "resultId", "id", forwarded);
    
    XmlPullParser parser = PacketParserUtils.getParserFor(l.toXML().toString());
    MamResultProvider prov = new MamResultProvider();
    MamResultExtension out = prov.parse(parser);
    assertEquals(l.toXML(), out.toXML());
  }
  

}
