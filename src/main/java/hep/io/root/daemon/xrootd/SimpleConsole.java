package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.StatOperation.FileStatus;
import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author tonyj
 */
public class SimpleConsole {

    public static void main(String[] args) {
        Console console = System.console();
        Session session = null;
        if (console == null) {
            System.out.println("No console available");
            System.exit(1);
        }
        for (;;) {
            try {
                String line = console.readLine("scalla%s>", session == null ? "" : "(" + session + ")");
                if (line == null) {
                    break;
                }
                if (line.trim().length() == 0) {
                    continue;
                }
                String[] tokens = line.trim().split("\\s+");
                String command = tokens[0];
                if ("exit".equals(command)) {
                    System.exit(0);
                } else if ("connect".equals(command)) {
                    if (session != null) {
                        session.close();
                    }
                    session = new Session(tokens[1], Integer.parseInt(tokens[2]), System.getProperty("user.name"));
                } else if ("disconnect".equals(command)) {
                    session.close();
                    session = null;
                } else if ("ping".equals(command)) {
                    session.ping();
                } else if ("stat".equals(command)) {
                    FileStatus status = session.stat(tokens[1]);
                    console.printf("%s\n", status);
                } else if ("dirList".equals(command)) {
                    List<String> list = session.dirList(tokens[1]);
                    for (String file : list) {
                        console.printf("%s\n", file);
                    }
                } else if ("query".equals(command)) {
                    String checksum = session.query(XrootdProtocol.kXR_Qcksum, tokens[1]);
                    console.printf("%s\n", checksum);
                } else if ("level".equals(command)) {
                    Level level = Level.parse(tokens[1]);
                    Logger.getLogger("").setLevel(level);
                    Logger.getLogger("").getHandlers()[0].setLevel(level);
                } else if ("get".equals(command)) {
                    File file = new File(tokens[1]);
                    String local = file.getName();
                    int handle = session.open(tokens[1], 0, XrootdProtocol.kXR_open_read);
                    OutputStream out = new FileOutputStream(local);
                    try {
                        byte[] buffer = new byte[65536];
                        int lTotal = 0;
                        for (;;) {
                            int l = session.read(handle, buffer, lTotal);
                            if (l <= 0) {
                                break;
                            }
                            out.write(buffer, 0, l);
                            lTotal += l;
                        }
                    } finally {
                        session.close(handle);
                        out.close();
                    }
                } else if ("locate".equals(command)) {
                    String[] result = session.locate(tokens[1], false, false);
                    for (String file : result) {
                        console.printf("%s\n", file);
                    }
                } else if ("open".equals(command)) {
                    int handle = session.open(tokens[1], 0, XrootdProtocol.kXR_open_read);
                    console.printf("file handle=%d\n", handle);
                } else if ("close".equals(command)) {
                    session.close(Integer.parseInt(tokens[1]));
                } else {
                    console.printf("Unknown command: %s\n", command);
                }

            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }
}
