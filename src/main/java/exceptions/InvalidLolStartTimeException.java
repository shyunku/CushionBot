package exceptions;

public class InvalidLolStartTimeException extends Exception {
    public InvalidLolStartTimeException() {
    }

    public InvalidLolStartTimeException(String message) {
        super(message);
    }
}
