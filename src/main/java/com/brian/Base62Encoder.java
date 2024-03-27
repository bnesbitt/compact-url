package com.brian;

import java.security.SecureRandom;
import java.util.Random;

/**
 * A Simple encoder that generates a random string with 6 characters.
 * The user is responsible for checking that the string is unique. This
 * could involve calling the encode method repeatedly until a unique
 * hash is obtained.
 */
public class Base62Encoder implements URLEncoder {
    private static final int URL_LEN = 6;
    private static final String randChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";

    private final Random rand = new SecureRandom();

    @Override
    public String encode(String notUsed) {
        char[] shortURL = new char[URL_LEN];

        for(int i = 0; i < URL_LEN; i++ ) {
            shortURL[i] = randChars.charAt(rand.nextInt(randChars.length()));
        }

        return new String(shortURL);
    }
}
