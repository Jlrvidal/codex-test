# codex-test

This small Java application traces the route to a given endpoint and verifies HTTPS connectivity.

## Compile

```bash
javac src/EndpointTracer.java
```

## Run

```bash
java -cp src EndpointTracer <endpoint>
```

Replace `<endpoint>` with the HTTPS URL or hostname you want to check. The program writes all messages to `trace_output.txt` in the current directory so you can review the traceroute (or `tracert` on Windows) output and a summary of the HTTPS connection test.

