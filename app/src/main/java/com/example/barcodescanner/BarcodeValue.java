package com.example.barcodescanner;

final class BarcodeValue {
    private BarcodeValue() {}

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim();
        if (!code.matches("[0-9]{8}|[0-9]{12,14}") || code.matches("0+")) {
            return null;
        }
        return code.length() == 12 ? "0" + code : code;
    }

    static String expandUpcE(String raw) {
        String code = normalize(raw);
        if (code == null || code.length() != 8 || (code.charAt(0) != '0' && code.charAt(0) != '1')) {
            return null;
        }
        char last = code.charAt(6);
        String body;
        switch (last) {
            case '0':
            case '1':
            case '2':
                body = code.substring(0, 3) + last + "0000" + code.substring(3, 6);
                break;
            case '3':
                body = code.substring(0, 4) + "00000" + code.substring(4, 6);
                break;
            case '4':
                body = code.substring(0, 5) + "00000" + code.charAt(5);
                break;
            default:
                body = code.substring(0, 6) + "0000" + last;
        }
        return "0" + body + code.charAt(7);
    }

    static boolean sameCode(String first, String second) {
        String a = identifier(first);
        return a != null && a.equals(identifier(second));
    }

    static String identifier(String value) {
        String code = normalize(value);
        return code == null ? null : code.replaceFirst("^0+", "");
    }
}
