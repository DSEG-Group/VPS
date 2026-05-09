package server;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.json.JSONObject;

public class startModel {
    private Map<String,Object> my_data = new HashMap<>();

    public startModel(Map<String,Object> data){
        this.my_data = data;
    }

    public startModel(){
        this.my_data.put("id", 1);
        this.my_data.put("mylist", List.of(1,2,3,4));
    }

    public void setData(Map<String,Object> modelData){
        this.my_data = modelData;
    }

    public void createJSON(String jsonPath){
        try{
            JSONObject jsonObject = new JSONObject(this.my_data);
            try (FileWriter writer = new FileWriter(
                    jsonPath,
                    StandardCharsets.UTF_8
            )) {
                writer.write(jsonObject.toString(2)); // 2 = pretty print
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    public double startPython(String jsonpath)throws Exception{
        String pythonExe = "/home/lyb/.conda/envs/vps_predict/bin/python";          // 或 python3 / 绝对路径
        String script = "/SSD00/lyb/test/VPS/src/server/runmodel.py";
        String param = jsonpath;
        ProcessBuilder pb = new ProcessBuilder(
                    pythonExe,
                    script,
                    param
        );
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.put("PYTHONPATH", "/SSD00/lyb/test/VPS/src");

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String line;
        String lastLine = null;

        while ((line = reader.readLine()) != null) {
            lastLine = line;
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Python exited with code " + exitCode);
        }

        System.out.println(lastLine.toString());
        return Double.parseDouble(lastLine.toString());
        
    }
    public static void main(String[] args) throws Exception {
        startModel test = new startModel();
        test.createJSON("/SSD00/lyb/test/VPS/test.json");
        test.startPython("/SSD00/lyb/test/VPS/test.json");
    }

}
