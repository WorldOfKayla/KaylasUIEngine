package org.takesome.kaylasEngine.utils;

import org.takesome.kaylasEngine.Engine;
import org.takesome.kaylasEngine.locale.LanguageProvider;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.StringTokenizer;

@SuppressWarnings("unused")
public class ServerInfo {
    private final LanguageProvider lang;


    private BufferedImage serverStatusImg;
    private int servtype = 2;

    public ServerInfo(Engine engine) {
        lang = engine.getLANG();
    }

    public String[] pollServer(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(2000);
            socket.setTcpNoDelay(true);
            socket.setTrafficClass(18);
            socket.connect(new InetSocketAddress(ip, port), 6000);

            try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                dos.write(254);

                if (dis.read() != 255) {
                    throw new IOException("Bad message");
                }

                String servc = readString(dis, 256).substring(3);
                servtype = servc.startsWith("§1") ? 1 : 2;

                return splitString(servc, "§");
            }
        } catch (Exception e) {
            return new String[]{null, null, null};
        }
    }

    private String[] splitString(String input, String delimiter) {
        StringTokenizer tokenizer = new StringTokenizer(input, delimiter);
        String[] resultArray = new String[tokenizer.countTokens()];
        int index = 0;
        while (tokenizer.hasMoreTokens()) {
            resultArray[index++] = tokenizer.nextToken();
        }
        return resultArray;
    }

    private String readString(DataInputStream is, int d) throws IOException {
        short word = is.readShort();
        if (word > d || word < 0) {
            throw new IOException();
        }
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < word; i++) {
            res.append(is.readChar());
        }
        return res.toString();
    }

    public String genServerStatus(String[] args) {
        if (args[0] == null && args[1] == null && args[2] == null) {
            return lang.getString(ServerStatus.SERVER_OFF.getStatusKey());
        } else {
            String serverName = args[servtype == 1 ? 4 : args.length - 2];
            String playerName = args[servtype == 1 ? 5 : args.length - 1];
            ServerStatus status = serverName.equals(playerName) ? ServerStatus.SERVER_OFF : ServerStatus.SERVER_ON;
            return lang.getString(status.getStatusKey()).replace("%%", serverName).replace("##", playerName);
        }
    }

    public BufferedImage genServerIcon(String[] args) {
        int imgIndex = args[0] == null && args[1] == null && args[2] == null ? 0 :
                args[1] != null && args[2] != null && !args[1].equals(args[2]) ? 2 : 1;
        return serverStatusImg.getSubimage(imgIndex * serverStatusImg.getHeight(), 0, serverStatusImg.getHeight(), serverStatusImg.getHeight());
    }

    enum ServerStatus {
        SERVER_OFF("server.serverOff"),
        SERVER_ON("server.serverOn"),
        SERVER_ERR("server.serverErr");

        private final String statusKey;

        ServerStatus(String statusKey) {
            this.statusKey = statusKey;
        }

        public String getStatusKey() {
            return statusKey;
        }
    }

    public void setServerStatusImg(BufferedImage serverStatusImg) {
        this.serverStatusImg = serverStatusImg;
    }
}