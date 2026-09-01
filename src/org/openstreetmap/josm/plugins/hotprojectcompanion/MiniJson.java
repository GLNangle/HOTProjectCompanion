package org.openstreetmap.josm.plugins.hotprojectcompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON reader for Tasking Manager API responses. */
final class MiniJson {
    private final String input;
    private int position;

    private MiniJson(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("JSON is null");
        }
        MiniJson parser = new MiniJson(input);
        Object result = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != input.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return result;
    }

    private Object readValue() {
        skipWhitespace();
        if (position >= input.length()) {
            throw error("Unexpected end of JSON");
        }
        char current = input.charAt(position);
        switch (current) {
        case '{':
            return readObject();
        case '[':
            return readArray();
        case '"':
            return readString();
        case 't':
            readLiteral("true");
            return Boolean.TRUE;
        case 'f':
            readLiteral("false");
            return Boolean.FALSE;
        case 'n':
            readLiteral("null");
            return null;
        default:
            if (current == '-' || Character.isDigit(current)) {
                return readNumber();
            }
            throw error("Unexpected character");
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (consume('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != '"') {
                throw error("Expected object key");
            }
            String key = readString();
            skipWhitespace();
            require(':');
            object.put(key, readValue());
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            require(',');
        }
    }

    private List<Object> readArray() {
        List<Object> array = new ArrayList<>();
        position++;
        skipWhitespace();
        if (consume(']')) {
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            require(',');
        }
    }

    private String readString() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char current = input.charAt(position++);
            if (current == '"') {
                return result.toString();
            }
            if (current != '\\') {
                if (current < 0x20) {
                    throw error("Control character in string");
                }
                result.append(current);
                continue;
            }
            if (position >= input.length()) {
                throw error("Unfinished escape sequence");
            }
            char escaped = input.charAt(position++);
            switch (escaped) {
            case '"':
            case '\\':
            case '/':
                result.append(escaped);
                break;
            case 'b':
                result.append('\b');
                break;
            case 'f':
                result.append('\f');
                break;
            case 'n':
                result.append('\n');
                break;
            case 'r':
                result.append('\r');
                break;
            case 't':
                result.append('\t');
                break;
            case 'u':
                result.append(readUnicodeEscape());
                break;
            default:
                throw error("Invalid escape sequence");
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (position + 4 > input.length()) {
            throw error("Incomplete unicode escape");
        }
        try {
            char value = (char) Integer.parseInt(input.substring(position, position + 4), 16);
            position += 4;
            return value;
        } catch (NumberFormatException exception) {
            throw error("Invalid unicode escape");
        }
    }

    private Number readNumber() {
        int start = position;
        if (input.charAt(position) == '-') {
            position++;
        }
        readDigits();
        boolean decimal = false;
        if (position < input.length() && input.charAt(position) == '.') {
            decimal = true;
            position++;
            readDigits();
        }
        if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
            decimal = true;
            position++;
            if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                position++;
            }
            readDigits();
        }
        String number = input.substring(start, position);
        try {
            if (decimal) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        int start = position;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            position++;
        }
        if (position == start) {
            throw error("Expected a digit");
        }
    }

    private void readLiteral(String literal) {
        if (!input.startsWith(literal, position)) {
            throw error("Invalid literal");
        }
        position += literal.length();
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position);
    }
}
