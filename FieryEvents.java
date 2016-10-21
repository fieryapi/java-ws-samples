import com.cedarsoftware.util.io.JsonWriter;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class FieryEvents {

    private static FieryWebsocketClient ws;

    // Trust All Certificates Special Class to enable REST API Calls to EFI Next
    // API. This class ignores certificate errors accessing Fiery API. Do Not
    // use this in production. Ignores all certificate errors (exposed MITM
    // attack)
    private static final class TrustAllCertificates implements
            X509TrustManager, HostnameVerifier {

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }

        public static void install() {
            try {
                // Do Not use this in production.
                TrustAllCertificates trustAll = new TrustAllCertificates();
                // Install the all-trusting trust manager
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, new TrustManager[]{
                    trustAll
                },
                        new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                // Install the all-trusting host verifier
                HttpsURLConnection.setDefaultHostnameVerifier(trustAll);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(
                        "Failed setting up all trusting certificate manager.",
                        e);
            } catch (KeyManagementException e) {
                throw new RuntimeException(
                        "Failed setting up all trusting certificate manager.",
                        e);
            }
        }
    }

    // Set filters to receive only fiery status change events
    private static void ReceiveFieryStatusChangeEvents() throws IOException {
        System.out.println();
        System.out.println("Scenario: Receive only Fiery status change events");
        System.out.println("Press <Enter> when you want to run next scenario");

        // Ignore all events except device events
        String[] ignoreParams = {"accounting", "job", "jobprogress", "preset", "property", "queue"};
        JsonRpc20.Command ignoreAllEventsExceptDevice = new JsonRpc20.Command("ignore", 1, ignoreParams);
        ws.send(JsonWriter.objectToJson(ignoreAllEventsExceptDevice));

        // Receive device events
        String[] receiveParams = {"device"};
        JsonRpc20.Command receiveDeviceEvents = new JsonRpc20.Command("receive", 2, receiveParams);
        ws.send(JsonWriter.objectToJson(receiveDeviceEvents));

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        in.readLine();
    }

    // Set filters to receive only job is printing? events
    private static void ReceiveJobIsPrintingEvents() throws IOException {
        System.out.println();
        System.out.println("Scenario: Receive only job is printing? events");
        System.out.println("Press <Enter> when you want to run next scenario");

        // Ignore all events except job events
        String[] ignoreParams = {"accounting", "device", "jobprogress", "preset", "property", "queue"};
        JsonRpc20.Command ignoreAllEventsExceptJob = new JsonRpc20.Command("ignore", 1, ignoreParams);
        ws.send(JsonWriter.objectToJson(ignoreAllEventsExceptJob));

        // Receive job events
        String[] receiveParams = {"job"};
        JsonRpc20.Command receiveJobEvents = new JsonRpc20.Command("receive", 2, receiveParams);
        ws.send(JsonWriter.objectToJson(receiveJobEvents));

        // Receive job events only if they contain <is printing?> key in the <attributes> key
        String[] attributes = {"is printing?"};
        JsonRpc20.Filter receiveIsPrintingEvents = new JsonRpc20.Filter(3, new JsonRpc20.Params("job", "add", attributes));
        ws.send(JsonWriter.objectToJson(receiveIsPrintingEvents));

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        in.readLine();
    }

    // Set filters in batch mode to receive only job is printing? events
    private static void ReceiveJobIsPrintingEventsBatchMode() throws IOException {
        System.out.println();
        System.out.println("Scenario: Receive only job is printing? events by sending messages in batch mode");
        System.out.println("Press <Enter> when you want to run next scenario");

        // Ignore all events except job events
        String[] ignoreParams = {"accounting", "device", "jobprogress", "preset", "property", "queue"};
        JsonRpc20.Command ignoreAllEventsExceptJob = new JsonRpc20.Command("ignore", 1, ignoreParams);

        // Receive job events
        String[] receiveParams = {"job"};
        JsonRpc20.Command receiveJobEvents = new JsonRpc20.Command("receive", 2, receiveParams);

        // Receive job events only if they contain <is printing?> key in the <attributes> key
        String[] attributes = {"is printing?"};
        JsonRpc20.Filter receiveIsPrintingEvents = new JsonRpc20.Filter(3, new JsonRpc20.Params("job", "add", attributes));

        // Send all three commands in batch mode
        Object[] ReceiveJobIsPrintingEventsBatchMode = {ignoreAllEventsExceptJob, receiveJobEvents, receiveIsPrintingEvents};
        ws.send(JsonWriter.objectToJson(ReceiveJobIsPrintingEventsBatchMode));

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        in.readLine();
    }

    public static void main(String[] args) throws IOException,
            InterruptedException {
        // Set the HostName as Fiery Name or IpAddress.
        final String hostname = "https://{{HOST_NAME}}";

        // Set the Username to login to the host fiery.
        final String username = "{{FIERY_USERNAME}}";

        // Set the Password to login to the fiery.
        final String password = "{{FIERY_PASSWORD}}";

        // Set the API Key for to access Fiery APIs.
        final String apikey = "{{API_KEY_STRING}}";

        String jsonPayloadforLogin = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"accessrights\":\"" + apikey + "\"}";

        // Initialize the Session Cookie to null. This cookie is used to make
        // subsequent Fiery API calls once the login was successful.
        String sessionCookie = null;

        //Buffered reader, DataOutputStream and input stream are used to append the json payload to the request and also to read the response for every API call.
        BufferedReader bufferedReader;
        InputStream inputStreamReader;
        DataOutputStream dataOutputStream;
        int responseCode = 0;
        StringBuffer response;

        // Login to fiery using FieryAPI.
        // Do Not use trust all certificates in production. Ignores all
        // certificate errors (exposed MITM attack) Comment the callback if the
        // server has a valid CA signed certificate installed.
        TrustAllCertificates.install();

        // Create a HTTP URL Connection object to connect to Fiery API using the URL object.
        HttpURLConnection connection;

        try {

            // Connect to the Fiery API Login URL using HttpURL Connection.
            connection = (HttpURLConnection) new URL("https://" + hostname + "/live/api/v2/login")
                    .openConnection();

            // Set the HTTP Request Call type as POST.
            connection.setRequestMethod("POST");

            // Set the HTTP Request Content-Type.
            connection.setRequestProperty("Content-Type",
                    "application/json; charset=utf-8");

            // Set the Output to true since the Http Request is POST and
            // contains request data.
            connection.setDoOutput(true);

            // Create a new data output stream to write data to the specified
            // underlying output stream.
            dataOutputStream = new DataOutputStream(
                    connection.getOutputStream());

            // Write the JSON Payload bytes to the output stream object.
            dataOutputStream.writeBytes(jsonPayloadforLogin);

            // Read the input stream from the active HTTP connection.
            inputStreamReader = connection.getInputStream();

            // Read the text from the Input Stream
            bufferedReader = new BufferedReader(new InputStreamReader(
                    inputStreamReader));

            // Create string object to read the response and use it to append
            // the response to buffer.
            String line;

            // Create the string buffer to store the response from Fiery API
            // Call.
            response = new StringBuffer();

            // Retrieve the response from bufferedReader.
            while ((line = bufferedReader.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }

            // Extracting the Session Cookie from the login response to make
            // subsequent fiery api requests.
            String headerName = null;
            for (int i = 1;
                    (headerName = connection.getHeaderFieldKey(i)) != null; i++) {
                if (headerName.equals("Set-Cookie")) {
                    sessionCookie = connection.getHeaderField(i);
                }
            }

            // Print the Session Cookie to the console.
            System.out.println("Session Cookie Received is :" + sessionCookie);

            // Get the Response Code for the Login Request.
            responseCode = connection.getResponseCode();

        } catch (ProtocolException e) {
            sessionCookie = null;
            e.printStackTrace(System.out);
        } catch (IOException e) {
            sessionCookie = null;
            e.printStackTrace(System.out);
        }
        // Verify if Login is successful and Session cookie is not null.
        if (responseCode == 200 && sessionCookie != null) {
            // Ignore all certificates errors when sending request to the fiery server.
            // Using this method without validation on production environment will increase the risk of MITM attack.
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setTrustAll(true);

            // create websocket object
            WebSocketClient client = new WebSocketClient(sslContextFactory);
            ws = new FieryWebsocketClient();
            try {

                // set fiery websocket server address
                URI serverAddress = new URI("wss://" + hostname + "/live/api/v2/events");

                // establish websocket connection
                client.start();
                ClientUpgradeRequest request = new ClientUpgradeRequest();
                request.setHeader("cookie ", sessionCookie);
                client.connect(ws, serverAddress, request);

                // wait until the websocket connection is opened
                ws.awaitClose(15, TimeUnit.SECONDS);
                Thread.sleep(2000);

                // run the scenarios
                ReceiveFieryStatusChangeEvents();
                ReceiveJobIsPrintingEvents();
                ReceiveJobIsPrintingEventsBatchMode();
            } catch (Throwable t) {
                t.printStackTrace(System.out);
            } finally {
                try {
                    client.stop();
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }

            // Logout.
            connection = (HttpURLConnection) new URL("https://" + hostname + "/live/api/v2/logout").openConnection();

            //Set the Request method as GET
            connection.setRequestMethod("GET");

            //Set the Session Cookie to the GET Request.
            connection.setRequestProperty("Cookie", sessionCookie);

            //Read input stream that reads from the open connection
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            //Initiate the String buffer to store the response.
            response = new StringBuffer();

            String inputLine;
            //append the response to buffer.
            while ((inputLine = bufferedReader.readLine()) != null) {
                response.append(inputLine);
            }

            //close the buffered reader.
            bufferedReader.close();
            System.out.println("Log Out\t" + response.toString());
        }
    }
}
