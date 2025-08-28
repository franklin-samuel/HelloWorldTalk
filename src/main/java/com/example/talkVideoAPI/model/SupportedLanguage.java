package com.example.talkVideoAPI.model;

public enum SupportedLanguage {
    EN("en", "Inglês"),
    ES("es", "Espanhol"),
    PT("pt", "Português"),
    FR("fr", "Francês"),
    DE("de", "Alemão"),
    IT("it", "Italiano"),
    RU("ru", "Russo"),
    JA("ja", "Japonês"),
    ZH("zh", "Chinês");

    private final String code;
    private final String displayName;

    SupportedLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static boolean isValid(String code) {
        for (SupportedLanguage lang : values()) {
            if (lang.getCode().equalsIgnoreCase(code)) return true;
        }
        return false;
    }
}