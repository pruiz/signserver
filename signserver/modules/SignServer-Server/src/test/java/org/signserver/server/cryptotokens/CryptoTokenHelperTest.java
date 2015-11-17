/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.server.cryptotokens;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import junit.framework.TestCase;

/**
 * Tests that the hard token properties are set correctly for PKCS11 crypto tokens.
 *
 * @version $Id$
 */
public class CryptoTokenHelperTest extends TestCase {
    /**
     * Tests some slot properties, including ATTRIBUTES.
     * @throws Exception
     */
    public final void testSlotProperties1() throws Exception {
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOT", "1");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        prop.put("ATTRIBUTES", "my attributes");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{ATTRIBUTES=my attributes, DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOT=1, SLOTLABELTYPE=SLOT_NUMBER, SLOTLABELVALUE=1, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slot=1, slotLabelType=SLOT_NUMBER, slotLabelValue=1}", p.toString());
    }

    /**
     * Tests some slot properties, including ATTRIBUTESFILE.
     * @throws Exception
     */
    public final void testSlotProperties2() throws Exception {
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOT", "1");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        prop.put("ATTRIBUTESFILE", "/opt/attributes.cfg");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{ATTRIBUTESFILE=/opt/attributes.cfg, DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOT=1, SLOTLABELTYPE=SLOT_NUMBER, SLOTLABELVALUE=1, attributesFile=/opt/attributes.cfg, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slot=1, slotLabelType=SLOT_NUMBER, slotLabelValue=1}", p.toString());
    }

    public final void testSlotIndexProperties() throws Exception {
        // When using nCipher we have to use slotListIndex instead of slot property
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOTLISTINDEX", "1");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOTLABELTYPE=SLOT_INDEX, SLOTLABELVALUE=1, SLOTLISTINDEX=1, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slotLabelType=SLOT_INDEX, slotLabelValue=1, slotListIndex=1}", p.toString());
    }

    /**
     * Tests some slot properties, including SLOTLISTTYPE and SLITLISTVALUE.
     * @throws Exception
     */
    public final void testSlotListTypePropertiesNumber() throws Exception {
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOTLABELTYPE", "SLOT_NUMBER");
        prop.put("SLOTLABELVALUE", "1");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        prop.put("ATTRIBUTESFILE", "/opt/attributes.cfg");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{ATTRIBUTESFILE=/opt/attributes.cfg, DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOTLABELTYPE=SLOT_NUMBER, SLOTLABELVALUE=1, attributesFile=/opt/attributes.cfg, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slotLabelType=SLOT_NUMBER, slotLabelValue=1}", p.toString());
    }

    /**
     * Tests some slot properties, including SLOTLISTTYPE and SLITLISTVALUE.
     * @throws Exception
     */
    public final void testSlotListTypePropertiesIndex() throws Exception {
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOTLABELTYPE", "SLOT_INDEX");
        prop.put("SLOTLABELVALUE", "1");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        prop.put("ATTRIBUTESFILE", "/opt/attributes.cfg");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{ATTRIBUTESFILE=/opt/attributes.cfg, DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOTLABELTYPE=SLOT_INDEX, SLOTLABELVALUE=1, attributesFile=/opt/attributes.cfg, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slotLabelType=SLOT_INDEX, slotLabelValue=1}", p.toString());
    }

    /**
     * Tests some slot properties, including SLOTLISTTYPE and SLITLISTVALUE.
     * @throws Exception
     */
    public final void testSlotListTypePropertiesLabel() throws Exception {
        Properties prop = new Properties();
        prop.put("SHAREDLIBRARY", "/opt/nfast/toolkits/pkcs11/libcknfast.so");
        prop.put("SLOTLABELTYPE", "SLOT_LABEL");
        prop.put("SLOTLABELVALUE", "MyLabel");
        prop.put("DEFAULTKEY", "default");
        prop.put("PIN", "1234");
        prop.put("ATTRIBUTESFILE", "/opt/attributes.cfg");
        SortedMap p = new TreeMap(CryptoTokenHelper.fixP11Properties(prop));
        assertEquals("{ATTRIBUTESFILE=/opt/attributes.cfg, DEFAULTKEY=default, PIN=1234, SHAREDLIBRARY=/opt/nfast/toolkits/pkcs11/libcknfast.so, SLOTLABELTYPE=SLOT_LABEL, SLOTLABELVALUE=MyLabel, attributesFile=/opt/attributes.cfg, defaultKey=default, pin=1234, sharedLibrary=/opt/nfast/toolkits/pkcs11/libcknfast.so, slotLabelType=SLOT_LABEL, slotLabelValue=MyLabel}", p.toString());
    }
    
    /**
     * Test an RSA keyspec with a public exponent expressed in decimal format.
     * 
     * @throws Exception 
     */
    public final void testRSAAlgorithmSpecWithDecimalExponent() throws Exception {
        final RSAKeyGenParameterSpec spec =
                (RSAKeyGenParameterSpec)
                CryptoTokenHelper.getPublicExponentParamSpecForRSA("2048 exp 65537");
        
        assertEquals("Key length", 2048, spec.getKeysize());
        assertEquals("Public exponent",
                     new BigInteger("65537"), spec.getPublicExponent());
    }
    
    /**
     * Test an RSA keyspec with a public exponent expressed in hexadecimal format.
     * 
     * @throws Exception 
     */
    public final void testRSAAlgorithmSpecWithHexExponent() throws Exception {
        final RSAKeyGenParameterSpec spec =
                (RSAKeyGenParameterSpec)
                CryptoTokenHelper.getPublicExponentParamSpecForRSA("2048 exp 0x10001");
        
        assertEquals("Key length", 2048, spec.getKeysize());
        assertEquals("Public exponent",
                     new BigInteger("65537"), spec.getPublicExponent());
    }
    
    /**
     * Test that using a mis-spelled exponent separator results in the correct
     * exception.
     * 
     * @throws Exception 
     */
    public final void testRSAAlgorithmSpecWithInvalidSeparator() throws Exception {
        try {
            CryptoTokenHelper.getPublicExponentParamSpecForRSA("2048 exr 65537");
            fail("Should throw an InvalidAlgorithmParameterException");
        } catch (InvalidAlgorithmParameterException ex) {
            // expected
        } catch (Exception ex) {
            fail("Unexpected exception: " + ex.getClass().getName());
        }
    }
    
    /**
     * Test that specifying the keyspec without spaces around "exp" also works.
     * 
     * @throws Exception 
     */
    public final void testRSAAlgorithmSpecWithoutSpaces() throws Exception {
        final RSAKeyGenParameterSpec spec =
                (RSAKeyGenParameterSpec)
                CryptoTokenHelper.getPublicExponentParamSpecForRSA("2048exp65537");
        
        assertEquals("Key length", 2048, spec.getKeysize());
        assertEquals("Public exponent",
                     new BigInteger("65537"), spec.getPublicExponent());
    }

    // TODO: Tests for dummy certificates temporarly moved to
    // SignServer-Test-System but can be moved back after upgrading to
    // BC >= 1.50

}
