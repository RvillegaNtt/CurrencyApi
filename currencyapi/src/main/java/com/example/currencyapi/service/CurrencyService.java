package com.example.currencyapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
public class CurrencyService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode getJsonFromUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return objectMapper.readTree(response.toString());
    }
    public double getValueForDate(String  date,String yearlyCurrency) throws Exception {
        // Construir la URL para obtener datos para una fecha específica
        String urlString = String.format("https://165.227.94.139/api/"+yearlyCurrency+"/%s", date.toString());
        JsonNode node = getJsonFromUrl(urlString);
        return getValueFromSerie(node.path("serie"), 0);
    }

    private double getValueFromSerie(JsonNode node, int index) {
        if (node.isArray() && node.size() > index) {
            return node.get(index).path("valor").asDouble();
        }
        return 0.0;
    }

}