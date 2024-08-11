package mealplanner;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

class Meal {
  private String category;
  private String name;
  private List<String> ingredients;

  public Meal(String category, String name, List<String> ingredients) {
    this.category = category;
    this.name = name;
    this.ingredients = ingredients;
  }

  public String getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public List<String> getIngredients() {
    return ingredients;
  }
}

public class Main {
  private static Connection connection;

  public static void main(String[] args) throws SQLException, IOException {
    String DB_URL = "jdbc:postgresql:meals_db";
    String USER = "postgres";
    String PASS = "1111";

    connection = DriverManager.getConnection(DB_URL, USER, PASS);
    connection.setAutoCommit(true);

    Statement statement = connection.createStatement();

    // Create the meals table if it doesn't exist
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS meals (" +
            "category VARCHAR(255)," +
            "meal VARCHAR(255)," +
            "meal_id INTEGER" +
            ")");

    // Create the ingredients table if it doesn't exist
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS ingredients (" +
            "ingredient VARCHAR(255)," +
            "ingredient_id INTEGER," +
            "meal_id INTEGER" +
            ")");

    // Create the plan table if it doesn't exist
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS plan (" +
            "day VARCHAR(255), " +
            "meal_category VARCHAR(255), " +
            "meal VARCHAR(255), " +
            "meal_id INTEGER" +
            ")");

    statement.close();

    // Load data from the database into memory
    List<Meal> meals = loadMealsFromDatabase();

    Scanner scanner = new Scanner(System.in);
    boolean isPlanDone = false;

    while (true) {
      System.out.println("What would you like to do (add, show, plan, save, exit)?");
      String action = scanner.nextLine().trim().toLowerCase();

      switch (action) {
        case "add":
          addMeal(scanner, meals);
          break;
        case "show":
          showMeals(scanner, meals);
          break;
        case "plan":
          planMeals(scanner, meals);
          isPlanDone = true;
          break;
        case "save":
          generateShoppingList(scanner);
          break;
        case "exit":
          System.out.println("Bye!");
          closeDatabase();
          return;
        default:
          System.out.println("What would you like to do (add, show, plan, save, exit)?");
      }
    }
  }

  private static boolean isPlanTableFilled() throws SQLException {
    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM plan");
    resultSet.next();
    int count = resultSet.getInt(1);
    resultSet.close();
    statement.close();
    return count > 0;
  }

  private static void generateShoppingList(Scanner scanner) throws SQLException, IOException {
    if (!isPlanTableFilled()) {
      System.out.println("Unable to save. Plan your meals first.");
      return;
    }

    Map<String, Integer> ingredientCount = new HashMap<>();

    Statement statement = connection.createStatement();
    ResultSet resultSet = statement.executeQuery("SELECT meal_id FROM plan");

    while (resultSet.next()) {
      int mealId = resultSet.getInt("meal_id");

      Statement ingredientStatement = connection.createStatement();
      ResultSet ingredientResultSet = ingredientStatement.executeQuery("SELECT ingredient FROM ingredients WHERE meal_id = " + mealId);

      while (ingredientResultSet.next()) {
        String ingredient = ingredientResultSet.getString("ingredient");
        ingredientCount.put(ingredient, ingredientCount.getOrDefault(ingredient, 0) + 1);
      }

      ingredientResultSet.close();
      ingredientStatement.close();
    }

    resultSet.close();
    statement.close();

    System.out.println("Input a filename:");
    String fileName = scanner.nextLine();

    try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName)))) {
      for (Map.Entry<String, Integer> entry : ingredientCount.entrySet()) {
        String ingredient = entry.getKey();
        int count = entry.getValue();
        if (count > 1) {
          writer.println(ingredient + " x" + count);
        } else {
          writer.println(ingredient);
        }
      }
      writer.flush();
      System.out.println("Saved!");
    }
  }

  private static void planMeals(Scanner scanner, List<Meal> meals) throws SQLException {
    String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    String[] categories = {"breakfast", "lunch", "dinner"};

    Statement insertStatement = connection.createStatement();

    // Clear previous plan
    insertStatement.executeUpdate("DELETE FROM plan");

    for (String day : days) {
      System.out.println(day);

      for (String category : categories) {
        // Filter and sort meals by category
        List<String> mealNames = new ArrayList<>();
        for (Meal meal : meals) {
          if (meal.getCategory().equalsIgnoreCase(category)) {
            mealNames.add(meal.getName());
          }
        }

        // Sort the meal names alphabetically
        Collections.sort(mealNames);

        // Display the sorted meal names
        for (String mealName : mealNames) {
          System.out.println(mealName);
        }

        String mealChoice;
        int mealId = -1;

        while (true) {
          System.out.println("Choose the " + category + " for " + day + " from the list above:");
          mealChoice = scanner.nextLine().trim();

          for (Meal meal : meals) {
            if (meal.getName().equalsIgnoreCase(mealChoice) && meal.getCategory().equalsIgnoreCase(category)) {
              mealId = meals.indexOf(meal) + 1;  // Assuming meal_id is the index + 1
              break;
            }
          }

          if (mealId != -1) break;
          System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
        }

        insertStatement.executeUpdate("INSERT INTO plan (day, meal_category, meal, meal_id) VALUES ('"
                + day + "', '"
                + category + "', '"
                + mealChoice + "', "
                + mealId + ")");
      }

      System.out.println("Yeah! We planned the meals for " + day + ".");
    }
    insertStatement.close();

    // Print the entire plan for the week
    printWeeklyPlan();
  }

  private static void printWeeklyPlan() throws SQLException {
    Statement statement = connection.createStatement();
    ResultSet rsPlan = statement.executeQuery("SELECT * FROM plan");

    String currentDay = "";
    while (rsPlan.next()) {
      String day = rsPlan.getString("day");
      String category = rsPlan.getString("meal_category");
      String meal = rsPlan.getString("meal");

      if (!day.equals(currentDay)) {
        if (!currentDay.equals("")) {
          System.out.println();
        }
        System.out.println(day);
        currentDay = day;
      }

      System.out.println(capitalizeFirstLetter(category) + ": " + meal);
    }

    rsPlan.close();
    statement.close();
  }

  private static String capitalizeFirstLetter(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
  }

  private static List<Meal> loadMealsFromDatabase() throws SQLException {
    List<Meal> meals = new ArrayList<>();

    Statement mealStatement = connection.createStatement();
    ResultSet rsMeals = mealStatement.executeQuery("SELECT * FROM meals");

    while (rsMeals.next()) {
      String category = rsMeals.getString("category");
      String name = rsMeals.getString("meal");
      int mealId = rsMeals.getInt("meal_id");

      // Query ingredients for the current meal
      List<String> ingredients = new ArrayList<>();
      Statement ingredientStatement = connection.createStatement();
      ResultSet rsIngredients = ingredientStatement.executeQuery("SELECT ingredient FROM ingredients WHERE meal_id = " + mealId);

      while (rsIngredients.next()) {
        ingredients.add(rsIngredients.getString("ingredient"));
      }
      rsIngredients.close();  // Close the ResultSet for ingredients after use
      ingredientStatement.close();  // Close the Statement for ingredients after use

      meals.add(new Meal(category, name, ingredients));
    }

    rsMeals.close();  // Close the ResultSet for meals after use
    mealStatement.close();  // Close the Statement for meals after use

    return meals;
  }

  private static void addMeal(Scanner scanner, List<Meal> meals) throws SQLException {
    String category;
    while (true) {
      System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
      category = scanner.nextLine().trim().toLowerCase();
      if (category.equals("breakfast") || category.equals("lunch") || category.equals("dinner")) {
        break;
      } else {
        System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
      }
    }

    String name;
    while (true) {
      System.out.println("Input the meal's name:");
      name = scanner.nextLine().trim();
      if (name.matches("[a-zA-Z ]+")) {
        break;
      } else {
        System.out.println("Wrong format. Use letters only!");
      }
    }

    // Auto-generate meal ID based on current meals size
    int mealId = meals.size() + 1;

    List<String> ingredients = new ArrayList<>();
    while (true) {
      System.out.println("Input the ingredients:");
      String ingredientsInput = scanner.nextLine().trim();

      if (ingredientsInput.endsWith(",")) {
        System.out.println("Wrong format. Use letters only!");
        continue;
      }

      String[] ingredientsArray = ingredientsInput.split(",\\s*");

      boolean valid = true;
      for (String ingredient : ingredientsArray) {
        ingredient = ingredient.trim();
        if (ingredient.isEmpty() || !ingredient.matches("[a-zA-Z ]+")) {
          valid = false;
          break;
        }
      }

      if (valid && ingredientsArray.length > 0) {
        for (String ingredient : ingredientsArray) {
          ingredients.add(ingredient.trim());
        }
        break;
      } else {
        System.out.println("Wrong format. Use letters only!");
      }
    }

    Statement insertStatement = connection.createStatement();

    insertStatement.executeUpdate("INSERT INTO meals (category, meal, meal_id) VALUES ('" + category + "', '" + name + "', " + mealId + ")");

    for (int i = 0; i < ingredients.size(); i++) {
      String ingredient = ingredients.get(i);
      insertStatement.executeUpdate("INSERT INTO ingredients (ingredient, ingredient_id, meal_id) VALUES ('" + ingredient + "', " + (i + 1) + ", " + mealId + ")");
    }

    insertStatement.close();

    meals.add(new Meal(category, name, ingredients));
    System.out.println("The meal has been added!");
  }

  private static void showMeals(Scanner scanner, List<Meal> meals) {
    String askedCategory;
    System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
    while (true) {
      askedCategory = scanner.nextLine().trim().toLowerCase();
      if (askedCategory.equals("breakfast") || askedCategory.equals("lunch") || askedCategory.equals("dinner")) {
        break;
      } else {
        System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
      }
    }

    int count = 0;

    for (Meal meal : meals) {
      if (meal.getCategory().equalsIgnoreCase(askedCategory)) {
        if (count == 0) {
          System.out.println("Category: " + askedCategory); // Print the category only if there's at least one meal
        }
        count++;
        System.out.println("Name: " + meal.getName());
        System.out.println("Ingredients:");
        for (String ingredient : meal.getIngredients()) {
          System.out.println(ingredient);
        }
        System.out.println();  // Add a blank line after each meal for formatting
      }
    }

    if (count == 0) {
      System.out.println("No meals found.");
    }
  }

  private static void closeDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
  }
}
