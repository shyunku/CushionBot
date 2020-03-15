package core;

import core.command.CommandManager;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class InternalEventListener extends ListenerAdapter {
    private final char startTag = '$';

    CommandManager commandManager = new CommandManager();

    public InternalEventListener() {
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        User user = e.getAuthor();
        Message msg = e.getMessage();
        String text = msg.getContentDisplay();

        if(user.isBot())return;
        if(text.length()==0)return;
        if(startTag != text.charAt(0))return;

        commandManager.parseCommand(e);
    }
}