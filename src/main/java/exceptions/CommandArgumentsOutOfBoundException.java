package exceptions;

public class CommandArgumentsOutOfBoundException extends Exception{
    public CommandArgumentsOutOfBoundException(int given, int callIndex) {
        super(String.format("Given command arguments can't reach given index, called: %d, given: %d", callIndex, given));
    }
}
