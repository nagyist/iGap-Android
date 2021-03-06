/*
* This is the source code of iGap for Android
* It is licensed under GNU AGPL v3.0
* You should have received a copy of the license in this archive (see LICENSE).
* Copyright © 2017 , iGap - www.iGap.net
* iGap Messenger | Free, Fast and Secure instant messaging application
* The idea of the RooyeKhat Media Company - www.RooyeKhat.co
* All rights reserved.
*/

package net.iGap;

import android.text.format.DateUtils;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketState;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.iGap.helper.HelperConnectionState;
import net.iGap.helper.HelperSetAction;
import net.iGap.helper.HelperTimeOut;
import net.iGap.module.enums.ConnectionState;
import net.iGap.request.RequestWrapper;
import net.iGap.response.HandleResponse;

import static net.iGap.Config.ALLOW_RECONNECT_AGAIN_NORMAL;

public class WebSocketClient {

    public static boolean allowForReconnecting = true;
    public static boolean waitingForReconnecting = false;
    private static int count = 0;
    private static int reconnectQueueLimitation; // this value not allowed to call reconnect method so much
    private static long latestConnectionTryTiming;
    private static long latestConnectionOpenTime = 0;
    private static WebSocket webSocketClient;
    private static WebSocketState connectionState;

    /**
     * add webSocketConnection listeners and try for connect
     *
     * @return WebSocket
     */

    private static synchronized WebSocket createSocketConnection() {
        WebSocket websocketFactory = null;
        try {
            websocketFactory = new WebSocketFactory().setConnectionTimeout((int) (10 * DateUtils.SECOND_IN_MILLIS)).createSocket(Config.URL_WEBSOCKET);
            websocketFactory.addListener(new WebSocketAdapter() {

                @Override public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
                    latestConnectionOpenTime = System.currentTimeMillis();
                    waitingForReconnecting = false;
                    if (G.isSecure) {
                        allowForReconnecting = true;
                        if (webSocketClient != null) {
                            webSocketClient.disconnect();
                        }
                    } else {
                        reconnectQueueLimitation = 0;
                        G.socketConnection = true;
                        HelperConnectionState.connectionState(ConnectionState.CONNECTING);
                        //checkFirstResponse();
                    }

                    super.onConnected(websocket, headers);
                }

                @Override public void onBinaryMessage(WebSocket websocket, final byte[] binary) throws Exception {
                    new HandleResponse(binary).run();
                    super.onBinaryMessage(websocket, binary);
                }

                @Override public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
                    reconnect(true);
                    super.onError(websocket, cause);
                }

                @Override public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
                    super.onFrameSent(websocket, frame);

                    /**
                     * set time after that actually frame was sent
                     */
                    ((RequestWrapper) frame.getRequestWrapper()).time = System.currentTimeMillis();
                }

                @Override public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
                    super.onStateChanged(websocket, newState);
                    connectionState = newState;
                }

                @Override public void onDisconnected(WebSocket websocket, com.neovisionaries.ws.client.WebSocketFrame serverCloseFrame, com.neovisionaries.ws.client.WebSocketFrame clientCloseFrame,
                    boolean closedByServer) throws Exception {
                    allowForReconnecting = true;
                    G.socketConnection = false;
                    reconnect(true);
                    super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
                }

                @Override public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
                    reconnect(true);
                    super.onConnectError(websocket, exception);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        final WebSocket finalWs = websocketFactory;
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                if (G.allowForConnect) {
                    try {
                        if (finalWs != null) {
                            /**
                             * in first make connection client should set latestConnectionTryTiming time
                             * because when run reconnect method timeout checking
                             */
                            latestConnectionTryTiming = System.currentTimeMillis();
                            HelperConnectionState.connectionState(ConnectionState.CONNECTING);
                            finalWs.connect();
                        }
                    } catch (WebSocketException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();

        return websocketFactory;
    }

    /**
     * create a webSocketConnection if is null now
     *
     * @return webSocketConnection
     */

    public static WebSocket getInstance() {
        if (!waitingForReconnecting && (webSocketClient == null || !webSocketClient.isOpen())) {
            waitingForReconnecting = true;
            HelperConnectionState.connectionState(ConnectionState.CONNECTING);
            checkGetInstanceSuccessfully();
            return webSocketClient = createSocketConnection();
        } else {
            return webSocketClient;
        }
    }

    /**
     * check current state of socket for insuring that
     * connection established and if socket connection
     * wasn't open or is null try for reconnecting
     */

    private static void checkGetInstanceSuccessfully() {

        G.handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (webSocketClient == null || !webSocketClient.isOpen()) {
                    reconnect(false);
                }
            }
        }, Config.INSTANCE_SUCCESSFULLY_CHECKING);
    }

    /**
     * clear securing state and reconnect to server
     *
     * @param force if set force true try for reconnect even socket is open.
     * client do this action because maybe connection lost but client not
     * detected this actions(android 7.*).
     */

    public static void reconnect(boolean force) {

        if ((force || (webSocketClient == null || !webSocketClient.isOpen()))) {
            G.handler.postDelayed(new Runnable() {
                @Override public void run() {

                    if (timeDifference(latestConnectionTryTiming) && connectionState != WebSocketState.CONNECTING && (connectionState != WebSocketState.OPEN || (HelperTimeOut.timeoutChecking(0,
                        latestConnectionOpenTime, Config.CONNECTION_OPEN_TIME_OUT)))) {
                        if (reconnectQueueLimitation > 0) {
                            reconnectQueueLimitation--;
                        }

                        HelperSetAction.clearAllActions();
                        if (allowForReconnecting) {
                            allowForReconnecting = false;
                            HelperConnectionState.connectionState(ConnectionState.CONNECTING);
                            if (G.allowForConnect) {
                                latestConnectionTryTiming = System.currentTimeMillis();
                                waitingForReconnecting = false;
                                resetWebsocketInfo();
                                WebSocketClient.getInstance();
                                checkSocketConnection();
                            }
                        }
                    } else {
                        if (reconnectQueueLimitation < Config.TRY_CONNECTION_COUNT) {
                            reconnectQueueLimitation++;
                            allowForReconnecting = true;
                            waitingForReconnecting = false;
                            reconnect(false);
                        } else {
                            reconnectQueueLimitation--;
                        }
                    }
                }
            }, Config.REPEAT_CONNECTION_CHECKING);
        }
    }

    /**
     * check if socket connected or from last try connecting over the past ten seconds and finally
     * reconnect or run this method again
     */

    private static void checkSocketConnection() {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            G.handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (timeDifference(latestConnectionTryTiming)) {
                        allowForReconnecting = true;
                        waitingForReconnecting = false;
                        reconnect(false);
                    } else {
                        checkSocketConnection();
                    }
                }
            }, Config.REPEAT_CONNECTION_CHECKING);
        } else {
            /**
             when connecting was successful and user login ,
             in user login response will be change
             allowForReconnecting=true and waitingForReconnecting=false
             for allow reconnecting later if need
             */
        }
    }

    /**
     * compute time difference between latest try for connecting to socket and current time
     *
     * @param beforeTime when try for connect to socket (currentTimeMillis)
     * @return return true if difference is bigger than 10 second
     */

    private static boolean timeDifference(long beforeTime) {
        long difference;
        long currentTime = System.currentTimeMillis();
        difference = (currentTime - beforeTime);

        return difference >= ALLOW_RECONNECT_AGAIN_NORMAL;
    }

    public static boolean checkConnection() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            return true;
        }
        /**
         * set allowForReconnecting = true; for allow that to reconnecting
         */
        waitingForReconnecting = false;
        allowForReconnecting = true;
        reconnect(false);
        return false;
    }

    /**
     * role back main data for preparation for reconnecting to socket
     */

    private static void resetWebsocketInfo() {
        count = 0;
        G.canRunReceiver = true;
        G.symmetricKey = null;
        webSocketClient = null;
        /**
         * when secure is false set useMask true otherwise set false
         */
        G.isSecure = false;
        WebSocket.useMask = true;
        G.userLogin = false;
    }

    /**
     * if not secure yet send fake message to server for securing preparation
     */

    private static void checkFirstResponse() {

        Thread thread = new Thread(new Runnable() {
            @Override public void run() {

                while (G.symmetricKey == null && G.socketConnection) {

                    if (count < 3) {
                        count++;
                        try {
                            Thread.sleep(Config.FAKE_PM_DELAY);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        G.handler.post(new Runnable() {
                            @Override public void run() {
                                if (G.symmetricKey == null && G.socketConnection) {
                                    WebSocket webSocket = WebSocketClient.getInstance();
                                    if (webSocket != null) {
                                        webSocket.sendText("i need 30001");
                                    }
                                }
                            }
                        });
                    } else {
                        G.allowForConnect = false;
                        WebSocket webSocket = WebSocketClient.getInstance();
                        if (webSocket != null) {
                            webSocket.disconnect();
                        }
                    }
                }
            }
        });
        thread.start();
    }
}
