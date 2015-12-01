import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.api.Session;

@WebSocket
public class FieryWebsocketClient {

    private final CountDownLatch closeLatch;

    private Session session;

    public FieryWebsocketClient() {
        this.closeLatch = new CountDownLatch(1);
    }

    public boolean awaitClose(int duration, TimeUnit unit) throws InterruptedException {
        return this.closeLatch.await(duration, unit);
    }

    public void send(String message) {
        session.getRemote().sendStringByFuture(message);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        System.out.printf("websocket connection closed: %d%n", statusCode);
        this.session = null;
        this.closeLatch.countDown();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("new websocket connection is opened");
        this.session = session;
        this.closeLatch.countDown();
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        System.out.println(msg);
    }
}
