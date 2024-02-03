package autoshutdown;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONObject;

public class UpdateJson {
    public static void write(String fileName, String key, Object newValue) throws IOException {

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

        config.put(key, newValue);

        // Write the updated JSON back to the file
        FileWriter writer = new FileWriter(fileName);
        writer.write(config.toString(4));
        writer.close();

    }
}
