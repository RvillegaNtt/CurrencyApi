package com.example.currencyapi.controller;

import com.example.currencyapi.model.MonthlyData;
import com.example.currencyapi.service.CurrencyService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CurrencyController {

    /** Services*/
    @Autowired
    private CurrencyService currencyService;

    /** Logger*/
    private static Logger log = LoggerFactory.getLogger(CurrencyController.class);

    /** Botones*/

    @GetMapping("/compare")
    public String compare(@RequestParam("date") String date, Model model, @RequestParam("codigoCurrency") String codigoCurrency) {
        if (date == null || date.trim().isEmpty()) {
            model.addAttribute("errorMessage", "El campo de fecha es obligatorio."); return "index";
        }
        if(codigoCurrency == null || codigoCurrency.trim().isEmpty()){
            model.addAttribute("errorMessage", "El codigo de moneda ingresado esta vacio"); return "index";
        }
        //Validacion del tipo moneda
        if (!isValidCurrency(codigoCurrency)) {
            model.addAttribute("errorMessage", "El código ingresado no existe en el sistema actualmente");
            return "index";
        }
        try {
            JsonNode currentNode = currencyService.getJsonFromUrl("https://165.227.94.139/api");
            JsonNode previousNode = currencyService.getJsonFromUrl("https://165.227.94.139/api/"+codigoCurrency+"/" + date);
            JsonNode serieNode = previousNode.path("serie");

            double currentUtmValue = currentNode.path("utm").path("valor").asDouble();
            double previousUtmValue = 0.0;

            if (serieNode.isArray()) {
                for (JsonNode itemNode : serieNode) {
                    previousUtmValue = itemNode.path("valor").asDouble();
                }
            }
            double utmDifference = currentUtmValue - previousUtmValue;
            double utmPercentageChange = (utmDifference / previousUtmValue) * 100;

            model.addAttribute("currentUtmValue", String.format("%.2f", currentUtmValue));
            model.addAttribute("previousUtmValue", String.format("%.3f", previousUtmValue));
            model.addAttribute("utmDifference", String.format("%.3f", utmDifference));
            model.addAttribute("utmPercentageChange", String.format("%.3f", utmPercentageChange));
        } catch (IOException e) {
            log.error("Error en /compare: ", e);
            model.addAttribute("errorMessage", "Error al obtener los datos de comparación.");
        }
        return "compare";
    }


    @GetMapping("/monthly")
    public String monthly(@RequestParam("year") int year, @RequestParam("month") int month, Model model, @RequestParam("monthlyCurrency") String monthlyCurrency) {
        if ((Integer)year == null && (Integer)month == null) {
            model.addAttribute("errorMessage", "Campo año y mes son requeridos.");
            return "index";
        }
        if(month <= 12){
            model.addAttribute("errorMessage", "El mes no puede ser mayor al 12 diciembre");
            return "index";
        }
        if (!isValidCurrency(monthlyCurrency)) {
            model.addAttribute("errorMessage", "El código ingresado no existe en el sistema actualmente");
            return "index";
        }
        try {
            // Determinar el primer y último día del mes
            LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
            LocalDate lastDayOfMonth = firstDayOfMonth.withDayOfMonth(firstDayOfMonth.lengthOfMonth());

            // Definir el formateador para el formato dd-MM-yyyy
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

            // Convertir las fechas a formato dd-MM-yyyy
            String firstDayStr = firstDayOfMonth.format(formatter);
            String lastDayStr = lastDayOfMonth.format(formatter);

            // Obtener datos para el primer y último día del mes
            double firstDayValue = currencyService.getValueForDate(firstDayStr,monthlyCurrency);
            double lastDayValue = currencyService.getValueForDate(lastDayStr,monthlyCurrency);

            // Calcular diferencia y porcentaje de variación
            double difference = lastDayValue - firstDayValue;
            double percentageChange = (firstDayValue != 0) ? (difference / firstDayValue) * 100 : 0;

            // Obtener datos para todo el mes
            List<Double> monthlyValues = getValuesForMonth(year, month,monthlyCurrency);

            // Calcular promedio y moda
            double average = calculateAverage(monthlyValues);
            double mode = calculateMode(monthlyValues);

            // Agregar al modelo
            model.addAttribute("firstDayValue", String.format("%.2f", firstDayValue));
            model.addAttribute("lastDayValue", String.format("%.2f", lastDayValue));
            model.addAttribute("difference", String.format("%.2f", difference));
            model.addAttribute("percentageChange", String.format("%.2f", percentageChange));
            model.addAttribute("average", String.format("%.2f", average));
            model.addAttribute("mode", String.format("%.2f", mode));

        } catch (IOException e) {
            log.error("Error de entrada/salida: ", e);
            model.addAttribute("errorMessage", "Error al obtener los datos.");
        }  catch (Exception e) {
            log.error("Error inesperado: ", e);
            model.addAttribute("errorMessage", "Ocurrió un error inesperado.");
        }
        return "monthly";
    }

    @GetMapping("/yearly")
    public String yearly(@RequestParam("year") int year, Model model, @RequestParam("yearlyCurrency") String yearlyCurrency) {
        if ((Integer)year == null) {
            model.addAttribute("errorMessage", "Campo año es requerido");
            return "index";
        }
        if (!isValidCurrency(yearlyCurrency)) {
            model.addAttribute("errorMessage", "El código ingresado no existe en el sistema actualmente");
            return "index";
        }
        try {
            List<MonthlyData> monthlyDataList = new ArrayList<>();
            double previousMonthAverage = 0;

            for (int month = 1; month <= 12; month++) {
                double average = getMonthlyAverage(year, month,yearlyCurrency);
                double percentageChange = (previousMonthAverage != 0)
                        ? ((average - previousMonthAverage) / previousMonthAverage) * 100
                        : 0;

                monthlyDataList.add(new MonthlyData(month, average, percentageChange));
                previousMonthAverage = average;
            }

            model.addAttribute("monthlyDataList", monthlyDataList.stream()
                    .map(data -> new MonthlyData(
                            data.getMonth(),
                            String.format("%.2f", Double.parseDouble(data.getAverage().replace(',', '.'))),        // Conversión y formateo
                            String.format("%.3f", Double.parseDouble(data.getPercentageChange().replace(',', '.'))) // Conversión y formateo
                    ))
                    .collect(Collectors.toList()));

        } catch (IOException e) {
            log.error("Error de entrada/salida: ", e);
            model.addAttribute("errorMessage", "Error al obtener los datos.");
        } catch (Exception e) {
            log.error("Error inesperado: ", e);
            model.addAttribute("errorMessage", "Ocurrió un error inesperado.");
        }
        return "yearly";
    }

    @GetMapping("/index")
    public String index(Model model) {
        try {
            // Set up the trust manager to trust all certificates
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Optional: Set up hostname verification to trust all
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            // Now make the request
            String urlString = "https://165.227.94.139/api";
            JsonNode jsonNode = currencyService.getJsonFromUrl(urlString);

            // Extract values from the JSON
            double ufValor = jsonNode.path("uf").path("valor").asDouble();
            double dollarValor = jsonNode.path("dolar").path("valor").asDouble();
            double euroValor = jsonNode.path("euro").path("valor").asDouble();
            double utmValor = jsonNode.path("utm").path("valor").asDouble();

            // Add the extracted values to the model
            model.addAttribute("ufValor", ufValor);
            model.addAttribute("dollarValor", dollarValor);
            model.addAttribute("euroValor", euroValor);
            model.addAttribute("utmValor", utmValor);
        } catch (IOException e) {
            log.error("Error de entrada/salida: ", e);
            model.addAttribute("errorMessage", "Error al obtener los datos.");
        }  catch (Exception e) {
            log.error("Error inesperado: ", e);
            model.addAttribute("errorMessage", "Ocurrió un error inesperado.");
        }
        return "index";
    }





    private List<Double> getValuesForMonth(int year, int month, String monthlyCurrency) throws Exception {
        List<Double> values = new ArrayList<>();
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        while (!startDate.isAfter(endDate)) {
            String dateStr = startDate.format(formatter);
            values.add(currencyService.getValueForDate(dateStr,monthlyCurrency));
            startDate = startDate.plusDays(1);
        }
        return values;
    }

    private double calculateAverage(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private double calculateMode(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        Map<Double, Integer> frequencyMap = new HashMap<>();
        for (double value : values) {
            frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1);
        }

        int maxFrequency = Collections.max(frequencyMap.values());
        List<Double> modes = new ArrayList<>();
        for (Map.Entry<Double, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getValue() == maxFrequency) {
                modes.add(entry.getKey());
            }
        }
        // Devuelve el primer valor de moda encontrado
        return modes.isEmpty() ? 0.0 : modes.get(0);
    }

    private double getMonthlyAverage(int year, int month, String yearlyCurrency) throws Exception {
        List<Double> values = new ArrayList<>();
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        while (!startDate.isAfter(endDate)) {
            String dateStr = startDate.format(formatter);
            values.add(currencyService.getValueForDate(dateStr,yearlyCurrency));
            startDate = startDate.plusDays(1);
        }
        values.removeIf(value -> value == 0.0);
        Double valueTotal = 0.0;
        for(Double x : values){
            valueTotal+= x.intValue();
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / values.size();
        System.out.println("El promedio es: " + average);
        System.out.println("El promedio es: " + valueTotal);

        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public Boolean isValidCurrency(String typeCurrency){
        List<String> tipoCodigos = new ArrayList<String>(Arrays.asList("euro","dolar","uf","utm"));
        boolean isValidCodigo = false;

        for (String nombre : tipoCodigos) {
            if (nombre.equals(typeCurrency)) {
                isValidCodigo = true;
                break;
            }
        }
        return isValidCodigo;
    }
}