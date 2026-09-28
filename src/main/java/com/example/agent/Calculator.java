package com.example.agent;

import java.util.regex.Pattern;

public class Calculator {

    private static final Pattern ALLOWED = Pattern.compile("^[0-9+\\-*/().\\s]+$");

    public static String evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "Ошибка: пустое выражение";
        }
        String expr = expression.trim();

        // Защита от инъекций: только математические символы
        if (!ALLOWED.matcher(expr).matches()) {
            return "Ошибка: недопустимые символы. Разрешены только цифры и + - * / ( )";
        }
        if (expr.length() > 200) {
            return "Ошибка: выражение слишком длинное";
        }

        try {
            double result = new Parser(expr).parse();
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result); // целое без .0
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "Ошибка вычисления: " + e.getMessage();
        }
    }

    /** Простой безопасный разбор выражения (рекурсивный спуск), без eval. */
    private static class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        double parse() {
            double v = parseExpression();
            if (pos < s.length()) throw new RuntimeException("неожиданный символ");
            return v;
        }

        private double parseExpression() { // сложение и вычитание
            double v = parseTerm();
            while (true) {
                skipSpaces();
                if (peek() == '+') { pos++; v += parseTerm(); }
                else if (peek() == '-') { pos++; v -= parseTerm(); }
                else break;
            }
            return v;
        }

        private double parseTerm() { // умножение и деление
            double v = parseFactor();
            while (true) {
                skipSpaces();
                if (peek() == '*') { pos++; v *= parseFactor(); }
                else if (peek() == '/') {
                    pos++;
                    double d = parseFactor();
                    if (d == 0) throw new RuntimeException("деление на ноль");
                    v /= d;
                } else break;
            }
            return v;
        }

        private double parseFactor() {
            skipSpaces();
            char c = peek();
            if (c == '(') {
                pos++;
                double v = parseExpression();
                skipSpaces();
                if (peek() != ')') throw new RuntimeException("нет закрывающей скобки");
                pos++;
                return v;
            }
            if (c == '-') { pos++; return -parseFactor(); } // унарный минус
            if (c == '+') { pos++; return parseFactor(); }
            return parseNumber();
        }

        private double parseNumber() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new RuntimeException("ожидалось число");
            return Double.parseDouble(s.substring(start, pos));
        }

        private void skipSpaces() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }
    }
}
