package org.jscep.jester;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.jscep.jester.http.RetryAfterParser;
import org.jscep.jester.io.EntityDecoder;
import org.jscep.jester.io.EntityEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public class EstClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EstClient.class);
    public static final String WELL_KNOWN_LOCATION = "/.well-known/est/";
    private static final String CA_CERTIFICATES_DISTRIBUTION = "cacerts";
    private static final String SIMPLE_ENROLL = "simpleenroll";
    private static final String SIMPLE_RENEW = "simplereenroll";
    private String host;
    private String caLabel;
    private HttpClient httpClient;
    private EntityDecoder<X509Certificate[]> certDecoder;
    private EntityEncoder<CertificationRequest> csrEncoder;
    private final KeyStore trustStore;

    public EstClient(HttpClient httpClient, EntityDecoder<X509Certificate[]> certDecoder, EntityEncoder<CertificationRequest> csrEncoder, String host) {
        this(httpClient, certDecoder, csrEncoder, host, null);
    }

    public EstClient(HttpClient httpClient, EntityDecoder<X509Certificate[]> certDecoder, EntityEncoder<CertificationRequest> csrEncoder, String host, String label) {
        this(httpClient, certDecoder, csrEncoder, host, null, null);
    }

    public EstClient(HttpClient httpClient, EntityDecoder<X509Certificate[]> certDecoder, EntityEncoder<CertificationRequest> csrEncoder, String host, String caLabel, KeyStore explicitCaDatabase) {
        this.host = host;
        this.caLabel = caLabel;
        this.certDecoder = certDecoder;
        this.csrEncoder = csrEncoder;
        this.httpClient = httpClient;
        this.trustStore = explicitCaDatabase;
    }

    public X509Certificate[] obtainCaCertificates() throws IOException {
        HttpGet get = new HttpGet(buildUrl(CA_CERTIFICATES_DISTRIBUTION));
        HttpResponse response = httpClient.execute(get);

        int statusCode = response.getStatusLine().getStatusCode();
        checkStatusCode(statusCode, 200);
        checkContentType(response);
        checkContentTransferEncoding(response);

        HttpEntity entity = response.getEntity();
        X509Certificate[] certs = certDecoder.decode(new Base64InputStream(entity.getContent()));

        return certs;
    }

    private void checkStatusCode(int actual, int... expected) throws EstProtocolException {
        if (Arrays.binarySearch(expected, actual) < 0) {
            throw new EstProtocolException("Status code should be in " + Arrays.toString(expected) + "; was " + actual);
        }
    }

    private void checkContentTransferEncoding(HttpResponse response) throws EstProtocolException {
        Header contentTransferEncoding = response.getFirstHeader("Content-Transfer-Encoding");
        if (contentTransferEncoding == null || !contentTransferEncoding.getValue().equals("base64")) {
            throw new EstProtocolException("Content transfer encoding should be base64");
        }
    }

    public EnrollmentResponse enroll(CertificationRequest csr) throws IOException {
        return enroll(csr, SIMPLE_ENROLL);
    }

    public EnrollmentResponse renew(CertificationRequest csr) throws IOException {
        return enroll(csr, SIMPLE_RENEW);
    }

    private EnrollmentResponse enroll(CertificationRequest csr, String command) throws IOException {
        HttpPost post = new HttpPost(buildUrl(command));
        post.addHeader("Content-Type", "application/pkcs10");
        post.addHeader("Content-Transfer-Encoding", "base64");
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        Base64OutputStream base64Out = new Base64OutputStream(bOut);
        csrEncoder.encode(base64Out, csr);
        base64Out.flush();
        base64Out.close();
        post.setEntity(new ByteArrayEntity(bOut.toByteArray()));
        HttpResponse response = httpClient.execute(post);

        int statusCode = response.getStatusLine().getStatusCode();
        checkStatusCode(statusCode, 200, 202);
        if (statusCode == 200) {
            checkContentType(response);
            checkContentTransferEncoding(response);

            HttpEntity entity = response.getEntity();
            X509Certificate[] certs = certDecoder.decode(new Base64InputStream(entity.getContent()));

            return new EnrollmentResponse(certs[0]);
        } else {
            String retryAfter = response.getFirstHeader("Retry-After").getValue();
            return new EnrollmentResponse(RetryAfterParser.parse(retryAfter));
        }
    }

    private String buildUrl(String command) {
        if (caLabel != null) {
            return "https://" + host + WELL_KNOWN_LOCATION + caLabel + "/" + command;
        } else {
            return "https://" + host + WELL_KNOWN_LOCATION + command;
        }
    }

    private void checkContentType(HttpResponse response) throws EstProtocolException {
        Header contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.getValue().equals("application/pkcs7-mime")) {
            throw new EstProtocolException("Content type should be application/pkcs7-mime");
        }
    }
}
