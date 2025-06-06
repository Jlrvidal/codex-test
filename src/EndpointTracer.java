import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class EndpointTracer {

    // Stores the certificate presented by the server during the last connection attempt
    private static X509Certificate lastServerCert;

    /**
     * Entry point of the program. It expects a single argument with the
     * endpoint to trace and test. All output is written to a file in the
     * current working directory so the user can review it later.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java EndpointTracer <endpoint>");
            System.exit(1);
        }

        String endpoint = args[0];
        String host = extractHost(endpoint); // hostname used for traceroute
        String outputName = "trace_output.txt";

        // Write results to the file using a PrintWriter
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputName))) {
            writer.println("Tracing endpoint: " + endpoint);

            System.out.println("Running traceroute to " + host + "...");
            traceRoute(host, writer);
            System.out.println("Testing HTTPS connection to " + endpoint + "...");
            testConnection(endpoint, writer);

            System.out.println("Results written to " + outputName);
        } catch (IOException e) {
            System.err.println("Unable to write output file: " + e.getMessage());
        }
    }

    /**
     * Runs the traceroute (or tracert on Windows) command and records the
     * entire output in the given writer.
     */
    private static void traceRoute(String endpoint, PrintWriter writer) {
        writer.println("Running traceroute to " + endpoint + "...");
        System.out.println("Tracing route to " + endpoint);
        String os = System.getProperty("os.name").toLowerCase();
        String[] cmd = os.contains("win") ? new String[] {"tracert", endpoint}
                : new String[] {"traceroute", endpoint};
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.println(line);
                    System.out.println(line); // show progress on screen
                }
           }
           int exit = p.waitFor();
           if (exit != 0) {
               writer.println("Traceroute finished with code " + exit
                       + ". The tool might be missing or the network is blocked.");
                System.out.println("Traceroute finished with code " + exit);
           }
        } catch (Exception e) {
            writer.println("Could not execute traceroute: " + e.getMessage());
        }
    }

    /**
     * Attempts an HTTP connection to the endpoint and reports common problems
     * like DNS resolution issues, firewalls, or SSL certificate errors.
     */
    private static void testConnection(String endpoint, PrintWriter writer) {
        writer.println();
        writer.println("Testing HTTPS connection to " + endpoint + "...");
        System.out.println("Connecting to " + endpoint);

        try {
            URL url = endpoint.startsWith("http") ? new URL(endpoint)
                    : new URL("https://" + endpoint);
            CapturingTrustManager capTm = createCapturingTrustManager();
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { capTm }, new SecureRandom());
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setSSLSocketFactory(ctx.getSocketFactory());
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            int code = conn.getResponseCode();
            String success = "Connection successful. Response code: " + code;
            writer.println(success);
            System.out.println(success);
        } catch (UnknownHostException e) {
            String msg = "Unable to resolve host. This usually means a DNS problem or missing network connectivity.";
            writer.println(msg);
            System.out.println(msg);
        } catch (ConnectException e) {
            String msg = "Connection refused. A firewall, proxy, or antivirus might be blocking access.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SocketTimeoutException e) {
            String msg = "The connection timed out. The network could be congested or the server is unreachable.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SocketException e) {
            String msg = "A network socket error occurred. The connection may have been reset or a broken pipe detected.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SSLHandshakeException e) {
            if (lastServerCert != null) {
                // getSubjectDN() is deprecated; use getSubjectX500Principal() to
                // obtain a readable representation of the certificate subject
                String subject = lastServerCert.getSubjectX500Principal().getName();
                String msg = "TLS handshake failed. Certificate not trusted: " + subject

                String msg = "TLS handshake failed. Certificate not trusted: " + lastServerCert.getSubjectDN()
                        + ". Install this certificate or its issuing CA in the JVM truststore.";
                writer.println(msg);
                System.out.println(msg);
            } else {
                String msg = "TLS handshake failed. The certificate could be invalid, untrusted, expired, or the hostname does not match.";
                writer.println(msg);
                System.out.println(msg);
            }
        } catch (SSLKeyException e) {
            String msg = "SSL key error. Check that the key and certificate configuration is correct.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SSLPeerUnverifiedException e) {
            String msg = "Failed to verify the server certificate. It might not be signed by a trusted CA.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SSLException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CertificateException) {
                String msg = "Certificate validation failed. The certificate might be malformed or unreadable.";
                writer.println(msg);
                System.out.println(msg);
            } else if (cause instanceof KeyManagementException) {
                String msg = "Error setting up the SSL context. Verify your truststore and key configuration.";
                writer.println(msg);
                System.out.println(msg);
            } else if (cause instanceof KeyStoreException) {
                String msg = "Unable to access the keystore. The truststore path or password may be incorrect.";
                writer.println(msg);
                System.out.println(msg);
            } else if (cause instanceof NoSuchAlgorithmException) {
                String msg = "Missing cryptographic algorithm. The JVM might not support the required TLS version.";
                writer.println(msg);
                System.out.println(msg);
            } else {
                String msg = "General SSL error: " + e.getMessage();
                writer.println(msg);
                System.out.println(msg);
            }
        } catch (IllegalStateException e) {
            String msg = "Illegal state encountered. This could be due to class instrumentation or classpath issues.";
            writer.println(msg);
            System.out.println(msg);
        } catch (NoClassDefFoundError e) {
            String msg = "A required class was not found. Ensure all dependencies are present.";
            writer.println(msg);
            System.out.println(msg);
        } catch (SecurityException e) {
            String msg = "A security manager is preventing the connection.";
            writer.println(msg);
            System.out.println(msg);
        } catch (UnsupportedOperationException e) {
            String msg = "The requested operation is not supported in this environment.";
            writer.println(msg);
            System.out.println(msg);
        } catch (IOException e) {
            String msg = "I/O error during communication: " + e.getMessage();
            writer.println(msg);
            System.out.println(msg);
        } catch (Exception e) {
            String msg = "Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            writer.println(msg);
            System.out.println(msg);
        }
    }

    /**
     * Extracts only the hostname from a full URL string. If parsing fails or no scheme
     * is provided, the original value is returned.
     */
    private static String extractHost(String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            try {
                return new URL(endpoint).getHost();
            } catch (MalformedURLException e) {
                // fall through and return original
            }
        }
        return endpoint;
    }

    /**
     * Creates a TrustManager that delegates to the default manager while capturing
     * the certificate chain presented by the server.
     */
    private static CapturingTrustManager createCapturingTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        X509TrustManager defaultTm = (X509TrustManager) tmf.getTrustManagers()[0];
        return new CapturingTrustManager(defaultTm);
    }

    /** TrustManager implementation that remembers the last server certificate. */
    private static class CapturingTrustManager implements X509TrustManager {
        private final X509TrustManager delegate;

        CapturingTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain != null && chain.length > 0) {
                lastServerCert = chain[0];
            }
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }
}
