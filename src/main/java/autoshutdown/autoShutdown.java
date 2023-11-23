package autoshutdown;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.event.*;

public class autoShutdown {

  static boolean testing = false;

  // Static variables
  static boolean running = true, loadshedding = true;
  static String TOKEN = "";

  static String arguments = "";
  static String areaID = "";
  static int stage;
  static int timeBefore = 1;
  static String start, end, name;
  static boolean endAtMidnight = false;
  static LocalTime parsedTime1, parsedTime2, parsedTime3, parsedTime4, parsedStart, parsedEnd, parsedTime5;
  static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
  static ProcessBuilder shutdown = new ProcessBuilder("shutdown", "/s", "/t 0");
  static ProcessBuilder copyStartup = new ProcessBuilder("cmd", "/c", "mklink", "/d",
      "%appdata%/Microsoft/Windows/Start Menu/Programs/Startup" + File.separator + "Auto Shutdown", "Auto Shutdown v*");

  static TrayIcon trayIcon = new TrayIcon(
      new ImageIcon("assets/icon.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH), "Auto Shutdown");
  static final SystemTray tray = SystemTray.getSystemTray();
  static JLabel eventsTitle = new JLabel();

  static Thread shutdownThread;
  static final int INTERVAL = 5000;

  static Color backgroundColor = new Color(30, 30, 30);
  static Color accentColor = new Color(54, 51, 51);
  static Color fontColor = new Color(80, 81, 100);
  static Color highlightColor = fontColor;

  public static void main(String[] args)
      throws IOException, UnirestException, InterruptedException, FileNotFoundException {

    showNotification("Test", TOKEN);

    if (isInternetAvailable()) {
      getData();
    }

    SwingUtilities.invokeLater(() -> {
      try {
        createAndShowGUI();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (AWTException e) {
        e.printStackTrace();
      }
    });

    Thread.sleep(2000);

    if (!isInternetAvailable()) {
      showNotification("No Internet", "No internet connection, using previously obtained times");
    }

    // Start the shutdown thread
    shutdownThread = new Thread(() -> {
      while (true) {
        if (running) {
          try {
            handleShutdown();
            Thread.sleep(5000); // Wait for 5 seconds

            LocalTime now = LocalTime.parse(LocalTime.now().toString().substring(0, 5), formatter);

            if (parsedStart != null && parsedEnd != null) {
              if (now.equals(LocalTime.parse("00:00", formatter)) || now.equals(parsedEnd)
                  || now.equals(LocalTime.parse("05:00", formatter)) || now.equals(parsedStart.minusMinutes(1))) {
                getData();
                updateGUI();
              }
            }

          } catch (IOException | InterruptedException e) {
            e.printStackTrace();
          } catch (UnirestException e) {
            e.printStackTrace();
          }
        } else {
          try {
            Thread.sleep(2000); // If not running, wait for 1 second
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    });
    shutdownThread.start();

  }

  // Check if the device has an internet connection
  static boolean isInternetAvailable() {
    try {
      URI uri = new URI("https://www.google.com");
      URL url = uri.toURL();
      URLConnection connection = url.openConnection();
      connection.connect();
      return true;
    } catch (IOException | URISyntaxException e) {
      return false;
    }
  }

  // Get data from ESP API
  static void getData() throws UnirestException, IOException {

    if (testing) {
      arguments = "&test=current";
    }

    String fileName = "data.json";
    File dataFile = new File(fileName);

    if (dataFile.exists()) {
      System.out.println("File " + fileName + " exists.");
    } else {
      System.out.println("File " + fileName + " does not exist. Creating it...");
      try {
        boolean created = dataFile.createNewFile();
        if (created) {
          System.out.println("File " + fileName + " created successfully.");
          createData();
        } else {
          System.out.println("Failed to create file " + fileName);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    String timesFileName = "times.json";
    File timesFile = new File(timesFileName);

    if (timesFile.exists()) {
      System.out.println("File " + timesFile + " exists.");
    } else {
      System.out.println("File " + timesFile + " does not exist. Creating it...");
      try {
        boolean created = timesFile.createNewFile();
        if (created) {
          System.out.println("File " + timesFile + " created successfully.");
          createTimes();
        } else {
          System.out.println("Failed to create file " + timesFile);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    FileReader reader = new FileReader("data.json");
    JSONTokener tokener = new JSONTokener(reader);
    JSONObject data = new JSONObject(tokener);

    TOKEN = data.getString("token");
    areaID = data.getString("id");

    while (TOKEN.length() < 35 || TOKEN == null || TOKEN.length() > 35) {
      TOKEN = JOptionPane.showInputDialog("Please enter your ESP API 2.0 token");
      data.put("token", TOKEN);

      try {
        FileWriter areaInfoFile = new FileWriter("data.json");
        areaInfoFile.write(data.toString(4));
        areaInfoFile.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    // Request area information from ESP API (named "response")
    HttpResponse<String> response = Unirest
        .get("https://developer.sepush.co.za/business/2.0/area?id=" + areaID + arguments)
        .header("Token", TOKEN)
        .asString();

    // Convert the API response to a string
    String responseString = response.getBody();

    JSONObject apiResponseJSON = new JSONObject(responseString);

    System.out.println(responseString);

    if (response.getStatus() == 200 && !apiResponseJSON.getJSONArray("events").isEmpty()) {

      DayOfWeek currentDay = LocalDate.now().getDayOfWeek();

      JSONArray daysArray = apiResponseJSON.getJSONObject("schedule").getJSONArray("days");
      JSONObject events = apiResponseJSON.getJSONArray("events").getJSONObject(0);

      stage = Character.getNumericValue(events.getString("note").charAt(6));

      String start = null, end = null;

      start = events.getString("start").substring(11, 16);
      end = events.getString("end").substring(11, 16);

      System.out.println("end: " + end);

      JSONArray currentDayStagesArray = daysArray.getJSONObject(0).getJSONArray("stages");
      JSONArray nextDayStagesArray = daysArray.getJSONObject(1).getJSONArray("stages");

      String time1 = "", time2 = "", time3 = "", time4 = "", time5 = "";

      if (daysArray.getJSONObject(0).getString("name").equalsIgnoreCase(currentDay.toString())) {
        JSONArray stagesArray = currentDayStagesArray.getJSONArray(stage - 1);
        JSONArray nextStagesArray = nextDayStagesArray.getJSONArray(stage - 1);
        int stagesArrayLength = stagesArray.length();
        int nextStagesArrayLength = stagesArray.length();

        time1 = (stagesArrayLength > 0 && stagesArray.getString(0) != null) ? stagesArray.getString(0) : "";
        time2 = (stagesArrayLength > 1 && stagesArray.getString(1) != null) ? stagesArray.getString(1) : "";
        time3 = (stagesArrayLength > 2 && stagesArray.getString(2) != null) ? stagesArray.getString(2) : "";
        time4 = (stagesArrayLength > 3 && stagesArray.getString(3) != null) ? stagesArray.getString(3) : "";
        time5 = (nextStagesArrayLength > 0 && nextStagesArray.getString(0) != null) ? nextStagesArray.getString(0)
            : "";

        time1 = time1.length() > 1 ? time1.substring(0, 5) : null;
        time2 = time2.length() > 1 ? time2.substring(0, 5) : null;
        time3 = time3.length() > 1 ? time3.substring(0, 5) : null;
        time4 = time4.length() > 1 ? time4.substring(0, 5) : null;
        time5 = time5.length() > 1 ? time5.substring(0, 5) : null;

        parsedTime1 = time1 != null ? LocalTime.parse(time1, formatter) : null;
        parsedTime2 = time2 != null ? LocalTime.parse(time2, formatter) : null;
        parsedTime3 = time3 != null ? LocalTime.parse(time3, formatter) : null;
        parsedTime4 = time4 != null ? LocalTime.parse(time4, formatter) : null;
        parsedTime5 = time5 != null && LocalTime.parse(time5, formatter).equals(LocalTime.parse("00:00", formatter))
            ? LocalTime.parse(time5, formatter)
            : null;

        parsedStart = LocalTime.parse(start, formatter);
        parsedEnd = LocalTime.parse(end, formatter);

        System.out.println("gd_Parsed end: " + parsedEnd);
        System.out.println("gd_Parsed start: " + parsedStart);
        System.out.println(parsedTime1);
        System.out.println(parsedTime2);
        System.out.println(parsedTime3);
        System.out.println(parsedTime4);
        System.out.println(parsedTime5);

        FileReader dataReader = new FileReader("times.json");
        JSONTokener dataTokener = new JSONTokener(dataReader);
        JSONObject times = new JSONObject(dataTokener);

        JSONArray timesArray = times.getJSONArray("times");
        JSONObject timesObject = timesArray.getJSONObject(0);
        JSONArray stageArrayFile = times.getJSONArray("stage");
        JSONObject stageObjectFile = stageArrayFile.getJSONObject(0);

        timesObject.put("time1", parsedTime1 != null ? parsedTime1.toString() : "");
        timesObject.put("time2", parsedTime2 != null ? parsedTime2.toString() : "");
        timesObject.put("time3", parsedTime3 != null ? parsedTime3.toString() : "");
        timesObject.put("time4", parsedTime4 != null ? parsedTime4.toString() : "");
        timesObject.put("time5", parsedTime5 != null ? parsedTime5.toString() : "");
        stageObjectFile.put("level", stage);
        stageObjectFile.put("start", start);
        stageObjectFile.put("end", end);

        FileWriter writer = new FileWriter("times.json");
        writer.write(times.toString(4));
        writer.close();

      }

    } else {
      if (response.getStatus() == 200 && apiResponseJSON.getJSONArray("events").isEmpty()) {
        loadshedding = false;
      } else {
        JOptionPane.showMessageDialog(null, "Error: " + response.getStatus(), "An error ocurred",
            JOptionPane.ERROR_MESSAGE);
      }
    }

  }

  // Check if inputted time is within the loadshedding range and return the value
  // if true (NOW OBSOLETE)
  static LocalTime checkTime(LocalTime time) {

    if (parsedEnd.equals(LocalTime.parse("00:30", formatter))
        || parsedEnd.equals(LocalTime.parse("00:00", formatter))) {
      parsedEnd = LocalTime.parse("23:59", formatter);
    }

    System.out.println("Parsed start: " + parsedStart);
    System.out.println("Parsed end: " + parsedEnd);
    System.out.println("Time: " + time);

    if (time != null && ((time.isAfter(parsedStart) || time.equals(parsedStart))) && time.isBefore(parsedEnd)) {
      return time;
    } else {
      return null;
    }

  }

  // Check if the current time meets the shutdown criteria and handle accordingly
  static void handleShutdown() throws IOException, InterruptedException, UnirestException {

    LocalTime now = LocalTime.parse(LocalTime.now().toString().substring(0, 5), formatter);

    String fileName = "config.json";
    try {
      FileReader reader = new FileReader(fileName);
      JSONTokener tokener = new JSONTokener(reader);
      JSONObject config = new JSONObject(tokener);

      timeBefore = config.getInt("timeBefore");

      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (parsedTime1 != null
        && now.equals(parsedTime1.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
      showNotification("Shutting Down", "Shutting down in 30 seconds");
      Thread.sleep(30000);
      shutdown.start();
    } else {
      System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime1);
    }

    if (parsedTime2 != null
        && now.equals(parsedTime2.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
      showNotification("Shutting Down", "Shutting down in 30 seconds");
      Thread.sleep(30000);
      shutdown.start();
    } else {
      System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime2);
    }

    if (parsedTime3 != null
        && now.equals(parsedTime3.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
      showNotification("Shutting Down", "Shutting down in 30 seconds");
      Thread.sleep(30000);
      shutdown.start();
    } else {
      System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime3);
    }

    if (parsedTime4 != null
        && now.equals(parsedTime4.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
      showNotification("Shutting Down", "Shutting down in 30 seconds");
      Thread.sleep(30000);
      shutdown.start();
    } else {
      System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime4);
    }

    LocalTime beforeMidnight = LocalTime.parse("23:59", formatter);

    if (parsedTime5 != null
        && now.equals(
            beforeMidnight.minusMinutes(timeBefore).plusMinutes(1).truncatedTo(ChronoUnit.SECONDS))) {
      showNotification("Shutting Down", "Shutting down in 30 seconds");
      Thread.sleep(30000);
      shutdown.start();
    } else {
      System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime5);
    }

    if ((parsedTime1 != null && now.equals(parsedTime1.minusMinutes(55)))
        || (parsedTime2 != null && now.equals(parsedTime2.minusMinutes(55)))
        || (parsedTime3 != null && now.equals(parsedTime3.minusMinutes(55)))
        || (parsedTime4 != null && now.equals(parsedTime4.minusMinutes(55)))
        || (parsedTime5 != null && now.equals(parsedTime5.minusMinutes(55)))) {
      showNotification("Loadshedding soon", "Loadshedding in 55 minutes");
    }

    if ((parsedTime1 != null && now.equals(parsedTime1.minusMinutes(15)))
        || (parsedTime2 != null && now.equals(parsedTime2.minusMinutes(15)))
        || (parsedTime3 != null && now.equals(parsedTime3.minusMinutes(15)))
        || (parsedTime4 != null && now.equals(parsedTime4.minusMinutes(15)))
        || (parsedTime5 != null && now.equals(parsedTime5.minusMinutes(14)))) {
      showNotification("Loadshedding soon", "Loadshedding in 15 minutes");
    }

  }

  static JLabel event1Label = new JLabel("");
  static JLabel event2Label = new JLabel("");
  static JLabel event3Label = new JLabel("");
  static JLabel event4Label = new JLabel("");
  static JLabel event5Label = new JLabel("");
  static String event1 = "", event2 = "", event3 = "", event4 = "", event5 = "";

  // Update GUI with new getData() values
  public static void updateGUI() throws FileNotFoundException {

    FileReader timesReader = new FileReader("times.json");
    JSONTokener timesTokener = new JSONTokener(timesReader);
    JSONObject times = new JSONObject(timesTokener);

    String[] time = new String[5];

    JSONArray timesArray = times.getJSONArray("times");
    JSONArray stageArray = times.getJSONArray("stage");

    stage = stageArray.getJSONObject(0).getInt("level");

    if (timesArray.getJSONObject(0).get("time1") != null) {
      time[0] = timesArray.getJSONObject(0).get("time1").toString();
    }
    if (timesArray.getJSONObject(0).get("time2") != null) {
      time[1] = timesArray.getJSONObject(0).get("time2").toString();
    }
    if (timesArray.getJSONObject(0).get("time3") != null) {
      time[2] = timesArray.getJSONObject(0).get("time3").toString();
    }
    if (timesArray.getJSONObject(0).get("time4") != null) {
      time[3] = timesArray.getJSONObject(0).get("time4").toString();
    }
    if (timesArray.getJSONObject(0).get("time5") != null) {
      time[4] = timesArray.getJSONObject(0).get("time5").toString();
    }

    System.out.println("Stage: " + stage);

    LocalTime now = LocalTime.now();
    now = LocalTime.parse(("" + now).substring(0, 5), formatter);

    if (!time[0].isBlank() && LocalTime.parse(time[0], formatter).isAfter(now)) {
      event1 = time[0];
      event1Label.setText(event1);
    } else {
      event1Label.setText(null);
    }

    if (!time[1].isBlank() && LocalTime.parse(time[1], formatter).isAfter(now)) {
      event2 = time[1];
      event2Label.setText(event2);
    } else {
      event2Label.setText(null);
    }

    if (!time[2].isBlank() && LocalTime.parse(time[2], formatter).isAfter(now)) {
      event3 = time[2];
      event3Label.setText(event3);
    } else {
      event3Label.setText(null);
    }

    if (!time[3].isBlank() && LocalTime.parse(time[3], formatter).isAfter(now)) {
      event4 = time[3];
      event4Label.setText(event4);
    } else {
      event4Label.setText(null);
    }

    if (!time[4].isBlank()) {
      event5 = "00:00";
      event5Label.setText(event5);
    } else {
      event5Label.setText(null);
    }

    if (loadshedding) {
      eventsTitle.setText("Upcoming Events: (Stage " + stage + ")");
    } else {
      eventsTitle.setText("No Loadshedding! :)");
    }

  }

  // Show the main GUI
  public static void createAndShowGUI() throws FileNotFoundException, AWTException {

    boolean startMinimized = true;

    FileReader reader = new FileReader("data.json");
    JSONTokener tokener = new JSONTokener(reader);
    JSONObject data = new JSONObject(tokener);

    String configFileName = "config.json";

    File configFile = new File(configFileName);

    if (!configFile.exists()) {
      createConfig();
    } else {
      System.out.println("File already exists.");
    }

    try {
      FileReader configReader = new FileReader(configFileName);
      JSONTokener configTokener = new JSONTokener(configReader);
      JSONObject config = new JSONObject(configTokener);

      timeBefore = config.getInt("timeBefore");
      startMinimized = !config.getBoolean("startMinimized");

      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    String token = data.getString("token");
    while (token.length() < 35 || token == null || token.length() > 35) {
      token = JOptionPane.showInputDialog("Please enter your ESP API 2.0 token");
      data.put("token", token);

      try {
        FileWriter areaInfoFile = new FileWriter("data.json");
        areaInfoFile.write(data.toString(4));
        areaInfoFile.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    String areaName = data.getString("name");

    int imgW = 100;
    int imgH = 100;

    // Create the main frame
    JFrame frame = new JFrame("Auto Shutdown");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(800, 500);
    frame.getContentPane().setBackground(backgroundColor);
    frame.setLayout(null);

    // Create and set frame icon
    ImageIcon icon = new ImageIcon("assets/icon.png");
    frame.setIconImage(icon.getImage());

    tray.add(trayIcon);

    // Add a MouseListener to handle tray icon actions
    trayIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          frame.setVisible(true);
          frame.setExtendedState(JFrame.NORMAL);
          frame.toFront();
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          // Right-click handling for popup menu
          PopupMenu popup = new PopupMenu();
          MenuItem restoreItem = new MenuItem("Restore");
          MenuItem closeItem = new MenuItem("Exit");

          // ActionListener for "Open" button
          restoreItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              frame.setVisible(true);
              frame.setExtendedState(JFrame.NORMAL);
              frame.toFront();
            }
          });

          // ActionListener for "Close" button
          closeItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              System.exit(0);
            }
          });

          // Add the items to the popup menu
          popup.add(restoreItem);
          popup.add(closeItem);
          trayIcon.setPopupMenu(popup);
        }
      }
    });

    // Add the window listener to handle minimizing
    frame.addWindowListener(new WindowAdapter() {
      public void windowIconified(WindowEvent e) {
        frame.setVisible(false);
      }
    });

    // Create a toggle button with default state "on" (green)
    ImageIcon on = new ImageIcon(
        new ImageIcon("assets/on.png").getImage().getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH));
    ImageIcon off = new ImageIcon(
        new ImageIcon("assets/off.png").getImage().getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH));

    JToggleButton toggleButton = new JToggleButton(on) {
      @Override
      public void paintComponent(Graphics g) {
        if (getModel().isPressed()) {
          g.setColor(accentColor);
          g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
      }
    };

    toggleButton.setSelectedIcon(off);
    toggleButton.setUI(new BasicButtonUI());
    toggleButton.setContentAreaFilled(false);
    toggleButton.setBorderPainted(false);
    toggleButton.setPreferredSize(new Dimension(imgW, imgH));

    toggleButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (toggleButton.isSelected()) {
          System.out.println("Button State: OFF");
          running = false;
          try {
            updateGUI();
          } catch (FileNotFoundException e1) {
            e1.printStackTrace();
          }
        } else {
          System.out.println("Button State: ON");
          running = true;
        }
      }
    });

    JButton areaIDButton = new JButton("Area: " + areaName);
    areaIDButton.setToolTipText(areaName);

    // Set tooltip formatting
    UIManager.put("ToolTip.background", backgroundColor);
    UIManager.put("ToolTip.foreground", Color.WHITE);
    UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder());
    UIManager.put("ToolTip.font", new Font("Dialog", Font.BOLD, 13));

    areaIDButton.setBackground(backgroundColor);
    areaIDButton.setForeground(fontColor);
    areaIDButton.setContentAreaFilled(false);
    areaIDButton.setBorderPainted(false);
    areaIDButton.setFont(new Font("Dialog", Font.BOLD, 14));
    areaIDButton.setBorder(BorderFactory.createEmptyBorder());
    areaIDButton.setHorizontalTextPosition(SwingConstants.CENTER);
    areaIDButton.setHorizontalAlignment(SwingConstants.CENTER);

    areaIDButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        areaIDButton.setText("loading...");
        areaIDButton.setToolTipText(areaName);
        try {
          getAreaID();
        } catch (IOException e1) {
          e1.printStackTrace();
        }

        try (

            FileReader reader = new FileReader("data.json")) {
          JSONTokener tokener = new JSONTokener(reader);
          JSONObject data = new JSONObject(tokener);
          String newAreaName = data.getString("name");
          areaIDButton.setText("Area: " + newAreaName);
          areaIDButton.setToolTipText(newAreaName);

        } catch (JSONException | IOException e1) {

          e1.printStackTrace();
        }

        areaIDButton.setFocusPainted(false);

      }

    });

    updateGUI();

    // Create a dropdown menu button in the top left
    JButton dropdownButton = new JButton();

    dropdownButton.setUI(new BasicButtonUI());
    dropdownButton.setBackground(backgroundColor);
    dropdownButton.setForeground(fontColor);
    dropdownButton.setFont(new Font("Dialog", Font.BOLD, 13));
    dropdownButton.setBorderPainted(false);
    dropdownButton.setFocusPainted(false);
    dropdownButton.setMargin(new Insets(1, 0, 1, 1));
    dropdownButton.setHorizontalAlignment(SwingConstants.LEFT);

    if (timeBefore == 1) {
      dropdownButton.setText(timeBefore + " minute");
    } else {
      dropdownButton.setText(timeBefore + " minutes");
    }

    // Create the dropdown menu
    JPopupMenu popupMenu = new JPopupMenu();
    popupMenu.setBorderPainted(false);
    popupMenu.setBorder(null);
    popupMenu.setBorder(BorderFactory.createEmptyBorder());
    String[] timeOptions = { "1 minute", "2 minutes", "5 minutes", "Custom" };

    for (String option : timeOptions) {
      JMenuItem menuItem = new JMenuItem(option);
      menuItem.setBackground(accentColor);
      menuItem.setForeground(Color.WHITE);
      menuItem.setContentAreaFilled(false);
      menuItem.setBorderPainted(false);
      menuItem.setFocusPainted(false);
      menuItem.setBorder(BorderFactory.createEmptyBorder());
      menuItem.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {

          String selectedTime = "" + menuItem.getText().charAt(0);
          int selectedTimeInt = 0;
          System.out.println(selectedTime);

          String fileName = "config.json";
          JSONObject config = new JSONObject();

          if (selectedTime.equals("C")) {
            selectedTime = JOptionPane.showInputDialog("Enter time before loadshedding to shut down (minutes)");
            selectedTimeInt = Integer.parseInt(selectedTime);

            if (selectedTime == null || selectedTime.isBlank() || selectedTimeInt < 1) {
              selectedTimeInt = 1;
              JOptionPane.showMessageDialog(null, "Invalid Value", "An error has occured", JOptionPane.ERROR_MESSAGE);
            }

            try {
              config.put("timeBefore", selectedTimeInt);
              FileWriter writer = new FileWriter(fileName);
              writer.write(config.toString(4));
              writer.close();
            } catch (IOException f) {
              f.printStackTrace();
            }

          } else {
            selectedTimeInt = Integer.parseInt(selectedTime);
            try {
              config.put("timeBefore", Integer.parseInt(selectedTime));
              FileWriter writer = new FileWriter(fileName);
              writer.write(config.toString(4));
              writer.close();
            } catch (IOException f) {
              f.printStackTrace();
            }
          }

          if (selectedTimeInt == 1) {
            dropdownButton.setText(selectedTimeInt + " minute");
          } else {
            dropdownButton.setText(selectedTimeInt + " minutes");
          }

        }
      });
      popupMenu.add(menuItem);
    }

    // Add an action listener to the dropdown button to show the popup menu
    dropdownButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        popupMenu.show(dropdownButton, 0, dropdownButton.getHeight());
      }
    });

    JPanel events = new JPanel();
    events.setBackground(backgroundColor);
    events.setSize(213, 300);
    events.setLayout(new BoxLayout(events, BoxLayout.Y_AXIS)); // Set vertical layout

    System.out.println(loadshedding);
    System.out.println(stage);
    eventsTitle.setText("Upcoming Events: (Stage " + stage + ")");
    JLabel spacer = new JLabel("\n");

    // Create the options bar
    JPanel optionsBar = new JPanel();
    optionsBar.setBackground(Color.WHITE);
    optionsBar.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));

    // Add components to the options bar
    JButton settingsButton = new JButton("Settings");
    settingsButton.setForeground(fontColor);
    settingsButton.setBackground(Color.WHITE);
    settingsButton.setBorderPainted(false);
    settingsButton.setFocusPainted(false);
    settingsButton.setMargin(new Insets(1, 0, 1, 1));
    settingsButton.setHorizontalAlignment(SwingConstants.LEFT);

    settingsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JDialog dialog = new JDialog(frame, "Settings", true);
        dialog.setLayout(new GridBagLayout());
        dialog.setSize(650, 400);
        dialog.getContentPane().setBackground(backgroundColor);
        dialog.getContentPane().setForeground(fontColor);

        String fileName = "config.json";

        // Constraints for the button
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;
        buttonConstraints.gridy = 1;
        buttonConstraints.weightx = 1.0;
        buttonConstraints.weighty = 1.0;
        buttonConstraints.anchor = GridBagConstraints.SOUTHEAST;
        buttonConstraints.insets = new Insets(10, 10, 10, 10);

        // Add a button to the dialog
        JButton saveButton = new JButton("Save");
        saveButton.setBackground(accentColor);
        saveButton.setForeground(Color.WHITE);
        saveButton.setFont(new Font("Dialog", Font.BOLD, 14));
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        saveButton.setPreferredSize(new Dimension(80, 25));

        JLabel generalLabel = new JLabel("General");
        generalLabel.setFont(new Font("Dialog", Font.BOLD, 24));
        generalLabel.setForeground(Color.WHITE);

        // Constraints for "general" label
        GridBagConstraints gLabelConstraints = new GridBagConstraints();
        gLabelConstraints.gridx = 1;
        gLabelConstraints.gridy = 1;
        gLabelConstraints.weightx = 1.0;
        gLabelConstraints.weighty = 1.0;
        gLabelConstraints.anchor = GridBagConstraints.NORTHWEST;
        gLabelConstraints.insets = new Insets(15, 20, 10, 10);

        JCheckBox startup = new JCheckBox("Run on startup");
        startup.setBackground(backgroundColor);
        startup.setForeground(Color.WHITE);
        startup.setBorderPainted(false);
        startup.setFocusPainted(false);

        // Constraints for startup check box
        GridBagConstraints startupConstraints = new GridBagConstraints();
        startupConstraints.gridx = 1;
        startupConstraints.gridy = 1;
        startupConstraints.weightx = 1.0;
        startupConstraints.weighty = 1.0;
        startupConstraints.anchor = GridBagConstraints.NORTHWEST;
        startupConstraints.insets = new Insets(60, 19, 10, 10);

        JCheckBox startBehaviour = new JCheckBox("Start Minimized");
        startBehaviour.setBackground(backgroundColor);
        startBehaviour.setForeground(Color.WHITE);
        startBehaviour.setBorderPainted(false);
        startBehaviour.setFocusPainted(false);

        // Constraints for startup check box
        GridBagConstraints startBehaviourConstraints = new GridBagConstraints();
        startBehaviourConstraints.gridx = 1;
        startBehaviourConstraints.gridy = 1;
        startBehaviourConstraints.weightx = 1.0;
        startBehaviourConstraints.weighty = 1.0;
        startBehaviourConstraints.anchor = GridBagConstraints.NORTHWEST;
        startBehaviourConstraints.insets = new Insets(80, 19, 10, 10);

        try {

          FileReader reader = new FileReader(fileName);
          JSONTokener tokener = new JSONTokener(reader);
          JSONObject config = new JSONObject(tokener);

          startup.setSelected(config.getBoolean("runOnStartup"));
          startBehaviour.setSelected(config.getBoolean("startMinimized"));

          reader.close();
        } catch (FileNotFoundException a) {
          a.printStackTrace();
        } catch (IOException e1) {
          e1.printStackTrace();
        }

        dialog.add(startup, startupConstraints);
        dialog.add(startBehaviour, startBehaviourConstraints);
        dialog.add(generalLabel, gLabelConstraints);
        dialog.add(saveButton, buttonConstraints);

        int x = frame.getX() + (frame.getWidth() / 2) - dialog.getWidth() / 2;
        int y = frame.getY() + (frame.getHeight() / 2) - dialog.getHeight() / 2;

        saveButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            try {
              // Read existing JSON data from the file
              StringBuilder fileData = new StringBuilder();
              BufferedReader reader = new BufferedReader(new FileReader(fileName));
              String line;
              while ((line = reader.readLine()) != null) {
                fileData.append(line).append("\n");
              }
              reader.close();

              // Parse existing JSON data
              JSONObject config = new JSONObject(fileData.toString());

              // Update the JSON with new data
              config.put("runOnStartup", startup.isSelected());
              config.put("startMinimized", startBehaviour.isSelected());

              if (startup.isSelected()) {
                System.out.println("Creating startup link");
                createStartupLink();
              } else {
                System.out.println("Deleting startup link");
                deleteStartupLink();
              }

              // Write the updated JSON back to the file
              FileWriter writer = new FileWriter(fileName);
              writer.write(config.toString(4));
              writer.close();
            } catch (IOException f) {
              f.printStackTrace();
            }
            dialog.dispose();
          }
        });

        dialog.setLocation(x, y);
        dialog.setResizable(false);
        dialog.setVisible(true);
      }

    });

    // TODO Finish help button
    JButton helpButton = new JButton("Help");
    helpButton.setForeground(fontColor);
    helpButton.setBackground(Color.WHITE);
    helpButton.setBorderPainted(false);
    helpButton.setFocusPainted(false);
    helpButton.setMargin(new Insets(1, 0, 1, 1));
    helpButton.setHorizontalAlignment(SwingConstants.LEFT);

    optionsBar.add(settingsButton);
    optionsBar.add(helpButton);

    event1Label.setText(event1);
    event2Label.setText(event2);
    event3Label.setText(event3);
    event4Label.setText(event4);
    event5Label.setText(event5);

    if (loadshedding == false) {
      event1Label.setText("No loadshedding!");
    }

    Font labelFont = new Font("Dialog", Font.BOLD, 14);

    eventsTitle.setFont(new Font("Dialog", Font.BOLD, 16));
    eventsTitle.setForeground(fontColor);

    event1Label.setFont(labelFont);
    event2Label.setFont(labelFont);
    event3Label.setFont(labelFont);
    event4Label.setFont(labelFont);
    event5Label.setFont(labelFont);

    event1Label.setForeground(fontColor);
    event2Label.setForeground(fontColor);
    event3Label.setForeground(fontColor);
    event4Label.setForeground(fontColor);
    event5Label.setForeground(fontColor);

    events.add(eventsTitle);
    events.add(spacer);
    events.add(event1Label);
    events.add(event2Label);
    events.add(event3Label);
    events.add(event4Label);
    events.add(event5Label);

    frame.add(dropdownButton);
    frame.add(areaIDButton);
    frame.add(toggleButton);
    frame.add(events);
    frame.add(optionsBar);
    frame.setVisible(startMinimized);
    frame.setResizable(false);

    dropdownButton.setBounds(7, 20, 110, 20);
    toggleButton.setBounds((frame.getWidth() / 2) - imgW / 2, 150, imgW, imgH);
    areaIDButton.setBounds((frame.getWidth() / 2) - 150, 250, 300, 50);
    optionsBar.setBounds(-3, -8, 800, 27);
    events.setBounds(557, 150, 213, 300);
  } // End of createAndShowGUI

  // Enable "run on startup" by creating a shortcut in the "startup" folder to the appliaction executable
  private static void createStartupLink() {

    try {
      String scriptContent = "@echo off\n"
              + "set \"targetFile=Auto Shutdown v*.exe\"\n"
              + "set \"shortcutName=Auto Shutdown.lnk\"\n"
              + "set \"startupFolder=%appdata%\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\"\n"
              + "\n"
              + "for %%F in (\"%targetFile%\") do (\n"
              + "    set \"targetFilePath=%%~fF\"\n"
              + "    set \"targetFileName=%%~nxF\"\n"
              + ")\n"
              + "\n"
              + "set \"shortcutTarget=%startupFolder%\\%shortcutName%\"\n"
              + "\n"
              + "echo Creating shortcut...\n"
              + "echo Target File: %targetFileName%\n"
              + "echo Startup Folder: %startupFolder%\n"
              + "echo Shortcut Target: %shortcutTarget%\n"
              + "\n"
              + "powershell -Command \"$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%shortcutTarget%'); $Shortcut.TargetPath = '%targetFilePath%'; $Shortcut.Save()\"\n"
              + "\n"
              + "echo Shortcut created at %shortcutTarget%\n";

      // Create a temporary batch file
      File tempBatchFile = File.createTempFile("createShortcut", ".bat");
      FileWriter writer = new FileWriter(tempBatchFile);
      writer.write(scriptContent);
      writer.close();

      ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", tempBatchFile.getAbsolutePath());
      processBuilder.directory(new File(System.getProperty("user.dir")));

      Process process = processBuilder.start();
      int exitCode = process.waitFor();

      if (exitCode == 0) {
          System.out.println("Shortcut created successfully.");
      } else {
          System.out.println("Failed to create shortcut. Exit code: " + exitCode);
      }

      // Clean up the temporary batch file
      tempBatchFile.delete();
  } catch (IOException | InterruptedException e) {
      e.printStackTrace();
  }
  }

  // Delete "startup" folder link to disable "run on startup"
  static void deleteStartupLink() {
    String destinationDirectory = System.getenv("APPDATA")
        + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\Auto Shutdown.lnk";

    try {
      Path link = Paths.get(destinationDirectory);
      if (Files.exists(link)) {
        Files.delete(link);
        System.out.println("Symbolic link deleted successfully.");
      } else {
        System.out.println("Symbolic link does not exist at: " + destinationDirectory);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Send a system notification
  public static void showNotification(String title, String message) {
    trayIcon.displayMessage(title, message, MessageType.INFO);
  }

  // Get the area ID of a search term
  public static void getAreaID() throws IOException {

    try {

      // Enter search query
      String searchQuery = JOptionPane.showInputDialog("Enter area to search for...");

      String TOKEN = "";

      String areaId = "";

      FileReader reader = new FileReader("data.json");
      JSONTokener tokener = new JSONTokener(reader);
      JSONObject data = new JSONObject(tokener);

      TOKEN = data.get("token").toString();
      System.out.println("Token: " + TOKEN);

      if ((searchQuery != null) && (searchQuery.length() > 0) && (searchQuery != " ") && (searchQuery != "")) {

        String encodedSearchQuery = URLEncoder.encode(searchQuery, "UTF-8");

        // Fetch area information
        HttpResponse<String> areaResponse = Unirest
            .get("https://developer.sepush.co.za/business/2.0/areas_search?text=" + encodedSearchQuery)
            .header("Token", TOKEN)
            .asString();

        // Check if the response is successful
        if (areaResponse.getStatus() == 200) {
          // Parse the JSON response
          JSONObject json = new JSONObject(areaResponse.getBody());
          JSONArray areas = json.getJSONArray("areas");
          System.out.println("Areas response: " + areas);

          String areaName = "";

          // Check if any areas were found
          if (areas.length() > 0) {
            // Get the first area ID
            areaId = areas.getJSONObject(0).getString("id");
            System.out.println("Area ID: " + areaId);
            areaName = areas.getJSONObject(0).getString("name");
            System.out.println("Area name: " + areaName);
          }

          data.put("name", areaName);
          data.put("id", areaId);

          try {
            FileWriter areaInfoFile = new FileWriter("data.json");
            areaInfoFile.write(data.toString(4));
            areaInfoFile.close();
          } catch (IOException e) {
            e.printStackTrace();
          }

          getData();

        } else {
          JOptionPane.showMessageDialog(null, "An error occured", "Error " + areaResponse.getStatus(), 0);
        }
      }

    } catch (UnirestException e) {
      System.out.println("Something went wrong");
      JOptionPane.showMessageDialog(null, "An error occured", "Error", 0);
    } catch (FileNotFoundException e1) {
      e1.printStackTrace();
    }
  } // End of areaID

  // Create the times.json file
  public static void createTimes() {
    // Create a JSON object for the "times" structure
    JSONObject timesObject = new JSONObject();
    JSONArray timesArray = new JSONArray();
    JSONObject timeData = new JSONObject();
    timeData.put("time1", "");
    timeData.put("time2", "");
    timeData.put("time3", "");
    timeData.put("time4", "");
    timesArray.put(timeData);
    timesObject.put("times", timesArray);

    // Create a JSON object for the "stage" structure
    JSONArray stageArray = new JSONArray();
    JSONObject stageData = new JSONObject();
    stageData.put("level", 0);
    stageData.put("start", "");
    stageData.put("end", "");
    stageArray.put(stageData);
    timesObject.put("stage", stageArray);

    // Write the JSON object to a file named "times.json"
    try (FileWriter fileWriter = new FileWriter("times.json")) {
      fileWriter.write(timesObject.toString(4));
    } catch (IOException e) {
      e.printStackTrace();
    }
  } // End of createTimes

  // Create the data.json file
  public static void createData() {
    // Create a JSON object for the specified structure
    JSONObject dataObject = new JSONObject();
    dataObject.put("name", "Cape Town CBD (7)");
    dataObject.put("id", "capetown-7-capetowncbd");
    dataObject.put("token", "");

    // Write the JSON object to a file named "data.json"
    try (FileWriter fileWriter = new FileWriter("data.json")) {
      fileWriter.write(dataObject.toString(4)); // Indent with 4 spaces for pretty printing
    } catch (IOException e) {
      e.printStackTrace();
    }
  } // End of createData

  // Create the config.json file
  public static void createConfig() {
    // Create a JSON object for the specified structure
    JSONObject configObject = new JSONObject();
    configObject.put("timeBefore", 1);
    configObject.put("runOnStartup", true);
    configObject.put("startMinimized", false);

    // Write the JSON object to a file named "data.json"
    try (FileWriter fileWriter = new FileWriter("config.json")) {
      fileWriter.write(configObject.toString(4)); // Indent with 4 spaces for pretty printing
    } catch (IOException e) {
      e.printStackTrace();
    }
  } // End of createConfig

}