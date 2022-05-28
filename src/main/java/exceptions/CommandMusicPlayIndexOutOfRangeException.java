package exceptions;

public class CommandMusicPlayIndexOutOfRangeException extends Exception{
    public CommandMusicPlayIndexOutOfRangeException() {
        super(String.format("Given argument index out of range, supported: 1 ~ 5"));
    }
}
