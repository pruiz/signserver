# PDFSigner SHA-256 / Detached Signature Upgrade Plan

## Goal

Upgrade the PDF signing path in this SignServer 3.4.1-based worktree so PDF signatures can use SHA-256 with RSA keys, including timestamp requests from a TSA that rejects or deprecates SHA-1.

The practical goal is compatibility with a TSA using SHA-256 and larger RSA keys, while preserving the local PDFSigner additions in this repository.

## Executive Summary

The local `PDFSigner` should not be replaced wholesale with the SignServer 5.2.0 implementation. The 5.2.0 code is useful as a reference for digest configurability, but it depends on newer SignServer APIs and omits or changes local behavior in this worktree.

The local signer already sets the PDF signature subfilter to `adbe.pkcs7.detached`:

`signserver/modules/SignServer-Module-PDFSigner/src/java/org/signserver/module/pdfsigner/PDFSigner.java:561-562`

The current blocker is not the PDF subfilter in this checkout. The blocker is hardcoded SHA-1 in the CMS signature generation and hardcoded SHA-1 in timestamp request generation:

`PDFSigner.java:598` creates `PdfPKCS7(..., "SHA1", ...)`.

`PDFSigner.java:609` creates `MessageDigest.getInstance("SHA1")`.

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/PdfPKCS7.java:1240` computes the TSA imprint with SHA-1.

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/TSAClientBouncyCastle.java:149` sends timestamp requests with `X509ObjectIdentifiers.id_SHA1`.

The recommended change is a focused backport of digest configurability and TSA SHA-256 support, while preserving the existing local PDFSigner implementation.

## Current State

### Local PDFSigner Location

Main signer:

`signserver/modules/SignServer-Module-PDFSigner/src/java/org/signserver/module/pdfsigner/PDFSigner.java`

Parameters:

`signserver/modules/SignServer-Module-PDFSigner/src/java/org/signserver/module/pdfsigner/PDFSignerParameters.java`

Tests:

`signserver/modules/SignServer-Module-PDFSigner/test/org/signserver/module/pdfsigner/PDFSignerUnitTest.java`

`signserver/modules/SignServer-Test-System/test/org/signserver/module/pdfsigner/PDFSignerTest.java`

iText fork used by PDFSigner:

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/PdfPKCS7.java`

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/TSAClientBouncyCastle.java`

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/TSAClient.java`

### Local PDF Subfilter

The local signer already uses:

```java
PdfSignature dic = new PdfSignature(PdfName.ADOBE_PPKLITE, new PdfName(
        "adbe.pkcs7.detached"));
```

This is already the desired PDF signature subfilter.

### Local CMS Digest

The local signer still uses SHA-1 for the CMS signature:

```java
sgn = new PdfPKCS7(privKey, certChain, crlList, "SHA1", null, false);
messageDigest = MessageDigest.getInstance("SHA1");
```

This should become configurable and should support SHA-256.

### Local TSA Digest

Timestamping is SHA-1-only in two places.

In `PdfPKCS7.getEncodedPKCS7(...)`, the timestamp imprint is always calculated as SHA-1 over the CMS signature value:

```java
byte[] tsImprint = MessageDigest.getInstance("SHA-1").digest(digest);
byte[] tsToken = tsaClient.getTimeStampToken(this, tsImprint);
```

In `TSAClientBouncyCastle`, the request declares SHA-1:

```java
TimeStampRequest request = tsqGenerator.generate(X509ObjectIdentifiers.id_SHA1, imprint, nonce);
```

For a SHA-256-only TSA, both the imprint bytes and the request algorithm OID must be SHA-256.

## SignServer 5.2.0 Reference Findings

The 5.2.0 PDFSigner from:

`https://github.com/pruiz/signserver/blob/9708eaa3b9de3416a906e46e0e0f90f5ac08922b/signserver/modules/SignServer-Module-PDFSigner/src/main/java/org/signserver/module/pdfsigner/PDFSigner.java`

uses `adbe.pkcs7.detached`, supports configurable `DIGESTALGORITHM`, and has a separate `TSA_DIGESTALGORITHM` concept.

Useful ideas to backport:

- `DIGESTALGORITHM` property.
- Default digest algorithm of `SHA256`.
- `TSA_DIGESTALGORITHM` property.
- DSA safety check requiring SHA-1 for DSA keys.
- Passing the digest algorithm to `PdfPKCS7` and `MessageDigest` instead of hardcoding SHA-1.
- Passing a TSA digest algorithm through the signing stack.

Parts not suitable for direct replacement:

- New `SignatureRequest` / `SignatureResponse` APIs.
- `ICryptoTokenV4` / `ICryptoInstance` APIs.
- New service lookup APIs.
- File-backed request/response data abstractions.
- New configuration error accumulation model.
- `InternalTSAClient` and `TSA_WORKER` support unless explicitly desired later.

## Local Features To Preserve

The local PDFSigner contains behavior that must be retained unless explicitly removed:

- Existing-field visible signature support through the `FIELD` property.
- Visible signature image support using base64 or file path.
- Visible signature page and rectangle configuration.
- Certification level handling.
- `EMBED_CRL` support.
- `EMBED_OCSP_RESPONSE` support.
- Password-protected PDF support using request metadata `pdfPassword`.
- Duplicate indirect object mitigation with `REFUSE_DOUBLE_INDIRECT_OBJECTS`.
- Permission rejection with `REJECT_PERMISSIONS`.
- Permission rewriting with `SET_PERMISSIONS`.
- Permission removal with `REMOVE_PERMISSIONS`.
- Owner password setting with `SET_OWNERPASSWORD`.
- Archive-to-disk support.
- Current second-signing retry behavior with 15% extra container size padding.
- Current old SignServer request/response APIs.

## Design Decisions

### Keep The Local PDFSigner As The Base

Use the current local `PDFSigner.java` as the implementation base. Do not copy the 5.2.0 file over it.

Reason:

The local file already has the desired subfilter and carries local features. Replacing it would require a much larger migration across SignServer APIs and risks dropping local behavior.

### Add `DIGESTALGORITHM`

Add a worker property:

`DIGESTALGORITHM`

Recommended default:

`SHA256`

Rationale:

SignServer 5.2.0 defaults to SHA-256. The current business need is SHA-256 TSA and RSA 3k support. Keeping SHA-1 as default would preserve historical behavior but would make the upgrade less effective unless every PDFSigner worker is manually configured.

Compatibility note:

If strict behavior compatibility is required, default `DIGESTALGORITHM` to `SHA1` and set `WORKER...DIGESTALGORITHM=SHA256` in production worker config. This is safer for old deployments but less aligned with the upgrade goal.

### Add `TSA_DIGESTALGORITHM`

Add a worker property:

`TSA_DIGESTALGORITHM`

Recommended default:

`SHA256`

Rationale:

The TSA is specifically upgrading to SHA-256. The timestamp request digest algorithm must be configurable independently from the PDF CMS digest.

### Keep `adbe.pkcs7.detached` Hardcoded

Do not introduce a `SUBFILTER` property unless there is a concrete need.

Reason:

The desired behavior is clear, and this worktree already uses `adbe.pkcs7.detached`. Configurability here would increase the chance of accidental reversion to SHA-1-only subfilter behavior.

### Avoid Broad iText Upgrade

Do not upgrade the entire iText fork as part of this change.

Reason:

The bundled fork already has SHA-256 OIDs in `PdfPKCS7`. A narrow patch is lower-risk than replacing iText in a legacy SignServer 3.4.1 codebase.

## Implementation Steps

### Step 1: Add Digest Constants To PDFSigner

File:

`signserver/modules/SignServer-Module-PDFSigner/src/java/org/signserver/module/pdfsigner/PDFSigner.java`

Add constants near existing configuration property constants:

```java
public static final String DIGESTALGORITHM = "DIGESTALGORITHM";
private static final String DEFAULT_DIGESTALGORITHM = "SHA256";
public static final String TSA_DIGESTALGORITHM = "TSA_DIGESTALGORITHM";
private static final String DEFAULT_TSA_DIGESTALGORITHM = "SHA256";
```

Use `private static final` for default values unless tests or docs need public access.

### Step 2: Add Instance Fields For Digest Configuration

File:

`PDFSigner.java`

Add fields:

```java
private String digestAlgorithm = DEFAULT_DIGESTALGORITHM;
private String tsaDigestAlgorithm = DEFAULT_TSA_DIGESTALGORITHM;
```

Keep these on `PDFSigner`, not `PDFSignerParameters`, unless the existing parameter class is already the preferred place for all signer properties. Either location can work, but keeping them on `PDFSigner` matches the 5.2.0 reference and keeps cryptographic concerns close to signing logic.

### Step 3: Read And Validate Digest Properties In `init`

File:

`PDFSigner.java`

In `init(...)`, after existing archive-to-disk initialization and before compiling `archivetodiskPattern` or after it, read:

```java
final String configuredDigest = config.getProperty(DIGESTALGORITHM);
if (configuredDigest != null) {
    digestAlgorithm = configuredDigest.trim();
}

final String configuredTsaDigest = config.getProperty(TSA_DIGESTALGORITHM);
if (configuredTsaDigest != null) {
    tsaDigestAlgorithm = configuredTsaDigest.trim();
}
```

Validation should reject unsupported values. Suggested minimal validation helper:

```java
private static boolean isSupportedDigestAlgorithm(final String algorithm) {
    return "SHA1".equalsIgnoreCase(algorithm)
            || "SHA-1".equalsIgnoreCase(algorithm)
            || "SHA224".equalsIgnoreCase(algorithm)
            || "SHA-224".equalsIgnoreCase(algorithm)
            || "SHA256".equalsIgnoreCase(algorithm)
            || "SHA-256".equalsIgnoreCase(algorithm)
            || "SHA384".equalsIgnoreCase(algorithm)
            || "SHA-384".equalsIgnoreCase(algorithm)
            || "SHA512".equalsIgnoreCase(algorithm)
            || "SHA-512".equalsIgnoreCase(algorithm);
}
```

Because this SignServer version does not have the newer `configErrors` flow, unsupported algorithms can be handled in one of two ways:

- Throw `IllegalArgumentException` or `RuntimeException` from `init`, if existing code expects init to fail fast.
- Validate in `processData` and throw `SignServerException` before signing.

Recommended approach:

Validate in `processData` using a private method and throw `SignServerException`. This avoids changing the method signature of `init` and is safer in legacy lifecycle code.

Add private method:

```java
private void checkDigestAlgorithms() throws SignServerException {
    if (!isSupportedDigestAlgorithm(digestAlgorithm)) {
        throw new SignServerException("Illegal digest algorithm: " + digestAlgorithm);
    }
    if (!isSupportedDigestAlgorithm(tsaDigestAlgorithm)) {
        throw new SignServerException("Illegal timestamping digest algorithm: " + tsaDigestAlgorithm);
    }
}
```

Call it near the start of `processData(...)`, after request validation and before signing.

### Step 4: Add DSA Safety Check

File:

`PDFSigner.java`

Import:

```java
import java.security.interfaces.DSAPrivateKey;
```

In `addSignatureToPDFDocument(...)`, after retrieving `PrivateKey privKey`, add:

```java
if (privKey instanceof DSAPrivateKey && !isSha1Digest(digestAlgorithm)) {
    throw new IllegalRequestException("Only SHA1 is permitted as digest algorithm for DSA private keys");
}
```

Add helper:

```java
private static boolean isSha1Digest(final String algorithm) {
    return "SHA1".equalsIgnoreCase(algorithm) || "SHA-1".equalsIgnoreCase(algorithm);
}
```

Reason:

The 5.2.0 code includes the same restriction. The primary target here is RSA, so this does not block the required use case.

### Step 5: Replace Hardcoded PDF CMS SHA-1

File:

`PDFSigner.java`

Change:

```java
sgn = new PdfPKCS7(privKey, certChain, crlList, "SHA1", null, false);
```

to:

```java
sgn = new PdfPKCS7(privKey, certChain, crlList, digestAlgorithm, null, false);
```

Change:

```java
messageDigest = MessageDigest.getInstance("SHA1");
```

to:

```java
messageDigest = MessageDigest.getInstance(digestAlgorithm);
```

Change the exception text from:

```java
"Error creating SHA1 digest"
```

to:

```java
"Error creating " + digestAlgorithm + " digest"
```

### Step 6: Pass TSA Digest To Signature Encoding

File:

`PDFSigner.java`

Current method:

```java
protected byte[] calculateSignature(PdfPKCS7 sgn, int size, MessageDigest messageDigest,
        Calendar cal, PDFSignerParameters params, Certificate[] certChain, TSAClient tsc, byte[] ocsp,
        PdfSignatureAppearance sap)
```

Change signature to include TSA digest:

```java
protected byte[] calculateSignature(PdfPKCS7 sgn, int size, MessageDigest messageDigest,
        Calendar cal, PDFSignerParameters params, Certificate[] certChain, TSAClient tsc, byte[] ocsp,
        PdfSignatureAppearance sap, String tsaDigestAlgorithm)
```

Change:

```java
byte[] encodedSig = sgn.getEncodedPKCS7(hash, cal, tsc, ocsp);
```

to a new overloaded method:

```java
byte[] encodedSig = sgn.getEncodedPKCS7(hash, cal, tsc, ocsp, tsaDigestAlgorithm);
```

Update all callers in `PDFSigner.java` and tests.

### Step 7: Add `PdfPKCS7.getEncodedPKCS7` Overload With TSA Digest

File:

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/PdfPKCS7.java`

Current overloads:

```java
public byte[] getEncodedPKCS7()
public byte[] getEncodedPKCS7(byte secondDigest[], Calendar signingTime)
public byte[] getEncodedPKCS7(byte secondDigest[], Calendar signingTime, TSAClient tsaClient, byte[] ocsp)
```

Add:

```java
public byte[] getEncodedPKCS7(byte secondDigest[], Calendar signingTime, TSAClient tsaClient, byte[] ocsp, String tsaDigestAlgorithm) {
    ...
}
```

Make the existing 4-argument method delegate to the new method with SHA-1 to preserve compatibility:

```java
public byte[] getEncodedPKCS7(byte secondDigest[], Calendar signingTime, TSAClient tsaClient, byte[] ocsp) {
    return getEncodedPKCS7(secondDigest, signingTime, tsaClient, ocsp, "SHA-1");
}
```

Inside the new method, replace:

```java
byte[] tsImprint = MessageDigest.getInstance("SHA-1").digest(digest);
```

with:

```java
byte[] tsImprint = MessageDigest.getInstance(tsaDigestAlgorithm).digest(digest);
```

If `tsaDigestAlgorithm` is null or blank, use SHA-1 for backwards compatibility.

### Step 8: Add TSA Digest Support To `TSAClientBouncyCastle`

File:

`signserver/modules/SignServer-Lib-iText/src/com/lowagie/text/pdf/TSAClientBouncyCastle.java`

Add a field:

```java
protected DERObjectIdentifier digestAlgorithmOid;
```

or if this Bouncy Castle version exposes `DERObjectIdentifier` from `org.bouncycastle.asn1`, import it explicitly.

Existing imports include:

```java
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.x509.*;
```

The code already uses `X509ObjectIdentifiers.id_SHA1`, so for SHA-256 use the available Bouncy Castle OID constant if present. If not present, construct it manually:

```java
new DERObjectIdentifier("2.16.840.1.101.3.4.2.1")
```

Add constructor overloads while preserving existing constructors:

```java
public TSAClientBouncyCastle(String url, String username, String password, String digestAlgorithm) {
    this(url, username, password, 7168, digestAlgorithm);
}

public TSAClientBouncyCastle(String url, String username, String password, int tokSzEstimate, String digestAlgorithm) {
    this(url, username, password, tokSzEstimate);
    this.digestAlgorithmOid = getDigestAlgorithmOid(digestAlgorithm);
}
```

Set default digest OID to SHA-1 in existing constructors to preserve compatibility:

```java
this.digestAlgorithmOid = X509ObjectIdentifiers.id_SHA1;
```

Add helper:

```java
private static DERObjectIdentifier getDigestAlgorithmOid(final String algorithm) {
    if (algorithm == null || algorithm.length() == 0 || "SHA1".equalsIgnoreCase(algorithm) || "SHA-1".equalsIgnoreCase(algorithm)) {
        return X509ObjectIdentifiers.id_SHA1;
    } else if ("SHA256".equalsIgnoreCase(algorithm) || "SHA-256".equalsIgnoreCase(algorithm)) {
        return new DERObjectIdentifier("2.16.840.1.101.3.4.2.1");
    } else if ("SHA384".equalsIgnoreCase(algorithm) || "SHA-384".equalsIgnoreCase(algorithm)) {
        return new DERObjectIdentifier("2.16.840.1.101.3.4.2.2");
    } else if ("SHA512".equalsIgnoreCase(algorithm) || "SHA-512".equalsIgnoreCase(algorithm)) {
        return new DERObjectIdentifier("2.16.840.1.101.3.4.2.3");
    } else if ("SHA224".equalsIgnoreCase(algorithm) || "SHA-224".equalsIgnoreCase(algorithm)) {
        return new DERObjectIdentifier("2.16.840.1.101.3.4.2.4");
    }
    throw new IllegalArgumentException("Unsupported TSA digest algorithm: " + algorithm);
}
```

Change:

```java
TimeStampRequest request = tsqGenerator.generate(X509ObjectIdentifiers.id_SHA1, imprint, nonce);
```

to:

```java
TimeStampRequest request = tsqGenerator.generate(digestAlgorithmOid, imprint, nonce);
```

Important:

The imprint length must match this OID. That is why `PdfPKCS7` must also compute `tsImprint` with the same `tsaDigestAlgorithm`.

### Step 9: Update `PDFSigner.getTimeStampClient`

File:

`PDFSigner.java`

Current method:

```java
protected TSAClient getTimeStampClient(String url, String username, String password) {
    return new TSAClientBouncyCastle(url, username, password);
}
```

Change to:

```java
protected TSAClient getTimeStampClient(String url, String username, String password, String digestAlgorithm) {
    return new TSAClientBouncyCastle(url, username, password, digestAlgorithm);
}
```

Update caller:

```java
tsc = getTimeStampClient(params.getTsa_url(), params.getTsa_username(), params.getTsa_password(), tsaDigestAlgorithm);
```

Compatibility with tests:

Tests overriding the old 3-argument method must be updated to override the new 4-argument method. Alternatively, keep the old method as a delegating overload:

```java
protected TSAClient getTimeStampClient(String url, String username, String password) {
    return getTimeStampClient(url, username, password, "SHA-1");
}
```

Recommended:

Keep the old overload temporarily to reduce test and subclass breakage, but make production call the new overload.

### Step 10: Update Signature Size Handling If Needed

File:

`PDFSigner.java`

The current signature size estimate may still be adequate. However, SHA-256 timestamp responses and 3k RSA keys can increase size.

Existing code retries with 15% padding if the signature is too large:

```java
return addSignatureToPDFDocument(params, pdfbytes, password, (int)(contentExact * 1.15));
```

Keep this behavior.

Optional improvement:

Increase the TSA estimate from 4096 to 7168 in `calculateEstimatedSignatureSize(...)`, matching the comment and current `TSAClientBouncyCastle` default.

Current local code still adds 4096:

```java
final int tscSize = 4096;
```

Recommended change:

```java
final int tscSize = 7168;
```

Reason:

Larger TSA chains and SHA-256 tokens are likely. The retry path exists, but a better first estimate avoids double signing.

### Step 11: Update Unit Tests

File:

`signserver/modules/SignServer-Module-PDFSigner/test/org/signserver/module/pdfsigner/PDFSignerUnitTest.java`

Add or update tests for:

#### Subfilter Regression

Sign a simple PDF and inspect the signature dictionary to confirm `/SubFilter /adbe.pkcs7.detached`.

Potential approach:

Use `PdfReader`, `AcroFields`, and signature names. The test likely needs to inspect the signature dictionary through low-level PDF objects if `AcroFields` does not expose subfilter directly.

Expected value:

`adbe.pkcs7.detached`

#### SHA-256 CMS Digest

Configure worker property:

`DIGESTALGORITHM=SHA256`

Sign a PDF.

Validate one of:

- The `PdfPKCS7` parsed from the signature reports SHA-256.
- The ASN.1 CMS structure contains digest algorithm OID `2.16.840.1.101.3.4.2.1`.
- At minimum, signing succeeds and no SHA-1 hardcoded path is used.

Preferred assertion:

CMS digest algorithm OID is SHA-256.

#### Invalid Digest Algorithm

Configure:

`DIGESTALGORITHM=NO_SUCH_DIGEST`

Expected result:

`SignServerException` before signing or while constructing `PdfPKCS7`.

#### SHA-256 TSA Request

The existing `MockedTSAClient` only receives the imprint bytes from `PdfPKCS7`; it does not verify the OID sent by `TSAClientBouncyCastle`.

Add a focused test for `TSAClientBouncyCastle` or enhance mock behavior to verify:

- For `TSA_DIGESTALGORITHM=SHA256`, the imprint passed to the TSA client is 32 bytes.
- If using a test subclass of `TSAClientBouncyCastle`, intercept request bytes in `getTSAResponse(byte[] requestBytes)`, parse `TimeStampRequest`, and assert `getMessageImprintAlgOID()` is SHA-256.

Example test strategy:

```java
TSAClientBouncyCastle client = new TSAClientBouncyCastle("http://example.invalid", null, null, "SHA256") {
    @Override
    protected byte[] getTSAResponse(byte[] requestBytes) throws Exception {
        TimeStampRequest request = new TimeStampRequest(requestBytes);
        assertEquals(TSPAlgorithms.SHA256, request.getMessageImprintAlgOID());
        assertEquals(32, request.getMessageImprintDigest().length);
        throw new ExpectedTestException();
    }
};
client.getTimeStampToken(new byte[32]);
```

Adjust for the exact Bouncy Castle API version available in this repository.

#### Existing Tests To Keep Passing

Run existing PDFSigner unit tests after changes, especially:

- Password-protected signing.
- Permission setting/removal/rejection.
- Visible signature and custom image tests.
- CRL/OCSP size estimation tests.
- Duplicate indirect object protection.
- Timestamp retry sizing tests.

### Step 12: Update System Tests If Practical

File:

`signserver/modules/SignServer-Test-System/test/org/signserver/module/pdfsigner/PDFSignerTest.java`

Add a system-level test if the test framework makes it easy:

- Configure `WORKER5675.DIGESTALGORITHM=SHA256`.
- Sign a PDF.
- Assert output is readable and not tampered.
- Assert subfilter remains `adbe.pkcs7.detached`.

Keep this as optional if the unit tests are sufficient and system tests are costly to run locally.

### Step 13: Update Configuration Examples

Files:

`signserver/modules/SignServer-Module-PDFSigner/src/conf/demo-part-config.properties`

`signserver/modules/SignServer-Module-PDFSigner/src/conf/soft-part-config.properties`

Potentially:

`signserver/modules/SignServer-Module-PDFSigner/src/conf/junittest-part-config.properties`

Add commented examples:

```properties
# Digest algorithm used for the PDF/CMS signature. Recommended: SHA256.
# WORKERGENID1.DIGESTALGORITHM=SHA256

# Digest algorithm used in RFC 3161 timestamp requests. Recommended: SHA256.
# WORKERGENID1.TSA_DIGESTALGORITHM=SHA256
```

For `junittest-part-config.properties`, add active values only if the tests are updated to expect SHA-256 globally. Otherwise keep tests explicit by setting worker properties in test methods.

### Step 14: Update Documentation

File:

`signserver/modules/SignServer-Doc/src/xdocs/manual/complete.en.xml`

Add worker property rows under PDFSigner properties:

#### `DIGESTALGORITHM`

Text:

The digest algorithm used for the CMS/PDF signature. For RSA keys, `SHA256` is recommended. For DSA keys, only `SHA1` is supported by this signer.

Default:

`SHA256` if using the recommended default.

Supported values:

`SHA1`, `SHA224`, `SHA256`, `SHA384`, `SHA512` and dashed variants where supported.

#### `TSA_DIGESTALGORITHM`

Text:

The digest algorithm used for the RFC 3161 timestamp request message imprint.

Default:

`SHA256` if using the recommended default.

Supported values:

Same as above, subject to TSA policy.

### Step 15: Build And Verification Commands

Identify the project’s actual Ant targets before final verification. This legacy repository likely uses Ant rather than Maven.

Candidate commands to inspect targets:

```bash
ant -projecthelp
```

Likely targeted verification commands will be one or more of:

```bash
ant build
ant test:module -Dmodule=SignServer-Module-PDFSigner
ant test
```

If exact targets are not available, use the repository’s documented build/test commands.

Minimum verification:

- Compile `SignServer-Lib-iText`.
- Compile `SignServer-Module-PDFSigner`.
- Run `PDFSignerUnitTest`.
- Run relevant system PDFSigner test if environment supports it.

Manual functional verification with a real TSA:

1. Configure a PDFSigner worker with RSA key and certificate chain.
2. Set `DIGESTALGORITHM=SHA256`.
3. Set `TSA_URL` to the upgraded TSA.
4. Set `TSA_DIGESTALGORITHM=SHA256`.
5. Sign a sample PDF.
6. Inspect with Adobe Reader or a PDF signature validator.
7. Confirm the signature subfilter is `adbe.pkcs7.detached`.
8. Confirm the CMS digest algorithm is SHA-256.
9. Confirm the timestamp token was accepted by the TSA and uses SHA-256 message imprint.
10. Confirm validation passes with the 3k RSA TSA chain.

## Suggested Patch Sequence

Implement in small commits or patch chunks:

1. Add digest properties and use `DIGESTALGORITHM` in `PDFSigner` for CMS digest.
2. Add `PdfPKCS7.getEncodedPKCS7(..., tsaDigestAlgorithm)` overload.
3. Add digest-configurable constructors to `TSAClientBouncyCastle`.
4. Wire `TSA_DIGESTALGORITHM` from `PDFSigner` to `TSAClientBouncyCastle` and `PdfPKCS7`.
5. Add DSA validation.
6. Add tests for subfilter and SHA-256 digest behavior.
7. Add tests for SHA-256 TSA request behavior.
8. Update docs/config examples.
9. Run build/tests and fix compile issues caused by old Bouncy Castle API names.

## Risks And Mitigations

### Risk: Old Bouncy Castle API Names Differ

This codebase uses an older Bouncy Castle version. Constants like `TSPAlgorithms.SHA256` may exist in tests, while digest OID constants may not exist in `TSAClientBouncyCastle` imports.

Mitigation:

Use explicit `DERObjectIdentifier` values for SHA-2 OIDs if named constants are missing.

OID map:

- SHA-1: `1.3.14.3.2.26`
- SHA-224: `2.16.840.1.101.3.4.2.4`
- SHA-256: `2.16.840.1.101.3.4.2.1`
- SHA-384: `2.16.840.1.101.3.4.2.2`
- SHA-512: `2.16.840.1.101.3.4.2.3`

### Risk: `MessageDigest.getInstance("SHA256")` Provider Support

Some Java providers prefer `SHA-256`, though common providers usually accept both `SHA256` and `SHA-256`.

Mitigation:

Normalize for JCA if needed:

```java
private static String toJcaDigestName(String algorithm) {
    if ("SHA256".equalsIgnoreCase(algorithm)) return "SHA-256";
    if ("SHA384".equalsIgnoreCase(algorithm)) return "SHA-384";
    if ("SHA512".equalsIgnoreCase(algorithm)) return "SHA-512";
    if ("SHA224".equalsIgnoreCase(algorithm)) return "SHA-224";
    if ("SHA1".equalsIgnoreCase(algorithm)) return "SHA-1";
    return algorithm;
}
```

But be careful: `PdfPKCS7.allowedDigests` accepts both dashed and non-dashed names, while `Signature.getInstance(getDigestAlgorithm())` may produce names like `SHA256withRSA`. Test before normalizing everywhere.

### Risk: Existing Workers Expect SHA-1 Output

Changing default `DIGESTALGORITHM` to SHA-256 changes signature output semantics.

Mitigation:

Decide default based on deployment needs:

- Use `SHA256` default for security-forward behavior.
- Use `SHA1` default and configure only target workers for conservative backward compatibility.

Recommendation for this repository’s stated need:

Use `SHA256` default, but explicitly document the change.

### Risk: Timestamp Token Size Estimate Too Small

SHA-256 and longer TSA chains can increase timestamp token size.

Mitigation:

Increase first estimate to 7168 and keep the existing 15% retry path.

### Risk: Adobe/PDF Version Compatibility

SignServer 5.2.0 has `PdfVersionCompatibilityChecker` to upgrade old PDF versions for newer digest algorithms. The local SignServer 3.4.1 code does not appear to include this class.

Mitigation:

Do not add PDF version upgrade in the first pass unless tests or real PDFs show compatibility failures. If failures occur with old PDF versions and SHA-256, backport only the minimal compatibility checker logic from 5.2.0 as a second phase.

### Risk: Long-Term Validation Expectations

This plan enables SHA-256 CMS signatures and SHA-256 timestamp requests. It does not implement full PAdES-LTV/DSS structures.

Mitigation:

Keep existing CRL/OCSP embedding behavior. If LTV/PAdES is required, handle it as a separate project.

## Acceptance Criteria

The change is complete when:

- PDFSigner still writes `/SubFilter /adbe.pkcs7.detached`.
- PDFSigner can create a PDF signature with CMS digest SHA-256 using an RSA key.
- Timestamp requests can use SHA-256 message imprint and SHA-256 request OID.
- Existing local PDFSigner features still pass tests.
- Invalid digest configuration fails clearly.
- DSA keys are rejected for non-SHA-1 digest configuration.
- Documentation and sample configs describe `DIGESTALGORITHM` and `TSA_DIGESTALGORITHM`.

## Recommended Production Worker Configuration

For the target upgraded TSA and RSA signing key:

```properties
WORKER<N>.DIGESTALGORITHM=SHA256
WORKER<N>.TSA_DIGESTALGORITHM=SHA256
WORKER<N>.TSA_URL=<tsa-url>
```

If the TSA requires authentication:

```properties
WORKER<N>.TSA_USERNAME=<username>
WORKER<N>.TSA_PASSWORD=<password>
```

Keep existing worker properties for visible signatures, permissions, archive-to-disk, CRL, OCSP, and password handling unchanged.

## Summary Task Plan And Estimates

The estimates below assume one engineer already familiar with this SignServer 3.4.1 codebase and able to run the module/unit test suite locally. They are workday estimates, not elapsed calendar time. Add extra time if application-server system tests or real TSA integration environments are slow to provision.

| Phase | Tasks | Estimate |
| --- | --- | ---: |
| 1. Confirm build/test baseline | Identify exact Ant targets, run current PDFSigner/iText build and relevant tests before code changes, record any pre-existing failures. | 0.5 day |
| 2. PDF CMS digest configurability | Add `DIGESTALGORITHM`, read/validate it, replace hardcoded SHA-1 in `PDFSigner`, add DSA guard, keep `adbe.pkcs7.detached` unchanged. | 1.0 day |
| 3. TSA digest configurability | Add `TSA_DIGESTALGORITHM`, add `PdfPKCS7.getEncodedPKCS7(..., tsaDigestAlgorithm)`, update `TSAClientBouncyCastle` to emit matching SHA-256 request OIDs and preserve old constructors. | 1.5 days |
| 4. Signature-size and compatibility adjustments | Increase timestamp size estimate if chosen, verify 3k RSA and larger TSA chains do not overflow the signature container, preserve 15% retry behavior. | 0.5 day |
| 5. Unit tests | Add tests for detached subfilter, SHA-256 CMS digest, invalid digest configuration, DSA/non-SHA-1 rejection, and SHA-256 TSA request generation. | 1.5 days |
| 6. Regression tests | Run existing PDFSigner tests for passwords, visible signatures, existing field signing, permissions, CRL/OCSP, archive-to-disk, and duplicate-object rejection. Fix regressions. | 1.0 day |
| 7. Documentation and config examples | Update PDFSigner manual and sample worker configs with `DIGESTALGORITHM` and `TSA_DIGESTALGORITHM`. | 0.5 day |
| 8. Real TSA validation | Configure a test worker with RSA key, `DIGESTALGORITHM=SHA256`, `TSA_DIGESTALGORITHM=SHA256`, and the upgraded TSA. Sign and validate real PDFs. | 1.0 day |

Estimated total:

`7.5 workdays`

Recommended contingency:

`+1.0 to 2.0 workdays` if the old Bouncy Castle API differs from expected names, if Ant targets are brittle, or if real TSA validation exposes PDF-version compatibility issues.

Suggested delivery milestones:

1. Milestone A: SHA-256 CMS signatures compile and unit tests pass without timestamping. Estimate: 2.0 workdays.
2. Milestone B: SHA-256 timestamp requests compile and unit tests prove matching imprint/OID behavior. Estimate: 2.0 additional workdays.
3. Milestone C: Full PDFSigner regression suite and docs/config examples complete. Estimate: 2.5 additional workdays.
4. Milestone D: Real TSA integration validated with production-like worker configuration. Estimate: 1.0 additional workday.

## Open Questions

1. Should `DIGESTALGORITHM` default to `SHA256` globally, or should the default remain `SHA1` with production workers explicitly configured for `SHA256`?
2. Does the target TSA require SHA-256 for the timestamp request message imprint, or only for the TSA signing certificate/signature algorithm? The code should support SHA-256 imprint either way.
3. Is support for `TSA_WORKER` from SignServer 5.2.0 needed, or is external `TSA_URL` enough?
4. Do the PDFs being signed include very old PDF versions where SHA-256 validation requires version upgrade behavior?

## Recommended First Implementation Choice

Use this first-pass scope:

- Keep local PDFSigner.
- Keep `adbe.pkcs7.detached` hardcoded.
- Add `DIGESTALGORITHM`, default `SHA256`.
- Add `TSA_DIGESTALGORITHM`, default `SHA256`.
- Patch `PdfPKCS7` and `TSAClientBouncyCastle` narrowly for configurable TSA digest.
- Increase TSA size estimate to 7168.
- Add regression tests for detached subfilter, SHA-256 CMS digest, and SHA-256 timestamp request.

Defer these unless needed:

- Full SignServer 5.2.0 PDFSigner replacement.
- `TSA_WORKER` backport.
- File-backed request/response support.
- PDF version upgrade logic.
- PAdES-LTV/DSS support.
