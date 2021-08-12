package core;

import Utilities.TextStyleManager;
import core.command.CommandManager;
import core.command.CommandParser;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.Scanner;

public class InternalEventListener extends ListenerAdapter {
    private CommandManager commandManager = new CommandManager();

    public InternalEventListener() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            CommandParser parser;
            while(true){
                String terminalCommand = scanner.nextLine();
                parser = new CommandParser(terminalCommand, true);

                String keyword = parser.getKeyword();
                String sentence = parser.getIntegratedString();

                switch(keyword){
                    case "adminify":
                        commandManager.privilege(sentence);
                        break;
                    case "clearwhite":
                        commandManager.resetWhiteList();
                        break;
                    default:
                        System.out.println("keyword: "+keyword+", content: "+sentence);
                        break;
                }
            }
        }).start();
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        System.out.println("ready!");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        commandManager.parseCommand(e);
    }

//    private void sendServerMessage(String str){
//        TextStyleManager styler = new TextStyleManager();
//        String printer = styler.toBold("[Server] "+str);
//
//        synchronized (commandManager){
//            commandManager.sendMessage(printer);
//        }
//    }
}