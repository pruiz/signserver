/*
 * Enhanced TimeStamp Signer, adding support for qcStatements extension.
 * 2019 - pablo.ruiz@gmail.com
 */
package org.signserver.module.tsa;

import java.io.IOException;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.qualified.QCStatement;

import org.signserver.common.*;

public class EnhancedTimeStampSigner extends TimeStampSigner {

	/** Log4j instance for actual implementation class. */
	private static final Logger LOG = Logger.getLogger(TimeStampSigner.class);

	// Property constants
	public static final String INCLUDE_QC_EXTENSION = "INCLUDE_QC_EXTENSION";

	private static final String DEFAULT_WORKERLOGGER = DefaultTimeStampLogger.class.getName();

	private static final ASN1ObjectIdentifier ID_ETSI_TSTS = new ASN1ObjectIdentifier("0.4.0.19422.1.1");

	@Override
	protected Extensions getAdditionalExtensions(final ProcessRequest request,
            RequestContext context)
            throws IOException {
		final ASN1EncodableVector statements = new ASN1EncodableVector();
		final QCStatement statement = new QCStatement(ID_ETSI_TSTS);
		statements.add(statement);

		//final byte[] encoded = value.toASN1Primitive().getEncoded(ASN1Encoding.DER);
		final DEROctetString value = new DEROctetString(new DERSequence(statements));
		Extension extension = new Extension(Extension.qCStatements, false, value);

        return new Extensions(new Extension[] { extension });
    }

}

