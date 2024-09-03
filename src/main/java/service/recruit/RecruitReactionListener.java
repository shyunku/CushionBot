package service.recruit;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RecruitReactionListener extends ListenerAdapter {
    private final Message message;

    public RecruitReactionListener(Message message) {
        this.message = message;
    }


}
