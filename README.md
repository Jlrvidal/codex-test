# codex-test

This small Java application traces the route to a given endpoint and verifies HTTPS connectivity.
If you provide a full URL, only the hostname is used for the traceroute step so
`traceroute`/`tracert` does not fail.

While running, progress messages are printed to the console so you can follow
each step.

## Compile

```bash

javac src/EndpointTracer.java src/EndpointTracerGUI.java
jar cfe EndpointTracer.jar EndpointTracerGUI -C src .

```

## Run

```bash

java -jar EndpointTracer.jar
```

You can also run the command-line version:

```bash
java -cp src EndpointTracer <endpoint> [userAgent]
```

The application opens a window where you can enter the URL or hostname and optionally set the **User-Agent** used for the HTTPS check. Results are saved to `trace_output.txt` in the current directory so you can review the traceroute (or `tracert` on Windows) output and a summary of the HTTPS connection test.
Errors are explained with possible causes and brief suggestions so that an
average user can act on them, for example by installing a missing certificate in
the JVM truststore if the TLS handshake fails.

