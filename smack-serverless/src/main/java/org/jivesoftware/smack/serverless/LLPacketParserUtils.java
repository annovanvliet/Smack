/*******************************************************************************
 *
 *      ___------------
 *    --  __-----------     NATO
 *    | --  __---------                  NCI AGENCY
 *    | | --          /\####    ###      P.O. box 174
 *    | | |          / #\   #    #       2501 CD The Hague
 *    | | |         <  # >       # 
 *     \ \ \         \ #/   #    # 
 *      \ \ \         \/####    ###
 *       \ \ \---------
 *        \ \---------     AGENCY        Project: JChat
 *         ----------     The Hague
 *
 * //******************************************************************************
 *
 *  * Copyright 2017 NCI Agency, Inc. All Rights Reserved.
 *  *
 *  * This software is proprietary to the NCI Agency and cannot be copied 
 *  * neither distributed to other parties.
 *  * Any other use of this software is subject to license terms, which
 *  * should be specified in a separate document, signed by NCIA and the
 *  * other party.
 *  *
 *  * This file is NATO UNCLASSIFIED
 *******************************************************************************/
package org.jivesoftware.smack.serverless;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jivesoftware.smack.compress.packet.Compress;
import org.jivesoftware.smack.packet.Bind;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Mechanisms;
import org.jivesoftware.smack.packet.Session;
import org.jivesoftware.smack.packet.StartTls;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Extensions on the {@link PacketParserUtils}.
 *
 * @author vliet
 *
 */
public class LLPacketParserUtils extends PacketParserUtils {
    private static Logger logger = Logger.getLogger(LLPacketParserUtils.class.getName());

    /**
     * @param parser
     * @return
     * @throws Exception 
     */
    public static List<ExtensionElement> parseFeatures(XmlPullParser parser) throws Exception {
        logger.finest("parseFeatures");
        final int initialDepth = parser.getDepth();
        List<ExtensionElement> streamFeatures = new ArrayList<>();
        while (true) {
            int eventType = parser.next();

            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == initialDepth + 1) {
                ExtensionElement streamFeature = null;
                String name = parser.getName();
                String namespace = parser.getNamespace();
                switch (name) {
                case StartTls.ELEMENT:
                    streamFeature = PacketParserUtils.parseStartTlsFeature(parser);
                    break;
                case Mechanisms.ELEMENT:
                    streamFeature = new Mechanisms(PacketParserUtils.parseMechanisms(parser));
                    break;
                case Bind.ELEMENT:
                    streamFeature = Bind.Feature.INSTANCE;
                    break;
                case Session.ELEMENT:
                    streamFeature = PacketParserUtils.parseSessionFeature(parser);
                    break;
                case Compress.Feature.ELEMENT:
                    streamFeature = PacketParserUtils.parseCompressionFeature(parser);
                    break;
                default:
                    ExtensionElementProvider<ExtensionElement> provider = ProviderManager.getStreamFeatureProvider(name, namespace);
                    if (provider != null) {
                        streamFeature = provider.parse(parser);
                    }
                    break;
                }
                if (streamFeature != null) {
                    streamFeatures.add(streamFeature);
                }
            }
            else if (eventType == XmlPullParser.END_TAG && parser.getDepth() == initialDepth) {
                break;
            }
        }
        return streamFeatures;
    }
    
    /**
     * Parse the Compression Feature reported from the server.
     *
     * @param parser the XML parser, positioned at the start of the compression stanza.
     * @return The CompressionFeature stream element
     * @throws XmlPullParserException if an exception occurs while parsing the stanza.
     * @throws IOException
     */
    public static Compress parseCompress(XmlPullParser parser)
                    throws IOException, XmlPullParserException {
        assert (parser.getEventType() == XmlPullParser.START_TAG);
        String name;
        final int initialDepth = parser.getDepth();
        String method = null;
        outerloop: while (true) {
            int eventType = parser.next();
            switch (eventType) {
            case XmlPullParser.START_TAG:
                name = parser.getName();
                switch (name) {
                case "method":
                    method = parser.nextText();
                    break;
                }
                break;
            case XmlPullParser.END_TAG:
                name = parser.getName();
                switch (name) {
                case Compress.ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                }
            }
        }
        assert (parser.getEventType() == XmlPullParser.END_TAG);
        assert (parser.getDepth() == initialDepth);
        return new Compress(method);
    }

}
