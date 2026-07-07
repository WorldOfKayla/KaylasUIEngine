package org.foxesworld.engine.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class GPUInfo {

    @SuppressWarnings("unused")
    public String getPreferredGPU() {
        String[] gpus = getAvailableGPUs();

        for (String gpu : gpus) {
            String lowercaseGPU = gpu.toLowerCase();
            if (lowercaseGPU.contains("nvidia") || lowercaseGPU.contains("amd") || lowercaseGPU.contains("intel")) {
                return gpu;
            }
        }

        return gpus.length > 0 ? gpus[0] : "No GPU Found";
    }

    public static String[] getAvailableGPUs() {
        try {
            Process process = Runtime.getRuntime().exec("wmic path win32_videocontroller get caption");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            StringBuilder output = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
            String[] gpus = output.toString().split("\n");
            for (int i = 0; i < gpus.length; i++) {
                gpus[i] = gpus[i].trim();
            }

            return gpus;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return new String[0];
    }
}
