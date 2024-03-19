import java.time.LocalDate;
import java.util.Scanner;
import java.sql.* ;

public class Database {
    public static void main(String[] args) throws SQLException {
        new Database().startUserLoop();
    }

    private final Scanner scanner;
    private final Connection connection;
    private final Statement statement;

    //cid of the currently logged user. If the user has not logged in but makes a purchase, this also gets cached
    private int cachedCID=-1;


    public Database() throws SQLException {
        scanner= new Scanner(System.in);
        // Register the driver.  You must register the driver before you can use it.
        try { DriverManager.registerDriver ( new com.ibm.db2.jcc.DB2Driver() ) ; }
        catch (Exception cnfe){ System.out.println("Class not found"); }
        String url = "jdbc:db2://winter2024-comp421.cs.mcgill.ca:50000/comp421";

        //TODO do not hard code your password in the submission
        String your_userid = null;
        String your_password = null;

        if(your_userid == null && (your_userid = System.getenv("SOCSUSER")) == null)
        {
            System.err.println("Error!! do not have a password to connect to the database!");
            System.exit(1);
        }
        if(your_password == null && (your_password = System.getenv("SOCSPASSWD")) == null)
        {
            System.err.println("Error!! do not have a password to connect to the database!");
            System.exit(1);
        }

        connection = DriverManager.getConnection(url,your_userid,your_password);
        statement = connection.createStatement ( ) ;


    }

    private void parseMainMenuInputs(String input){
        try{
            int choice = Integer.parseInt(input);

            switch (choice){
                case 1->browse();
                case 2->purchase();
                case 3-> logIn();
                case 4->opt4();
                case 5->opt5();
                case 6->terminateProgram(0);
                default -> throw new Exception();
            }

        }catch (Exception e){
            System.out.printf("Invalid input [%s], please enter a number ranging from 1 to 6\n",input);
        }
    }

    private void terminateProgram(int exitCode) throws SQLException {
        if(exitCode!=0){
            System.out.println("program terminating because of some error");
        }

        statement.close();
        connection.close();
        System.exit(exitCode);
    }

    public void startUserLoop(){
        System.out.println("started");
        while (true){
            System.out.println("""
                    ------------------------------------------------
                    Book Store Main Menu:
                        Please select one of the following options:
                        1. see available books/Search for a book
                        2. Make a purchase
                        3. Log in to your membership account
                        4. TODO
                        5. TODO
                        6. Quit
                    ------------------------------------------------
                    """);
            System.out.print(">>");
            parseMainMenuInputs(scanner.nextLine());
        }
    }

    private void browse(){
        System.out.println("""
                    Please select one of the following options:
                    1. List all available books
                    2. See stock count for a specific book
                    """);
        System.out.print(">>");
        int choice;
        try{
            choice = Integer.parseInt(scanner.nextLine());
        }catch (Exception e){
            System.out.print("Invalid input");
            return;
        }

        switch (choice){
            case 1->{
                String query = "SELECT title FROM Book;";
                try {
                    ResultSet rs=statement.executeQuery(query);

                    while(rs.next()){
                        System.out.println("-  \""+rs.getString("title")+"\"");
                    }

                } catch (SQLException e) {
                    handleSQLException(e);
                }

            }
            case 2->{
                System.out.println("Please enter the tile of the book you'd like to search for:");
                System.out.print(">>");
                String input = scanner.nextLine();
                try {
                    PreparedStatement query = connection.prepareStatement("""
                        SELECT in_stock AS stock FROM
                        Book b  WHERE  b.title = ?;
                    """);
                    query.setString(1,input);
                    ResultSet rs=query.executeQuery();
                    if(!rs.next()){
                        System.out.println("There are no copies of the requested book");
                        return;
                    }

                    int booksInStock = rs.getInt("stock");
                    System.out.printf("There are %d copies of '%s' in stock\n",booksInStock,input);

                } catch (SQLException e) {
                    handleSQLException(e);
                }

            }
            default -> System.out.print("Invalid input");
        }


    }

    private void handleSQLException(SQLException e) {
        int sqlCode = e.getErrorCode(); // Get SQLCODE
        String sqlState = e.getSQLState(); // Get SQLSTATE

        System.out.println("Code: " + sqlCode + "  sqlState: " + sqlState);
        System.out.println(e);
    }

    private boolean fillInCredentials() throws SQLException {
        System.out.println("Please enter your credit card number (no spaces, just numbers):");
        System.out.print(">>");
        String cardNumS = scanner.nextLine();
        if(cardNumS.length()!= 16){
            System.out.println("invalid card number");
            return false;
        }
        for(char c : cardNumS.toCharArray()){
            if (!Character.isDigit(c)){
                System.out.println("invalid card number");
                return false;
            }
        }

        System.out.println("Please enter your expiration date (MMYY):");
        System.out.print(">>");
        String tempDate = scanner.nextLine();
        if(tempDate.length()!= 4){
            System.out.println("invalid expiration date");
            return false;
        }

        for(char c : tempDate.toCharArray()){
            if (!Character.isDigit(c)){
                System.out.println("invalid expiration date");
                return false;
            }
        }

        int month = Integer.parseInt(tempDate.substring(0, 2));
        int year = Integer.parseInt(tempDate.substring(2));



        PreparedStatement query = connection.prepareStatement("INSERT INTO REGULARCUSTOMER (card_num,EXPIRY) values (?,?)"
                ,Statement.RETURN_GENERATED_KEYS);
        query.setLong(1,Long.parseLong(cardNumS));
        query.setDate(2, Date.valueOf(LocalDate.of(year+2000,month,1)));
        if(query.executeUpdate()<0 || !query.getGeneratedKeys().next()){
            System.out.println("purchase failed inexplicably, sorry about that");
            return false;
        }
        cachedCID=query.getGeneratedKeys().getInt(1);
        return true;
    }

    private void purchase(){
        System.out.println("Please enter the tile of the book you'd like to purchase:");
        System.out.print(">>");
        String input = scanner.nextLine();
        try {
            PreparedStatement query = connection.prepareStatement("""
                        SELECT in_stock AS stock,isbn  FROM
                        Book b  WHERE  b.title = ?;
                    """);
            query.setString(1,input);
            ResultSet rs=query.executeQuery();
            if(!rs.next() ||  rs.getInt("stock")<=0){
                System.out.println("There are no copies of the requested book");
                return;
            }

            int isbn= rs.getInt("isbn");
            //only ask users to fill in bank info once
            if(cachedCID == -1) {
                if(!fillInCredentials()){
                    return;
                }
            }
            query= connection.prepareStatement("""
                UPDATE Book
                SET in_stock = in_stock-1
                WHERE title = ?;
            """);
            query.setString(1,input);
            if(query.executeUpdate() <1){
                System.out.println("purchase failed inexplicably, sorry about that");
                return;
            }

            query = connection.prepareStatement("INSERT INTO Transaction (cid) VALUES (?)",Statement.RETURN_GENERATED_KEYS);
            query.setInt(1, cachedCID);

            if(query.executeUpdate()<0 || !query.getGeneratedKeys().next()){
                System.out.println("purchase failed inexplicably, sorry about that");
                return;
            }

            int TNUM = query.getGeneratedKeys().getInt(1);
            query = connection.prepareStatement("INSERT INTO CONTAINS (tnum, isbn) VALUES (?,?)");
            query.setInt(1, TNUM);
            query.setInt(2, isbn);
            query.executeUpdate();

            System.out.printf("Your purchase of '%s' was performed successfully. Thank you for your " +
                    "patronage\n",input);

        } catch (SQLException e) {
            handleSQLException(e);
        }

    }
    private void logIn(){
        System.out.println("Please enter your email address:");
        System.out.print(">>");
        String input = scanner.nextLine();
        try {
            PreparedStatement query = connection.prepareStatement("""
                        SELECT PASSWORD AS pass,CID AS id,NAME AS nm from LOYALTYCUSTOMER
                        where EMAIL = ?
                    """);
            query.setString(1, input);
            ResultSet rs = query.executeQuery();
            if (!rs.next()) {
                System.out.println("There are no loyalty customer accounts with such an email");
                return;
            }
            System.out.println("Please enter your password:");
            System.out.print(">>");
            input = scanner.nextLine();
            String pswd= rs.getString("pass");
            if(!pswd.equals(input)){
                System.out.println("invalid password");
                return;
            }
            cachedCID = rs.getInt("id");
            String name = rs.getString("nm");
            System.out.println("Logged in successfully. Welcome "+name);

        }catch (SQLException e){
            handleSQLException(e);
        }

    }
    private void opt4(){
        //todo whoever chooses to do this
    }
    private void opt5(){
        //todo whoever chooses to do this
    }


}
