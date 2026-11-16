package io.github.rtx4o9o.discordpdf.core;

/**
 * An expected failure whose message is safe to show to the user.
 * The message is an i18n key (see {@code messages*.properties}) plus arguments,
 * so it never carries file contents, URLs or other user data into logs.
 */
public class UserFacingException extends RuntimeException {

    private final String key;
    private final transient Object[] args;

    public UserFacingException(String key, Object... args) {
        super(key, null, false, false);
        this.key = key;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args.clone();
    }
}
