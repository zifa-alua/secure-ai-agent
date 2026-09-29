package com.example.agent;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

public class SqlTool {

    private static final String DB_PATH = "agent.db";

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|create|replace|attach|detach|pragma|vacuum|truncate)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Создаёт таблицы и наполняет тестовыми данными при первом запуске. */
    public static void init() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement st = conn.createStatement()) {

            st.executeUpdate("CREATE TABLE IF NOT EXISTS products (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, category TEXT, price REAL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS customers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, city TEXT)");

            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products");
            rs.next();
            if (rs.getInt(1) == 0) {
                st.executeUpdate("INSERT INTO products (name, category, price) VALUES " +
                        "('Ноутбук', 'Электроника', 450000)," +
                        "('Мышка', 'Электроника', 8000)," +
                        "('Клавиатура', 'Электроника', 15000)," +
                        "('Стол', 'Мебель', 60000)," +
                        "('Стул', 'Мебель', 25000)," +
                        "('Кофе', 'Продукты', 3500)");
                st.executeUpdate("INSERT INTO customers (name, city) VALUES " +
                        "('Айгерим', 'Алматы')," +
                        "('Данияр', 'Астана')," +
                        "('Мадина', 'Алматы')," +
                        "('Ерлан', 'Шымкент')");
            }
        } catch (SQLException e) {
            System.out.println("Ошибка инициализации БД: " + e.getMessage());
        }
    }

    /** Выполняет проверенный read-only SELECT и возвращает результат в виде текста. */
    public static String query(String sql) {
        if (sql == null || sql.isBlank()) return "Ошибка: пустой запрос";

        String q = sql.trim();
        if (q.endsWith(";")) q = q.substring(0, q.length() - 1).trim(); // один хвостовой ; допустим

        if (q.contains(";")) return "Ошибка: разрешён только один SELECT-запрос (без нескольких стейтментов)";
        if (!q.toLowerCase().startsWith("select")) return "Ошибка: разрешены только SELECT-запросы";
        if (FORBIDDEN.matcher(q).find()) return "Ошибка: запрос содержит запрещённую операцию";

        // Read-only соединение — защита на уровне драйвера
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH, config.toProperties());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(q)) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            StringBuilder sb = new StringBuilder();

            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(" | ");
                sb.append(meta.getColumnName(i));
            }
            sb.append("\n");

            int rows = 0;
            while (rs.next() && rows < 100) {
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) sb.append(" | ");
                    sb.append(rs.getString(i));
                }
                sb.append("\n");
                rows++;
            }
            if (rows == 0) return "Строк не найдено.";
            return sb.toString();
        } catch (SQLException e) {
            return "Ошибка выполнения запроса: " + e.getMessage();
        }
    }
}
