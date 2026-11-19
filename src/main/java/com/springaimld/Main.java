package com.springaimld;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Scanner;

/** Menu-driven inventory management application backed by MySQL. */
public class Main {
    private static final String URL = "jdbc:mysql://localhost:3306/retail_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Scanner scanner = new Scanner(System.in)) {
            createTable(connection);
            System.out.println("Connected to retail_db. Inventory table is ready.");
            runMenu(connection, scanner);
        } catch (SQLException e) {
            System.err.println("Could not connect to or initialize MySQL: " + e.getMessage());
            System.err.println("Check that MySQL is running and root/root credentials are correct.");
        }
    }

    private static void createTable(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS Product ("
                + "product_id INT PRIMARY KEY, "
                + "product_name VARCHAR(100), "
                + "category VARCHAR(50), "
                + "quantity INT, "
                + "price DOUBLE)";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void runMenu(Connection connection, Scanner scanner) {
        boolean running = true;
        while (running) {
            System.out.println("\n=== Inventory Management ===");
            System.out.println("1. Insert Product Records");
            System.out.println("2. Conditional Retrieval & Sorting");
            System.out.println("3. Update Product Stock");
            System.out.println("4. Search & Modify Product Details");
            System.out.println("5. Aggregate Query & Conditional Deletion");
            System.out.println("6. Exit");
            int choice = readInt(scanner, "Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> insertProducts(connection, scanner);
                    case 2 -> findLowStockByCategory(connection, scanner);
                    case 3 -> addStock(connection, scanner);
                    case 4 -> updatePrice(connection, scanner);
                    case 5 -> showValueAndDeleteZeroStock(connection, scanner);
                    case 6 -> {
                        System.out.println("Goodbye.");
                        running = false;
                    }
                    default -> System.out.println("Invalid choice. Select 1-6.");
                }
            } catch (SQLException e) {
                System.out.println("Database operation failed: " + e.getMessage());
            }
        }
    }

    private static void insertProducts(Connection connection, Scanner scanner) throws SQLException {
        int count = readInt(scanner, "How many products would you like to insert? ");
        if (count <= 0) {
            System.out.println("Enter a count greater than zero.");
            return;
        }
        String sql = "INSERT INTO Product (product_id, product_name, category, quantity, price) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= count; i++) {
                System.out.println("Product " + i + " of " + count + ":");
                int id = readInt(scanner, "  Product ID: ");
                System.out.print("  Product name: ");
                String name = scanner.nextLine().trim();
                System.out.print("  Category: ");
                String category = scanner.nextLine().trim();
                int quantity = readInt(scanner, "  Quantity: ");
                double price = readDouble(scanner, "  Price: ");

                statement.setInt(1, id);
                statement.setString(2, name);
                statement.setString(3, category);
                statement.setInt(4, quantity);
                statement.setDouble(5, price);
                try {
                    statement.executeUpdate();
                    System.out.println("Product inserted.");
                } catch (SQLIntegrityConstraintViolationException e) {
                    System.out.println("Product ID " + id + " already exists; this record was skipped.");
                }
            }
        }
    }

    private static void findLowStockByCategory(Connection connection, Scanner scanner) throws SQLException {
        System.out.print("Enter category: ");
        String category = scanner.nextLine().trim();
        String sql = "SELECT product_id, product_name, category, quantity, price FROM Product "
                + "WHERE category = ? AND quantity < 20 ORDER BY price DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            try (ResultSet results = statement.executeQuery()) {
                printProducts(results);
            }
        }
    }

    private static void addStock(Connection connection, Scanner scanner) throws SQLException {
        int id = readInt(scanner, "Enter product ID: ");
        int quantity = readInt(scanner, "Quantity to add: ");
        String sql = "UPDATE Product SET quantity = quantity + ? WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) {
                System.out.println("Product not found");
            } else {
                System.out.println("Product stock updated.");
            }
        }
    }

    private static void updatePrice(Connection connection, Scanner scanner) throws SQLException {
        int id = readInt(scanner, "Enter product ID: ");
        double price = readDouble(scanner, "Enter new price: ");
        if (price <= 0) {
            System.out.println("Price must be greater than 0.");
            return;
        }

        String updateSql = "UPDATE Product SET price = ? WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setDouble(1, price);
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) {
                System.out.println("Product not found.");
                return;
            }
        }
        String selectSql = "SELECT product_id, product_name, category, quantity, price FROM Product WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    System.out.println("Updated product:");
                    printProduct(results);
                }
            }
        }
    }

    private static void showValueAndDeleteZeroStock(Connection connection, Scanner scanner) throws SQLException {
        String totalSql = "SELECT SUM(quantity * price) FROM Product";
        try (PreparedStatement statement = connection.prepareStatement(totalSql);
             ResultSet results = statement.executeQuery()) {
            results.next();
            double total = results.getDouble(1);
            if (results.wasNull()) total = 0.0;
            System.out.printf("Total inventory value: %.2f%n", total);
        }

        int id = readInt(scanner, "Enter product ID to delete if its quantity is zero: ");
        String deleteSql = "DELETE FROM Product WHERE product_id = ? AND quantity = 0";
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setInt(1, id);
            if (statement.executeUpdate() > 0) {
                System.out.println("Zero-stock product deleted.");
                return;
            }
        }

        String checkSql = "SELECT quantity FROM Product WHERE product_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(checkSql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    System.out.println("Product cannot be deleted because its quantity is " + results.getInt("quantity") + ".");
                } else {
                    System.out.println("Product not found.");
                }
            }
        }
    }

    private static void printProducts(ResultSet results) throws SQLException {
        boolean found = false;
        while (results.next()) {
            found = true;
            printProduct(results);
        }
        if (!found) System.out.println("No matching products found.");
    }

    private static void printProduct(ResultSet result) throws SQLException {
        System.out.printf("ID: %d | Name: %s | Category: %s | Quantity: %d | Price: %.2f%n",
                result.getInt("product_id"), result.getString("product_name"),
                result.getString("category"), result.getInt("quantity"), result.getDouble("price"));
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}
