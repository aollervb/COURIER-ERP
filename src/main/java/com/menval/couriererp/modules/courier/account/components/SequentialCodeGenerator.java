package com.menval.couriererp.modules.courier.account.components;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SequentialCodeGenerator implements AccountCodeGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        // TODO: Change account code <CR> to be configurable by the customer
        return "CR-" + randomPart(6);
    }

    private String randomPart(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
