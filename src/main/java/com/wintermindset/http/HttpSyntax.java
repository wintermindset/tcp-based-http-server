package com.wintermindset.http;

final class HttpSyntax {
    private HttpSyntax() {}

    static boolean token(String value) {
        return !value.isEmpty() && value.chars().allMatch(c ->
                c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                        || "!#$%&'*+-.^_`|~".indexOf(c) >= 0);
    }

    static boolean fieldValue(String value) {
        return value.chars().allMatch(c -> c == '\t' || c >= 32 && c < 127);
    }
}
