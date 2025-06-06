import java.io.*;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLKeyException;
import javax.net.ssl.SSLPeerUnverifiedException;

public class EndpointTracer {

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
        String outputName = "trace_output.txt";

        // Write results to the file using a PrintWriter
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputName))) {
            writer.println("Tracing endpoint: " + endpoint);

            traceRoute(endpoint, writer);
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
                }
            }
            int exit = p.waitFor();
            if (exit != 0) {
                writer.println("Traceroute finished with code " + exit
                        + ". The tool might be missing or the network is blocked.");
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

        try {
            URL url = endpoint.startsWith("http") ? new URL(endpoint)
                    : new URL("https://" + endpoint);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();
            int code = conn.getResponseCode();
            writer.println("Connection successful. Response code: " + code);
        } catch (UnknownHostException e) {
            writer.println("Unable to resolve host. This usually means a DNS problem or missing network connectivity.");
        } catch (ConnectException e) {
            writer.println("Connection refused. A firewall, proxy, or antivirus might be blocking access.");
        } catch (SocketTimeoutException e) {
            writer.println("The connection timed out. The network could be congested or the server is unreachable.");
        } catch (SocketException e) {
            writer.println("A network socket error occurred. The connection may have been reset or a broken pipe detected.");
        } catch (SSLHandshakeException e) {
            writer.println("TLS handshake failed. The certificate could be invalid, untrusted, expired, or the hostname does not match.");
        } catch (SSLKeyException e) {
            writer.println("SSL key error. Check that the key and certificate configuration is correct.");
        } catch (SSLPeerUnverifiedException e) {
            writer.println("Failed to verify the server certificate. It might not be signed by a trusted CA.");
        } catch (SSLException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CertificateException) {
                writer.println("Certificate validation failed. The certificate might be malformed or unreadable.");
            } else if (cause instanceof KeyManagementException) {
                writer.println("Error setting up the SSL context. Verify your truststore and key configuration.");
            } else if (cause instanceof KeyStoreException) {
                writer.println("Unable to access the keystore. The truststore path or password may be incorrect.");
            } else if (cause instanceof NoSuchAlgorithmException) {
                writer.println("Missing cryptographic algorithm. The JVM might not support the required TLS version.");
            } else {
                writer.println("General SSL error: " + e.getMessage());
            }
        } catch (IllegalStateException e) {
            writer.println("Illegal state encountered. This could be due to class instrumentation or classpath issues.");
        } catch (NoClassDefFoundError e) {
            writer.println("A required class was not found. Ensure all dependencies are present.");
        } catch (SecurityException e) {
            writer.println("A security manager is preventing the connection.");
        } catch (UnsupportedOperationException e) {
            writer.println("The requested operation is not supported in this environment.");
        } catch (IOException e) {
            writer.println("I/O error during communication: " + e.getMessage());
        } catch (Exception e) {
            writer.println("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
