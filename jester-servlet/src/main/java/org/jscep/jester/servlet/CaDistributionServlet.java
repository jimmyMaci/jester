package org.jscep.jester.servlet;

import org.apache.commons.codec.binary.Base64OutputStream;
import org.jscep.jester.EstMediator;
import org.jscep.jester.io.EntityEncoder;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;

@WebServlet(urlPatterns = {"/cacerts"})
public class CaDistributionServlet extends HttpServlet {
    public static final String APPLICATION_PKCS7_MIME_SMIME_CERTS_ONLY = "application/pkcs7-mime;smime-type=certs-only";
    @Inject
    private EstMediator est;
    @Inject
    private EntityEncoder<X509Certificate> encoder;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType(APPLICATION_PKCS7_MIME_SMIME_CERTS_ONLY);
        response.addHeader("Content-Transfer-Encoding", "base64");

        encoder.encode(new Base64OutputStream(response.getOutputStream()), est.getCaCertificates());
    }
}
