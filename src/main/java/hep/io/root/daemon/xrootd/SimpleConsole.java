package hep.io.root.daemon.xrootd;

import hep.io.root.daemon.xrootd.StatOperation.FileStatus;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;
import jline.ConsoleReader;
import jline.History;
import jline.Completor;

/**
 * A simple command line interface to xrootd
 * @author tonyj
 */
public class SimpleConsole {

    @Option(metaVar="host", name = "-h", usage = "Host to connect to")
    private String host;
    @Option(metaVar="port", name = "-p", usage = "Port to connect to")
    private int port = 1094;
    @Option(metaVar="level", name = "-l", usage = "Logging level")
    private String level;
    @Argument
    private List<String> arguments = new ArrayList<String>();
    private Session session;
    private static Map<String, Command> commandMap = new TreeMap<String, Command>();
    

    static {
        commandMap.put("open", new OpenCommand());
        commandMap.put("close", new CloseCommand());
        commandMap.put("ping", new PingCommand());
        commandMap.put("locate", new LocateCommand());
        commandMap.put("level", new LevelCommand());
        commandMap.put("stat", new StatCommand());
        commandMap.put("exit", new ExitCommand());
        commandMap.put("dirList", new DirListCommand());
        commandMap.put("checksum", new ChecksumCommand());
        commandMap.put("get", new GetCommand());
        commandMap.put("connect", new ConnectCommand());
        commandMap.put("disconnect", new DisconnectCommand());
        commandMap.put("protocol", new ProtocolCommand());
    }

    public static void main(String[] args) throws IOException {
        new SimpleConsole().doMain(args);
    }

    private void doMain(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            // parse the arguments.
            parser.parseArgument(args);

            if (host != null) {
                session = new Session(host, port, System.getProperty("user.name"));
            }
            if (level != null) {
                setLoggingLevel(level);
            }
            if (!arguments.isEmpty()) {
                handleCommand(arguments, this, new PrintWriter(System.out, true));
            } else {
                for (;;) {
                    try {
                        ConsoleReader console = new ConsoleReader();
                        File historyDir = new File(new File(System.getProperty("user.home")),".scalla");
                        historyDir.mkdir();
                        File historyFile = new File(historyDir,"command.history");
                        History history = historyDir.canWrite() ? new History(historyFile) : new History();
                        console.setHistory(history);
                        console.addCompletor(new CommandCompletor());

                        String line = console.readLine(String.format("scalla%s>", session == null ? "" : "(" + session + ")"));
                        if (line == null) {
                            console.printNewline();
                            console.flushConsole();
                            break;
                        }
                        if (line.trim().length() == 0) {
                            continue;
                        }
                        String[] tokens = line.trim().split("\\s+");
                        handleCommand(Arrays.asList(tokens), this, new PrintWriter(System.out,true));

                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }
        } catch (CmdLineException e) {
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            System.err.printf("java %s [options...] arguments...\n", SimpleConsole.class.getName());
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.printf("  Example: java %s %s\n", SimpleConsole.class.getName(), parser.printExample(ExampleMode.ALL));

            return;
        }
    }

    private Session getSession() {
        return session;
    }

    private void setSession(Session newSession) throws IOException {
        if (session != null) {
            session.close();
        }
        session = newSession;
    }

    private void handleCommand(List<String> tokens, SimpleConsole session, PrintWriter console) throws SecurityException, IOException, IllegalArgumentException {
        String commandName = tokens.get(0);

        if ("help".equals(commandName)) {
            for (Map.Entry<String, Command> entry : commandMap.entrySet()) {
                console.printf("%s %s\n", entry.getKey(), entry.getValue().printExample());
            }
        } else {
            Command command = commandMap.get(commandName);
            if (command == null) {
                console.printf("Unknown command: %s\n", commandName);
            } else {
                command.doCommand(commandName, tokens.subList(1, tokens.size()), session, console);
            }

        }
    }

    private void setLoggingLevel(String token) throws IllegalArgumentException, SecurityException {
        Level logLevel = Level.parse(token);
        Logger.getLogger("").setLevel(logLevel);
        Logger.getLogger("").getHandlers()[0].setLevel(logLevel);
    }

    private String getLoggingLevel() {
        return Logger.getLogger("").getLevel().getName();
    }
    
    static class CommandCompletor implements Completor {

        public int complete(String buffer, int position, List candidates)
        {
            if (buffer.contains(" ")) return 0;
            else
            {
                for (String command : commandMap.keySet()) {
                    if (command.startsWith(buffer)) candidates.add(command+" ");
                }
                return 0;
            }
        }
    }

    static abstract class Command {

        @Option(metaVar="level", name = "-l", usage = "Set logging level for this command")
        private String level;
        private SimpleConsole simpleConsole;

        Session getSession() {
            if (simpleConsole.getSession() == null) {
                throw new RuntimeException("No session");
            }
            return simpleConsole.getSession();
        }

        void setLoggingLevel(String level) {
            simpleConsole.setLoggingLevel(level);
        }

        void setSession(Session session) throws IOException {
            simpleConsole.setSession(session);
        }

        String printExample() {
            return new CmdLineParser(this).printExample(ExampleMode.ALL);
        }

        abstract void doCommand(PrintWriter console) throws IOException;

        void doCommand(String command, List<String> args, SimpleConsole session, PrintWriter console) throws IOException {
            level = null;
            CmdLineParser parser = new CmdLineParser(this);
            try {
                // parse the arguments.
                parser.parseArgument(args.toArray(new String[args.size()]));
                this.simpleConsole = session;
                if (level != null) {
                    String oldLevel = simpleConsole.getLoggingLevel();
                    try {
                        setLoggingLevel(level);
                        doCommand(console);
                    } finally {
                        setLoggingLevel(oldLevel);
                    }
                } else {
                    doCommand(console);
                }
            } catch (CmdLineException e) {
                // if there's a problem in the command line,
                // you'll get this exception. this will report
                // an error message.
                System.err.println(e.getMessage());
                System.err.printf("%s [options...] arguments...\n", command);
                // print the list of available options
                parser.printUsage(System.err);
                System.err.println();

                // print option sample. This is useful some time
                System.err.printf("  Example: %s %s\n", command, parser.printExample(ExampleMode.ALL));

            }
        }
    }

     static class OpenCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to open")
         private String path;

         void doCommand(PrintWriter console) throws IOException {
             int handle = getSession().open(path, 0, XrootdProtocol.kXR_open_read);
             console.printf("file handle=%d\n", handle);
         }
     }

     static class CloseCommand extends Command {

         @Argument(metaVar="handle", index = 0, required = true, usage = "Handle to close")
         private int handle;

         void doCommand(PrintWriter console) throws IOException {
             getSession().close(handle);
         }
     }

     static class PingCommand extends Command {

         void doCommand(PrintWriter console) throws IOException {
             getSession().ping();
         }
     }

     static class ExitCommand extends Command {

         void doCommand(PrintWriter console) throws IOException {
             System.exit(0);
         }
     }

     static class StatCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to file")
         private String path;

         void doCommand(PrintWriter console) throws IOException {
             FileStatus status = getSession().stat(path);
             console.printf("%s\n", status);
         }
     }

     static class DirListCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to directory")
         private String path;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             List<String> list = getSession().dirList(path);
             for (String file : list) {
                 console.printf("%s\n", file);
             }
         }
     }

     static class LevelCommand extends Command {

         @Argument(metaVar="level", index = 0, required = true, usage = "Logging level")
         private String level;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             setLoggingLevel(level);
         }
     }

     static class LocateCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to locate")
         private String path;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             String[] result = getSession().locate(path, false, false);
             for (String file : result) {
                 console.printf("%s\n", file);
             }
         }
     }

     static class ChecksumCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to file")
         private String path;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             String checksum = getSession().query(XrootdProtocol.kXR_Qcksum, path);
             console.printf("%s\n", checksum);
         }
     }

     static class ConnectCommand extends Command {

         @Argument(metaVar="host", index = 0, required = true, usage = "Host to connect to")
         private String host;
         @Option(name = "-p", usage = "Port to connect to")
         private int port = 1094;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             Session session = new Session(host, port, System.getProperty("user.name"));
             setSession(session);
         }
     }

     static class DisconnectCommand extends Command {

         @Override
         void doCommand( PrintWriter console) throws IOException {
             setSession(null);
         }
     }
     
     static class ProtocolCommand extends Command {

        @Override
        void doCommand(PrintWriter console) throws IOException
        {
            String protocol = getSession().protocol();
            console.println(protocol);
        }         
     }

     static class GetCommand extends Command {

         @Argument(metaVar="path", index = 0, required = true, usage = "Path to file")
         private String path;

         @Override
         void doCommand( PrintWriter console) throws IOException {
             File file = new File(path);
             String local = file.getName();
             int handle = getSession().open(path, 0, XrootdProtocol.kXR_open_read);
             OutputStream out = new FileOutputStream(local);
             try {
                 byte[] buffer = new byte[65536];
                 int lTotal = 0;
                 for (;;) {
                     int l = getSession().read(handle, buffer, lTotal);
                     if (l <= 0) {
                         break;
                     }
                     out.write(buffer, 0, l);
                     lTotal += l;
                 }
             } finally {
                 getSession().close(handle);
                 out.close();
             }
         }
     }
        
}
