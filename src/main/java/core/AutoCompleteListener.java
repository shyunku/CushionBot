package core;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AutoCompleteListener extends ListenerAdapter {
    public static final List<String> gameWords = new ArrayList<>();

    static {
        gameWords.add("리그오브레전드");
        gameWords.add("롤");
        gameWords.add("발로란트");
        gameWords.add("발로");
        gameWords.add("오버워치");
        gameWords.add("마인크래프트");
        gameWords.add("마크");
        gameWords.add("로아");
        gameWords.add("로스트아크");
        gameWords.add("엘든링");
        gameWords.add("근든링");
        gameWords.add("GTA");
        gameWords.add("그타");
        gameWords.add("구스구스덕");
        gameWords.add("구구덕");
        gameWords.add("스타크래프트");
        gameWords.add("스타");
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        String interactionId = event.getInteraction().getId();
        String focusedOption = event.getFocusedOption().getName();
        switch (interactionId) {
            case "recruitModal":
                switch (focusedOption) {
                    case "gameName":
                        this.replyWithChoices(event, gameWords);
                }
                break;
        }
    }

    private void replyWithChoices(CommandAutoCompleteInteractionEvent event, List<String> words) {
        List<Command.Choice> options = Stream.of(words)
                .filter(word -> word.contains(event.getFocusedOption().getValue())) // only display words that start with the user's current input
                .map(word -> new Command.Choice(word.toString(), String.valueOf(word))) // map the words to choices
                .collect(Collectors.toList());
        event.replyChoices(options).queue();
    }
}