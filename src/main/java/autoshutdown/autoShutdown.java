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
import java.io.BufferedWriter;
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

  static String versionInfo = "<html>Name:\tAuto Shutdown v2.4 <br>Version:\t2.4 <br>Updated:\t30/11/2023 <br> Publisher:\tVortex IT Solutions <br> GitHub:\t<a href=\"https://github.com/vortexit07/autoshutdown\">vortexit07/autoshutdown</a></html>";

  // Static variables
  static boolean running = true, loadshedding = true;
  static String TOKEN = "";

  static String arguments = "";
  static String areaID = "";
  static int stage;
  static int timeBefore = 1;
  static String os = "";
  static String start, end, name;
  static boolean endAtMidnight = false;
  static LocalTime parsedTime1, parsedTime2, parsedTime3, parsedTime4, parsedStart, parsedEnd, parsedTime5;
  static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
  static ProcessBuilder shutdown = new ProcessBuilder("shutdown", "/s", "/t", "0");
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

  public static void main(String[] args) {
    try {
      if (isInternetAvailable()) {
        getData();
      }

      os = getOS();
      createLog();

      SwingUtilities.invokeLater(() -> {
        createAndShowGUI();
      });

      Thread.sleep(2000);

      if (!isInternetAvailable()) {
        showNotification("Auto Shutdown", "No internet connection, using previously obtained times");
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
                    || now.equals(LocalTime.parse("05:00", formatter))
                    || now.equals(parsedStart.minusMinutes(1))) {
                  getData();
                  updateGUI();
                }
              }

            } catch (InterruptedException e) {
              e.printStackTrace();
              logException(e);
            }
          } else {
            try {
              Thread.sleep(2000);
            } catch (InterruptedException e) {
              e.printStackTrace();
              logException(e);
            }
          }
        }
      });
      shutdownThread.start();

    } catch (InterruptedException e) {
      e.printStackTrace();
      logException(e);
    }
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
  static void getData() {
    try {
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
          logException(e);
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
          logException(e);
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
          logException(e);
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
    } catch (IOException | UnirestException e) {
      e.printStackTrace();
      logException(e);
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
  static void handleShutdown() {
    try {
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
        logException(e);
      }

      try {
        FileReader timesReader = new FileReader("times.json");
        JSONTokener timesTokener = new JSONTokener(timesReader);
        JSONObject timesJSON = new JSONObject(timesTokener);

        JSONArray times = timesJSON.getJSONArray("times");

        parsedTime1 = !times.getJSONObject(0).get("time1").toString().isBlank()
            ? LocalTime.parse(times.getJSONObject(0).get("time1").toString(), formatter)
            : null;
        parsedTime2 = !times.getJSONObject(0).get("time2").toString().isBlank()
            ? LocalTime.parse(times.getJSONObject(0).get("time2").toString(), formatter)
            : null;
        parsedTime3 = !times.getJSONObject(0).get("time3").toString().isBlank()
            ? LocalTime.parse(times.getJSONObject(0).get("time3").toString(), formatter)
            : null;
        parsedTime4 = !times.getJSONObject(0).get("time4").toString().isBlank()
            ? LocalTime.parse(times.getJSONObject(0).get("time4").toString(), formatter)
            : null;
        parsedTime5 = !times.getJSONObject(0).get("time5").toString().isBlank()
            ? LocalTime.parse(times.getJSONObject(0).get("time5").toString(), formatter)
            : null;

        timesReader.close();
      } catch (IOException e) {
        e.printStackTrace();
        logException(e);
      }

      if (parsedTime1 != null
          && now.equals(parsedTime1.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
        showNotification("Shutting Down", "Shutting down in 30 seconds");
        Thread.sleep(30000);

        if (os.equals("windows")) {
          shutdown.start();
        } else if (os.equals("mac")) {
          shutdownMac();
        } else if (os.equals("linux")) {
          shutdownLinux();
        }

      } else {
        System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime1);
      }

      if (parsedTime2 != null
          && now.equals(parsedTime2.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
        showNotification("Shutting Down", "Shutting down in 30 seconds");
        Thread.sleep(30000);
        if (os.equals("windows")) {
          shutdown.start();
        } else if (os.equals("mac")) {
          shutdownMac();
        } else if (os.equals("linux")) {
          shutdownLinux();
        }

      } else {
        System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime2);
      }

      if (parsedTime3 != null
          && now.equals(parsedTime3.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
        showNotification("Shutting Down", "Shutting down in 30 seconds");
        Thread.sleep(30000);
        if (os.equals("windows")) {
          shutdown.start();
        } else if (os.equals("mac")) {
          shutdownMac();
        } else if (os.equals("linux")) {
          shutdownLinux();
        }

      } else {
        System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime3);
      }

      if (parsedTime4 != null
          && now.equals(parsedTime4.minusMinutes(timeBefore).truncatedTo(ChronoUnit.SECONDS))) {
        showNotification("Shutting Down", "Shutting down in 30 seconds");
        Thread.sleep(30000);
        if (os.equals("windows")) {
          shutdown.start();
        } else if (os.equals("mac")) {
          shutdownMac();
        } else if (os.equals("linux")) {
          shutdownLinux();
        }

      } else {
        System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime4);
      }

      LocalTime beforeMidnight = LocalTime.parse("23:59", formatter);

      if (parsedTime5 != null
          && now.equals(
              beforeMidnight.minusMinutes(timeBefore).plusMinutes(1).truncatedTo(ChronoUnit.SECONDS))) {
        showNotification("Shutting Down", "Shutting down in 30 seconds");
        Thread.sleep(30000);
        if (os.equals("windows")) {
          shutdown.start();
        } else if (os.equals("mac")) {
          shutdownMac();
        } else if (os.equals("linux")) {
          shutdownLinux();
        }

      } else {
        System.out.println("Waiting for " + timeBefore + " minute(s) before " + parsedTime5);
      }

      if ((parsedTime1 != null && now.equals(parsedTime1.minusMinutes(55)))
          || (parsedTime2 != null && now.equals(parsedTime2.minusMinutes(55)))
          || (parsedTime3 != null && now.equals(parsedTime3.minusMinutes(55)))
          || (parsedTime4 != null && now.equals(parsedTime4.minusMinutes(55)))
          || (parsedTime5 != null && now.equals(beforeMidnight.minusMinutes(55)))) {
        showNotification("Loadshedding soon", "Loadshedding in 55 minutes");
        Thread.sleep(60500);
      }

      if ((parsedTime1 != null && now.equals(parsedTime1.minusMinutes(15)))
          || (parsedTime2 != null && now.equals(parsedTime2.minusMinutes(15)))
          || (parsedTime3 != null && now.equals(parsedTime3.minusMinutes(15)))
          || (parsedTime4 != null && now.equals(parsedTime4.minusMinutes(15)))
          || (parsedTime5 != null && now.equals(beforeMidnight.minusMinutes(14)))) {
        showNotification("Loadshedding soon", "Loadshedding in 15 minutes");
        Thread.sleep(60500);
      }

    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      logException(e);
    }

  }

  static JLabel event1Label = new JLabel("");
  static JLabel event2Label = new JLabel("");
  static JLabel event3Label = new JLabel("");
  static JLabel event4Label = new JLabel("");
  static JLabel event5Label = new JLabel("");
  static String event1 = "", event2 = "", event3 = "", event4 = "", event5 = "";

  // Update GUI with time values in "times.json"
  public static void updateGUI() {
    try {
      FileReader timesReader = new FileReader("times.json");
      JSONTokener timesTokener = new JSONTokener(timesReader);
      JSONObject times = new JSONObject(timesTokener);

      String[] time = new String[5];

      JSONArray timesArray = times.getJSONArray("times");
      JSONArray stageArray = times.getJSONArray("stage");

      stage = stageArray.getJSONObject(0).getInt("level");

      time[0] = timesArray.getJSONObject(0).get("time1").toString();
      time[1] = timesArray.getJSONObject(0).get("time2").toString();
      time[2] = timesArray.getJSONObject(0).get("time3").toString();
      time[3] = timesArray.getJSONObject(0).get("time4").toString();
      time[4] = timesArray.getJSONObject(0).get("time5").toString();

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
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      logException(e);
    }

  }

  // Show the main GUI
  public static void createAndShowGUI() {
    try {
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
        logException(e);
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
          logException(e);
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
            updateGUI();
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
          getAreaID();

          try (

              FileReader reader = new FileReader("data.json")) {
            JSONTokener tokener = new JSONTokener(reader);
            JSONObject data = new JSONObject(tokener);
            String newAreaName = data.getString("name");
            areaIDButton.setText("Area: " + newAreaName);
            areaIDButton.setToolTipText(newAreaName);

          } catch (JSONException | IOException e1) {

            e1.printStackTrace();
            logException(e1);
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
                logException(f);
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
                logException(f);
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

          // Constraints for the save button
          GridBagConstraints buttonConstraints = new GridBagConstraints();
          buttonConstraints.gridx = 1;
          buttonConstraints.gridy = 1;
          buttonConstraints.weightx = 1.0;
          buttonConstraints.weighty = 1.0;
          buttonConstraints.anchor = GridBagConstraints.SOUTHEAST;
          buttonConstraints.insets = new Insets(10, 10, 10, 10);

          // Add save button to the dialog
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

          // Constraints for startup behaivour check box
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
            log("" + a);
          } catch (IOException e1) {
            e1.printStackTrace();
            logException(e1);
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
                logException(f);
              }
              dialog.dispose();
            }
          });

          dialog.setLocation(x, y);
          dialog.setResizable(false);
          dialog.setVisible(true);
        }

      });

      JButton helpButton = new JButton("Help");
      helpButton.setForeground(fontColor);
      helpButton.setBackground(Color.WHITE);
      helpButton.setBorderPainted(false);
      helpButton.setFocusPainted(false);
      helpButton.setMargin(new Insets(1, 0, 1, 1));
      helpButton.setHorizontalAlignment(SwingConstants.LEFT);

      helpButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          JPopupMenu popupMenu = new JPopupMenu();

          JMenuItem helpMenuItem = new JMenuItem("Help");
          JMenuItem aboutMenuItem = new JMenuItem("About");

          helpMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              File helpFile = new File("help.html");
              if (isInternetAvailable()) {
                openLink("sites.google.com/view/auto-shutdown/help-documentation");
              } else {
                try {
                  Desktop.getDesktop().open(helpFile);
                } catch (IOException e1) {
                  e1.printStackTrace();
                  logException(e1);
                }
              }
            }
          });

          aboutMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              JDialog about = new JDialog(frame, "About", true);
              about.setLayout(new GridBagLayout());
              about.setSize(650, 400);
              about.getContentPane().setBackground(backgroundColor);
              about.getContentPane().setForeground(fontColor);

              JLabel aboutLabel = new JLabel("About");
              aboutLabel.setFont(new Font("Dialog", Font.BOLD, 24));
              aboutLabel.setForeground(Color.WHITE);

              // Constraints for "general" label
              GridBagConstraints aboutLabelContstraints = new GridBagConstraints();
              aboutLabelContstraints.gridx = 1;
              aboutLabelContstraints.gridy = 1;
              aboutLabelContstraints.weightx = 1.0;
              aboutLabelContstraints.weighty = 1.0;
              aboutLabelContstraints.anchor = GridBagConstraints.NORTHWEST;
              aboutLabelContstraints.insets = new Insets(15, 20, 10, 10);

              JLabel infoLabel = new JLabel(versionInfo);
              infoLabel.setFont(new Font("Dialog", Font.PLAIN, 18));
              infoLabel.setForeground(Color.LIGHT_GRAY);

              // Constraints for "info" label
              GridBagConstraints infoLabelConstraints = new GridBagConstraints();
              infoLabelConstraints.gridx = 1;
              infoLabelConstraints.gridy = 1;
              aboutLabelContstraints.weightx = 1.0;
              infoLabelConstraints.weighty = 1.0;
              infoLabelConstraints.anchor = GridBagConstraints.NORTHWEST;
              infoLabelConstraints.insets = new Insets(50, 20, 10, 10);

              JButton hyperlinkButton = new JButton();
              hyperlinkButton.setUI(new BasicButtonUI());
              hyperlinkButton.setOpaque(false);

              GridBagConstraints hyperlinkButtonConstraints = new GridBagConstraints();
              hyperlinkButtonConstraints.gridx = 1;
              hyperlinkButtonConstraints.gridy = 1;
              hyperlinkButtonConstraints.weightx = 1.0;
              hyperlinkButtonConstraints.weighty = 1.0;
              hyperlinkButtonConstraints.anchor = GridBagConstraints.NORTHWEST;
              hyperlinkButtonConstraints.insets = new Insets(151, 85, 10, 10);

              hyperlinkButton.setPreferredSize(new Dimension(200, 22));
              hyperlinkButton.setFocusPainted(false);
              hyperlinkButton.setBorderPainted(false);
              hyperlinkButton.setBorder(BorderFactory.createEmptyBorder());

              hyperlinkButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                  openLink("github.com/vortexit07/autoshutdown");
                }
              });

              about.add(aboutLabel, aboutLabelContstraints);
              about.add(infoLabel, infoLabelConstraints);
              about.add(hyperlinkButton, hyperlinkButtonConstraints);

              int x = frame.getX() + (frame.getWidth() / 2) - about.getWidth() / 2;
              int y = frame.getY() + (frame.getHeight() / 2) - about.getHeight() / 2;
              about.setLocation(x, y);
              about.setResizable(false);
              about.setVisible(true);

            }
          });

          // Add menu items to the popup menu
          popupMenu.add(helpMenuItem);
          popupMenu.add(aboutMenuItem);

          // Show the popup menu at the button's location
          popupMenu.show(helpButton, 0, helpButton.getHeight());
        }
      });

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

      dropdownButton.setBounds(7, 20, 110, 20);
      toggleButton.setBounds((frame.getWidth() / 2) - imgW / 2, 150, imgW, imgH);
      areaIDButton.setBounds((frame.getWidth() / 2) - 150, 250, 300, 50);
      optionsBar.setBounds(-3, -8, 800, 27);
      events.setBounds(557, 150, 213, 300);

      frame.add(dropdownButton);
      frame.add(areaIDButton);
      frame.add(toggleButton);
      frame.add(events);
      frame.add(optionsBar);
      frame.setVisible(startMinimized);
      frame.setResizable(false);

    } catch (FileNotFoundException | AWTException e) {
      e.printStackTrace();
      logException(e);
    }
  } // End of createAndShowGUI

  static void openLink(String url) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      try {
        URI uri = new URI("http://" + url);
        Desktop.getDesktop().browse(uri);
      } catch (IOException | URISyntaxException e) {
        e.printStackTrace();
        logException(e);
      }
    } else {
      System.out.println("Opening links is not supported on this platform");
      // You can handle this case accordingly for your application
    }
  }

  // Enable "run on startup" by creating a shortcut in the "startup" folder to the
  // appliaction executable
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
      logException(e);
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
      logException(e);
    }
  }

  // Send a system notification
  public static void showNotification(String title, String message) {
    trayIcon.displayMessage(title, message, MessageType.INFO);
  }

  // Get the area ID of a search term
  public static void getAreaID() {
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

      if ((searchQuery != null) && (searchQuery.length() > 0) && (!searchQuery.equals(" "))
          && (!searchQuery.equals(""))) {

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
            logException(e);
          }

          getData();

        } else {
          JOptionPane.showMessageDialog(null, "An error occurred", "Error " + areaResponse.getStatus(), 0);
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
      logException(e);
    } catch (UnirestException e) {
      System.out.println("Something went wrong");
      JOptionPane.showMessageDialog(null, "An error occurred", "Error", 0);
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
      logException(e);
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
      logException(e);
    }
  } // End of createData

  // Create the config.json file
  public static void createConfig() {
    // Create a JSON object for the specified structure
    JSONObject configObject = new JSONObject();
    configObject.put("timeBefore", 1);
    configObject.put("runOnStartup", true);
    createStartupLink();
    configObject.put("startMinimized", false);

    // Write the JSON object to a file named "data.json"
    try (FileWriter fileWriter = new FileWriter("config.json")) {
      fileWriter.write(configObject.toString(4)); // Indent with 4 spaces for pretty printing
    } catch (IOException e) {
      e.printStackTrace();
      logException(e);
    }
  } // End of createConfig

  private static void shutdownMac() {
    String shutdownCommand = "sudo shutdown -h now"; // Command to shut down the system

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(shutdownCommand.split(" "));
      Process process = processBuilder.inheritIO().start();
      int exitCode = process.waitFor();

      if (exitCode == 0) {
        System.out.println("System shutdown initiated successfully.");
      } else {
        System.out.println("Failed to shut down the system.");
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      logException(e);
    }
  }

  private static void shutdownLinux() {
    String shutdownCommand = "sudo shutdown -P now"; // Command to shut down the system

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(shutdownCommand.split(" "));
      Process process = processBuilder.inheritIO().start();
      int exitCode = process.waitFor();

      if (exitCode == 0) {
        System.out.println("System shutdown initiated successfully.");
      } else {
        System.out.println("Failed to shut down the system.");
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      logException(e);
    }
  }

  private static String getOS() {
    String os = System.getProperty("os.name").toLowerCase();

    if (os.contains("win")) {
      return "windows";
    } else if (os.contains("mac")) {
      return "mac";
    } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
      return "linux";
    }
    return "unknown";
  }

  private static void log(String input) {

    String folderName = "logs";
    String fileName = LocalDate.now() + ".log";
    String path = folderName + File.separator + fileName;

    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss:SS");

    try {

      FileWriter fileWriter = new FileWriter(path, true);
      BufferedWriter writer = new BufferedWriter(fileWriter);

      writer.newLine();
      writer.write(LocalTime.now().format(timeFormatter) + " - " + input);
      writer.close();

    } catch (IOException e) {
      System.err.println("An error occurred while appending to the file: " + e.getMessage());
      log("An error occurred while appending to the file: " + e.getMessage());
    }
  }

  private static void logException(Throwable e) {
    String folderName = "logs";
    String fileName = LocalDate.now() + ".log";
    String path = folderName + "/" + fileName;

    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss:SS");

    try {
      FileWriter fileWriter = new FileWriter(path, true);
      BufferedWriter writer = new BufferedWriter(fileWriter);

      writer.newLine();
      writer.write(LocalTime.now().format(timeFormatter) + " - " + e.toString());
      writer.newLine();

      // Writing the stack trace to the log file
      for (StackTraceElement element : e.getStackTrace()) {
        writer.write(element.toString() + "\n");
        writer.newLine();
      }

      writer.close();
    } catch (IOException ex) {
      System.err.println("An error occurred while appending to the file: " + ex.getMessage());
      log("An error occurred while appending to the file: " + ex.getMessage());
    }
  }

  private static void createLog() {
    String folderName = "logs";
    String fileName = LocalDate.now() + ".log";
    String path = folderName + File.separator + fileName;

    try {
      File folder = new File(folderName);

      if (!folder.exists()) {
        folder.mkdir();
        System.out.println("Folder created: " + folder.getName());
      }

      File file = new File(path);

      if (file.createNewFile()) {
        System.out.println("File created: " + file.getName() + " in " + folder.getName());
      } else {
        System.out.println("File already exists in " + folder.getName());
      }
    } catch (IOException e) {
      System.err.println("An error occurred while creating the file: " + e.getMessage());
      log("An error occurred while creating the file: " + e.getMessage());
    }
  }

}