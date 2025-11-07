package com.example.testedeacessibilidade.data;

public class DecisionResult {
    public boolean recommendedToAccept;
    public String reason;
    public RideMetrics metrics;

    public float apiTotalDistanciaKM = 0.0f;
    public float apiTotalTempoMinutos = 0.0f;

    public DecisionResult(boolean recommendedToAccept, String reason, RideMetrics metrics) {
        this.recommendedToAccept = recommendedToAccept;
        this.reason = reason;
        this.metrics = metrics;
    }

    // Método para definir os dados da API
    public void setApiResults(float km, float min) {
        this.apiTotalDistanciaKM = km;
        this.apiTotalTempoMinutos = min;
    }

    // Getters para a diferença (para usar no overlay)
    public float getKmDifference() {
        return apiTotalDistanciaKM - metrics.totalDistanciaKM; // metrics.totalDistanciaKM não existe, mas sim data.totalDistanciaKM. Precisaremos passar o DTO para o Decisor.
    }

    public float getMinDifference() {
        return apiTotalTempoMinutos - metrics.totalTempoMinutos; // O mesmo aqui.
    }
}