import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;

/**
 * Simple GUI wrapper around EndpointTracer that shows traceroute
 * and HTTPS connection results in a text area.
 */
public class EndpointTracerGUI extends JFrame {
    private final JTextField hostField = new JTextField(30);
    private final JButton startButton = new JButton("Start");
    private final JButton cancelButton = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea outputArea = new JTextArea(20, 60);

    private SwingWorker<Void, Void> worker;

    public EndpointTracerGUI() {
        super("Endpoint Tracer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        outputArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(outputArea);

        JPanel inputPanel = new JPanel();
        inputPanel.add(new JLabel("Endpoint:"));
        inputPanel.add(hostField);
        inputPanel.add(startButton);
        inputPanel.add(cancelButton);

        progressBar.setStringPainted(true);
        cancelButton.setEnabled(false);

        add(inputPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(progressBar, BorderLayout.SOUTH);

        startButton.addActionListener(this::startAction);
        cancelButton.addActionListener(e -> cancel());

        pack();
        setLocationRelativeTo(null);
    }

    private void startAction(ActionEvent e) {
        String endpoint = hostField.getText().trim();
        if (endpoint.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a host or URL.");
            return;
        }
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setIndeterminate(true);
        outputArea.setText("");

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (PrintWriter writer = createWriter()) {
                    String host = EndpointTracer.extractHost(endpoint);
                    writer.println("Tracing endpoint: " + endpoint);
                    EndpointTracer.traceRoute(host, writer);
                    if (isCancelled()) return null;
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(50);
                    EndpointTracer.testConnection(endpoint, writer);
                    progressBar.setValue(100);
                } catch (IOException ex) {
                    appendText("Unable to write output file: " + ex.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                startButton.setEnabled(true);
                cancelButton.setEnabled(false);
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
            }
        };
        worker.execute();
    }

    private PrintWriter createWriter() throws IOException {
        FileWriter fileWriter = new FileWriter("trace_output.txt");
        Writer textAreaWriter = new Writer() {
            @Override public void write(char[] cbuf, int off, int len) {
                appendText(new String(cbuf, off, len));
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        Writer multi = new Writer() {
            private final Writer[] writers = new Writer[] {fileWriter, textAreaWriter};
            @Override public void write(char[] cbuf, int off, int len) throws IOException {
                for (Writer w : writers) w.write(cbuf, off, len);
            }
            @Override public void flush() throws IOException { for (Writer w : writers) w.flush(); }
            @Override public void close() throws IOException { for (Writer w : writers) w.close(); }
        };
        return new PrintWriter(multi, true);
    }

    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> outputArea.append(text + "\n"));
    }

    private void cancel() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            EndpointTracer.cancel();
            progressBar.setIndeterminate(false);
            progressBar.setString("Cancelled");
            cancelButton.setEnabled(false);
            startButton.setEnabled(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EndpointTracerGUI().setVisible(true));
    }
}
