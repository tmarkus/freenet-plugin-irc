package plugin.frirc;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Jircs: Java IRC Server. This is a simplistic IRC server. It's not designed
 * for production environments; I mainly wrote it to allow me to test out an IRC
 * bot I'm writing (http://jzbot.googlecode.com) when I'm not connected to the
 * internet. Every other dang IRC server takes a minute or so to try and look up
 * my hostname before realizing that I'm not even connected to the internet.
 * Jircs specifically doesn't do that.
 * 
 * @author Alexander Boyd
 * 
 */
public class Connection implements Runnable
{
    public static class Channel
    {
        private ArrayList<Connection> channelMembers = new ArrayList<Connection>();
        private String topic;
        protected String name;
        
        public void sendNot(Connection not, String toSend)
        {
            synchronized (mutex)
            {
                for (Connection con : channelMembers)
                {
                    if (con != not)
                        con.send(toSend);
                }
            }
        }
        
        public void send(String toSend)
        {
            sendNot(null, toSend);
        }
        
        public void memberQuit(String nick)
        {
            
        }
    }
    
    public static final Object mutex = new Object();
    private Socket socket;
    private String username;
    private String hostname;
    private String nick;
    private String description;
    public static Map<String, Connection> connectionMap = new HashMap<String, Connection>();
    public static Map<String, Channel> channelMap = new HashMap<String, Channel>();
    private static String globalServerName;
    
    public Connection(Socket socket)
    {
        this.socket = socket;
    }
    
    public String getRepresentation()
    {
        return nick + "!" + username + "@" + hostname;
    }
    
    /**
     * @param args
     */
    public static void main(String[] args) throws Throwable
    {
        if (args.length == 0)
        {
            System.out.println("Usage: java jw.jircs.Connection <servername>");
            return;
        }
        globalServerName = args[0];
        ServerSocket ss = new ServerSocket(6667);
        while (true)
        {
            Socket s = ss.accept();
            Connection jircs = new Connection(s);
            Thread thread = new Thread(jircs);
            thread.start();
        }
    }
    
    public enum Command
    {
        NICK(1, 1)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                if (con.nick == null)
                    doFirstTimeNick(con, arguments[0]);
                else
                    doSelfSwitchNick(con, arguments[0]);
            }
            
            private void doSelfSwitchNick(Connection con, String nick)
            {
                synchronized (mutex)
                {
                    String oldNick = con.nick;
                    con.nick = filterAllowedNick(nick);
                    connectionMap.remove(oldNick);
                    connectionMap.put(con.nick, con);
                    con.send(":" + oldNick + "!" + con.username + "@"
                            + con.hostname + " NICK :" + con.nick);
                    /*
                     * Now we need to notify all channels that we are on
                     */
                    for (Channel c : channelMap.values())
                    {
                        if (c.channelMembers.contains(con))
                            c
                                    .sendNot(con, ":" + oldNick + "!"
                                            + con.username + "@" + con.hostname
                                            + " NICK :" + con.nick);
                    }
                }
            }
            
            private void doFirstTimeNick(Connection con, String nick)
                    throws InterruptedException
            {
                con.nick = filterAllowedNick(nick);
                synchronized (mutex)
                {
                    connectionMap.put(con.nick, con);
                }
                /*
                 * Now we send the user a welcome message and everything.
                 */
            }
            
        },
        USER(1, 4)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                if (con.username != null)
                {
                    con.send("NOTICE AUTH :You can't change your user "
                            + "information after you've logged in right now.");
                    return;
                }
                con.username = arguments[0];
                String forDescription = arguments.length > 3 ? arguments[3]
                        : "(no description)";
                con.description = forDescription;
                /*
                 * Now we'll send the user their initial information.
                 */
                con.sendGlobal("001 " + con.nick + " :Welcome to "
                        + globalServerName + ", a Jircs-powered IRC network.");
                con.sendGlobal("004 " + con.nick + " " + globalServerName
                        + " Jircs");
                con.sendGlobal("375 " + con.nick + " :- " + globalServerName
                        + " Message of the Day -");
                con.sendGlobal("372 " + con.nick + " :- Hello. Welcome to "
                        + globalServerName + ", a Jircs-powered IRC network.");
                con
                        .sendGlobal("372 "
                                + con.nick
                                + " :- See http://code.google.com/p/jwutils/wiki/Jircs "
                                + "for more info on Jircs.");
                con.sendGlobal("376 " + con.nick + " :End of /MOTD command.");
            }
        },
        PING(1, 1)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                con.send(":" + globalServerName + " PONG " + globalServerName
                        + " :" + arguments[0]);
            }
        },
        JOIN(1, 2)
        {
            
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                if (arguments.length == 2)
                {
                    con.sendSelfNotice("This server does not support "
                            + "channel keys at "
                            + "this time. JOIN will act as if you "
                            + "hadn't specified any keys.");
                }
                String[] channelNames = arguments[0].split(",");
                for (String channelName : channelNames)
                {
                    if (!channelName.startsWith("#"))
                    {
                        con.sendSelfNotice("This server only allows "
                                + "channel names that "
                                + "start with a # sign.");
                        return;
                    }
                    if (channelName.contains(" "))
                    {
                        con.sendSelfNotice("This server does not allow spaces "
                                + "in channel names.");
                        return;
                    }
                }
                for (String channelName : channelNames)
                {
                    doJoin(con, channelName);
                }
            }
            
            public void doJoin(Connection con, String channelName)
            {
                if (!channelName.startsWith("#"))
                {
                    con
                            .sendSelfNotice("This server only allows channel names that "
                                    + "start with a # sign.");
                    return;
                }
                if (channelName.contains(" "))
                {
                    con
                            .sendSelfNotice("This server does not allow spaces in channel names.");
                }
                synchronized (mutex)
                {
                    Channel channel = channelMap.get(channelName);
                    boolean added = false;
                    if (channel == null)
                    {
                        added = true;
                        channel = new Channel();
                        channel.name = channelName;
                        channelMap.put(channelName, channel);
                    }
                    if (channel.channelMembers.contains(con))
                    {
                        con.sendSelfNotice("You're already a member of "
                                + channelName);
                        return;
                    }
                    channel.channelMembers.add(con);
                    channel.send(":" + con.getRepresentation() + " JOIN "
                            + channelName);
                    if (added)
                        con.sendGlobal("MODE " + channelName + " +nt");
                    // This is commented out because channel.send takes care of
                    // this for us
                    // con.send(":" + con.getRepresentation() + " JOIN "
                    // + channelName);
                    if (channel.topic != null)
                        con.sendGlobal("332 " + con.nick + " " + channel.name
                                + " :" + channel.topic);
                    else
                        con.sendGlobal("331 " + con.nick + " " + channel.name
                                + " :No topic is set");
                    for (Connection channelMember : channel.channelMembers)
                    {// 353,366
                        con.sendGlobal("353 " + con.nick + " = " + channelName
                                + " :" + channelMember.nick);
                    }
                    con.sendGlobal("366 " + con.nick + " " + channelName
                            + " :End of /NAMES list");
                }
            }
        },
        WHO(0, 2)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                if (arguments.length > 1)
                    con
                            .sendSelfNotice("Filtering by operator only using the WHO "
                                    + "command isn't yet supported. WHO will act "
                                    + "as if \"o\" has not been specified.");
                String person = "";
                if (arguments.length > 0)
                    person = arguments[0];
                synchronized (mutex)
                {
                    Channel channel = channelMap.get(person);
                    if (channel != null)
                    {
                        for (Connection channelMember : channel.channelMembers)
                        {
                            con.sendGlobal("352 " + con.nick + " " + person
                                    + " " + channelMember.username + " " + channelMember.hostname
                                    + " " + globalServerName + " " + channelMember.nick
                                    + " H :0 " + channelMember.description);
                        }
                    }
                    else
                    {
                        con
                                .sendSelfNotice("WHO with something other than a channel "
                                        + "as arguments is not supported right now. "
                                        + "WHO will display an empty list of people.");
                    }
                }
                con.send("315 " + con.nick + " " + person
                        + " :End of /WHO list.");
            }
        },
        USERHOST(1, 5)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                ArrayList<String> replies = new ArrayList<String>();
                for (String s : arguments)
                {
                    Connection user = connectionMap.get(s);
                    if (user != null)
                        replies.add(user.nick + "=+" + user.username + "@"
                                + user.hostname);
                }
                con.sendGlobal("302 " + con.nick + " :"
                        + delimited(replies.toArray(new String[0]), " "));
            }
        },
        MODE(0, 2)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                if (arguments.length == 1)
                {
                    if (arguments[0].startsWith("#"))
                    {
                        con.sendGlobal("324 " + con.nick + " " + arguments[0]
                                + " +nt");
                    }
                    else
                    {
                        con
                                .sendSelfNotice("User mode querying not supported yet.");
                    }
                }
                else if (arguments.length == 2
                        && (arguments[1].equals("+b") || arguments[1]
                                .equals("+e")))
                {
                    if (arguments[0].startsWith("#"))
                    {// 368,349
                        if (arguments[1].equals("+b"))
                        {
                            con.sendGlobal("368 " + con.nick + " "
                                    + arguments[0]
                                    + " :End of channel ban list");
                        }
                        else
                        {
                            con.sendGlobal("349 " + con.nick + " "
                                    + arguments[0]
                                    + " :End of channel exception list");
                        }
                    }
                    else
                    {
                        con
                                .sendSelfNotice("User mode setting not supported yet for +b or +e.");
                    }
                }
                else
                {
                    con.sendSelfNotice("Specific modes not supported yet.");
                }
            }
        },
        PART(1, 2)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                String[] channels = arguments[0].split(",");
                for (String channelName : channels)
                {
                    synchronized (mutex)
                    {
                        Channel channel = channelMap.get(channelName);
                        if (channelName == null)
                            con
                                    .sendSelfNotice("You're not a member of the channel "
                                            + channelName
                                            + ", so you can't part it.");
                        else
                        {
                            channel.send(":" + con.getRepresentation()
                                    + " PART " + channelName);
                            channel.channelMembers.remove(con);
                            if (channel.channelMembers.size() == 0)
                                channelMap.remove(channelName);
                        }
                    }
                }
            }
        },
        QUIT(1, 1)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                con.sendQuit("Quit: " + arguments[0]);
            }
        },
        PRIVMSG(2, 2)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                String[] recipients = arguments[0].split(",");
                String message = arguments[1];
                for (String recipient : recipients)
                {
                    if (recipient.startsWith("#"))
                    {
                        Channel channel = channelMap.get(recipient);
                        if (channel == null)
                            con
                                    .sendSelfNotice("No such channel, so can't send "
                                            + "a message to it: " + recipient);
                        else if (!channel.channelMembers.contains(con))
                            con.sendSelfNotice("You can't send messages to "
                                    + "channels you're not at.");
                        else
                            channel.sendNot(con, ":" + con.getRepresentation()
                                    + " PRIVMSG " + recipient + " :" + message);
                    }
                    else
                    {
                        Connection recipientConnection = connectionMap
                                .get(recipient);
                        if (recipientConnection == null)
                            con.sendSelfNotice("The user " + recipient
                                    + " is not online.");
                        else
                            recipientConnection.send(":"
                                    + con.getRepresentation() + " PRIVMSG "
                                    + recipient + " :" + message);
                    }
                }
            }
        },
        TOPIC(1, 2)
        {
            @Override
            public void run(Connection con, String prefix, String[] arguments)
                    throws Exception
            {
                Channel channel = channelMap.get(arguments[0]);
                if (channel == null)
                {
                    con.sendSelfNotice("No such channel for topic viewing: "
                            + arguments[0]);
                    return;
                }
                if (arguments.length == 1)
                {
                    /*
                     * The user wants to see the channel topic.
                     */
                    if (channel.topic != null)
                        con.sendGlobal("332 " + con.nick + " " + channel.name
                                + " :" + channel.topic);
                    else
                        con.sendGlobal("331 " + con.nick + " " + channel.name
                                + " :No topic is set");
                }
                else
                {
                    /*
                     * The user wants to set the channel topic.
                     */
                    channel.topic = arguments[1];
                    channel.sendNot(con, ":" + con.getRepresentation()
                            + " TOPIC " + channel.name + " :" + channel.topic);
                }
            }
        };
        private int minArgumentCount;
        private int maxArgumentCount;
        
        private Command(int min, int max)
        {
            minArgumentCount = min;
            maxArgumentCount = max;
        }
        
        public int getMin()
        {
            return minArgumentCount;
        }
        
        public int getMax()
        {
            return maxArgumentCount;
        }
        
        public abstract void run(Connection con, String prefix,
                String[] arguments) throws Exception;
    }
    
    public static String delimited(String[] items, String delimiter)
    {
        StringBuffer response = new StringBuffer();
        boolean first = true;
        for (String s : items)
        {
            if (first)
                first = false;
            else
                response.append(delimiter);
            response.append(s);
        }
        return response.toString();
    }
    
    protected void sendQuit(String quitMessage)
    {
        synchronized (mutex)
        {
            for (String channelName : new ArrayList<String>(channelMap.keySet()))
            {
                Channel channel = channelMap.get(channelName);
                channel.channelMembers.remove(this);
                channel.send(":" + getRepresentation() + " QUIT :"
                        + quitMessage);
                if (channel.channelMembers.size() == 0)
                    channelMap.remove(channel.name);
            }
        }
    }
    
    @Override
    public void run()
    {
        try
        {
            doServer();
        }
        catch (Exception e)
        {
            try
            {
                socket.close();
            }
            catch (Exception e2)
            {
            }
            e.printStackTrace();
        }
        finally
        {
            if (nick != null && connectionMap.get(nick) == this)
            {
                sendQuit("Client disconnected");
            }
        }
    }
    
    protected void sendGlobal(String string)
    {
        send(":" + globalServerName + " " + string);
    }
    
    private LinkedBlockingQueue<String> outQueue = new LinkedBlockingQueue<String>(
            1000);
    
    private Thread outThread = new Thread()
    {
        public void run()
        {
            try
            {
                OutputStream out = socket.getOutputStream();
                while (true)
                {
                    String s = outQueue.take();
                    s = s.replace("\n", "").replace("\r", "");
                    s = s + "\r\n";
                    out.write(s.getBytes());
                    out.flush();
                }
            }
            catch (Exception e)
            {
                System.out.println("Outqueue died");
                outQueue.clear();
                outQueue = null;
                e.printStackTrace();
                try
                {
                    socket.close();
                }
                catch (Exception e2)
                {
                    e2.printStackTrace();
                }
            }
        }
    };
    
    private void doServer() throws Exception
    {
        InetSocketAddress address = (InetSocketAddress) socket
                .getRemoteSocketAddress();
        hostname = address.getAddress().getHostAddress();
        System.out.println("Connection from host " + hostname);
        outThread.start();
        InputStream socketIn = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                socketIn));
        String line;
        while ((line = reader.readLine()) != null)
        {
            processLine(line);
        }
        
    }
    
    private void processLine(String line) throws Exception
    {
        System.out.println("Processing line from " + nick + ": " + line);
        String prefix = "";
        if (line.startsWith(":"))
        {
            String[] tokens = line.split(" ", 2);
            prefix = tokens[0];
            line = (tokens.length > 1 ? tokens[1] : "");
        }
        String[] tokens1 = line.split(" ", 2);
        String command = tokens1[0];
        line = tokens1.length > 1 ? tokens1[1] : "";
        String[] tokens2 = line.split("(^| )\\:", 2);
        String trailing = null;
        line = tokens2[0];
        if (tokens2.length > 1)
            trailing = tokens2[1];
        ArrayList<String> argumentList = new ArrayList<String>();
        if (!line.equals(""))
            argumentList.addAll(Arrays.asList(line.split(" ")));
        if (trailing != null)
            argumentList.add(trailing);
        String[] arguments = argumentList.toArray(new String[0]);
        /*
         * Now we actually process the command.
         */
        if (command.matches("[0-9][0-9][0-9]"))
            command = "n" + command;
        Command commandObject = null;
        try
        {
            Command.valueOf(command.toLowerCase());
        }
        catch (Exception e)
        {
        }
        if (commandObject == null)
        {
            try
            {
                commandObject = Command.valueOf(command.toUpperCase());
            }
            catch (Exception e)
            {
            }
        }
        if (commandObject == null)
        {
            sendSelfNotice("That command (" + command
                    + ") isnt a supported command at this server.");
            return;
        }
        if (arguments.length < commandObject.getMin()
                || arguments.length > commandObject.getMax())
        {
            sendSelfNotice("Invalid number of arguments for this"
                    + " command, expected not more than "
                    + commandObject.getMax() + " and not less than "
                    + commandObject.getMin() + " but got " + arguments.length
                    + " arguments");
            return;
        }
        commandObject.run(this, prefix, arguments);
    }
    
    /**
     * Sends a notice from the server to the user represented by this
     * connection.
     * 
     * @param string
     *            The text to send as a notice
     */
    private void sendSelfNotice(String string)
    {
        send(":" + globalServerName + " NOTICE " + nick + " :" + string);
    }
    
    public static String filterAllowedNick(String theNick)
    {
        return theNick.replace(":", "").replace(" ", "").replace("!", "")
                .replace("@", "").replace("#", "");
    }
    
    private String[] padSplit(String line, String regex, int max)
    {
        String[] split = line.split(regex);
        String[] output = new String[max];
        for (int i = 0; i < output.length; i++)
        {
            output[i] = "";
        }
        for (int i = 0; i < split.length; i++)
        {
            output[i] = split[i];
        }
        return output;
    }
    
    public void send(String s)
    {
        Queue<String> testQueue = outQueue;
        if (testQueue != null)
        {
            System.out.println("Sending line to " + nick + ": " + s);
            testQueue.add(s);
        }
    }
}