package cn.nukkit.command;

import cn.nukkit.InterruptibleThread;
import cn.nukkit.Server;
import cn.nukkit.event.server.ServerCommandEvent;
import cn.nukkit.timings.Timings;
import jline.console.ConsoleReader;
import jline.console.CursorBuffer;

import java.io.IOException;

/**
 * author: MagicDroidX
 * Nukkit
 */
public class CommandReader extends Thread implements InterruptibleThread {

    private final ConsoleReader reader;

    public static CommandReader instance;

    private volatile CursorBuffer stashed;

    private boolean running = true;

    public static CommandReader getInstance() {
        if (instance == null) {
            instance = new CommandReader();
        }
        return instance;
    }

    public CommandReader() {
        if (instance != null) {
            throw new RuntimeException("Command Reader is already exist");
        }
        try {
            this.reader = new ConsoleReader();
            reader.setPrompt("> ");
            instance = this;
        } catch (IOException e) {
            Server.getInstance().getLogger().error("Unable to start Console Reader", e);
            throw new RuntimeException(e);
        }
        this.setName("Console");
    }

    public String readLine() {
        try {
            synchronized (reader) {
                reader.resetPromptLine("", "", 0);
            }
            return this.reader.readLine("> ");
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
            return "";
        }
    }

    public void run() {
        Long lastLine = System.currentTimeMillis();
        while (this.running) {
            if (Server.getInstance().getConsoleSender() == null || Server.getInstance().getPluginManager() == null) {
                continue;
            }

            String line = readLine();

            if (line != null && !line.trim().equals("")) {
                //todo 将即时执行指令改为每tick执行
                try {
                    Timings.serverCommandTimer.startTiming();
                    ServerCommandEvent event = new ServerCommandEvent(Server.getInstance().getConsoleSender(), line);
                    Server.getInstance().getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        Server.getInstance().dispatchCommand(event.getSender(), event.getCommand());
                    }
                    Timings.serverCommandTimer.stopTiming();
                } catch (Exception e) {
                    Server.getInstance().getLogger().logException(e);
                }

            } else if (System.currentTimeMillis() - lastLine <= 1) {
                try {
                    sleep(40);
                } catch (InterruptedException e) {
                    Server.getInstance().getLogger().logException(e);
                }
            }
            lastLine = System.currentTimeMillis();
        }
    }

    public void shutdown() {
        this.running = false;
    }

    public void stashLine() {
        try {
            synchronized (reader) {
                this.stashed = reader.getCursorBuffer().copy();
                reader.getOutput().write("\u001b[1G\u001b[K");
                reader.flush();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public void unstashLine() {
        try {
            synchronized (reader) {
                reader.resetPromptLine("> ", this.stashed == null ? "" : this.stashed.toString(), this.stashed == null ? 0 : this.stashed.cursor);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    public void removePromptLine() {
        try {
            synchronized (reader) {
                reader.resetPromptLine("", "", 0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
