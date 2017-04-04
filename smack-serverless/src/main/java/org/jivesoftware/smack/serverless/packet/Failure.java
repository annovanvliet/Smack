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
package org.jivesoftware.smack.serverless.packet;

import org.jivesoftware.smack.compress.packet.Compress;
import org.jivesoftware.smack.packet.Nonza;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * Compression Failure packet.
 *
 * @author vliet
 *
 */
public class Failure implements Nonza {
    public static final String ELEMENT = "failure";
    public static final String NAMESPACE = Compress.NAMESPACE;
    
    public enum CompressError {
        unsupportedMethod("unsupported-method"),
        setupFailed("setup-failed");
        
        private final String value;

        CompressError(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private final CompressError error;
    
    /**
     * @param error
     * 
     */
    public Failure(CompressError error) {
        this.error = error;
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.smack.packet.Element#toXML()
     */
    @Override
    public CharSequence toXML() {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        xml.emptyElement(error);
        xml.closeElement(this);
        return xml;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }
}
