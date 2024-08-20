package com.example.currencyapi.model;

public class MonthlyData {
    private int month;
    private String average;  // Cambiar a String para los datos formateados
    private String percentageChange;  // Cambiar a String para los datos formateados

    // Constructor para datos numéricos
    public MonthlyData(int month, double average, double percentageChange) {
        this.month = month;
        this.average = String.format("%.2f", average);
        this.percentageChange = String.format("%.3f", percentageChange);
    }

    // Constructor para datos formateados (si es necesario)
    public MonthlyData(int month, String average, String percentageChange) {
        this.month = month;
        this.average = average;
        this.percentageChange = percentageChange;
    }

    // Getters y Setters
    public int getMonth() { return month; }
    public String getAverage() { return average; }
    public String getPercentageChange() { return percentageChange; }
}

