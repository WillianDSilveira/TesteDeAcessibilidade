package com.example.testedeacessibilidade.business;

import android.util.Log;
import com.example.testedeacessibilidade.data.DecisionResult;
import com.example.testedeacessibilidade.data.RideOfferData;
import com.example.testedeacessibilidade.data.RideMetrics;

import java.util.Locale;

/**
 * Responsável pela lógica de cálculo (R$/KM, R$/Hora) e decisão (Aceitar/Recusar).
 * Esta classe NÃO deve ter dependência da API de acessibilidade.
 */
public class RideDecisionMaker {

    private static final String TAG = "DEBUG_DECIDER";

    // --- Regras de Negócio ---
    private static final float MIN_RATE_PER_KM = 1.5f;
    private static final float MIN_RATE_PER_HOUR = 38.0f;

    /**
     * Converte a string de valor para float.
     */
    private float parseValueString(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) return 0.0f;
        // Remove tudo que não for número, vírgula ou ponto, e troca vírgula por ponto
        String cleanStr = valueStr.replaceAll("[^0-9,.]", "").trim().replace(",", ".");
        if (cleanStr.isEmpty()) return 0.0f;
        try {
            return Float.parseFloat(cleanStr);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Erro ao converter valor: " + valueStr);
            return 0.0f;
        }
    }

    /**
     * Calcula as métricas R$/KM e R$/Hora e aplica a lógica de decisão.
     */
    public DecisionResult decide(RideOfferData data) {
        float valorNumerico = parseValueString(data.corridaValorStr);
        RideMetrics metrics = new RideMetrics();

        // 1. Cálculo R$/KM
        if (data.totalDistanciaKM > 0) {
            metrics.ratePerKm = valorNumerico / data.totalDistanciaKM;
        }

        // 2. Cálculo R$/Hora
        if (data.totalTempoMinutos > 0) {
            metrics.ratePerHour = (valorNumerico / data.totalTempoMinutos) * 60.0f;
        }

        // --- Lógica de Decisão ---
        boolean meetsKmCriteria = metrics.ratePerKm >= MIN_RATE_PER_KM;
        boolean meetsHourCriteria = metrics.ratePerHour >= MIN_RATE_PER_HOUR;
        boolean recommendedToAccept = meetsKmCriteria && meetsHourCriteria;

        String reason;

        if (data.totalDistanciaKM == 0.0f && valorNumerico > 0) {
            reason = "Distância não detectada. Cálculo de taxa falhou.";
            recommendedToAccept = false;
        } else if (data.totalDistanciaKM == 0.0f && valorNumerico == 0) {
            reason = "Aguardando oferta completa.";
            recommendedToAccept = false;
        } else if (recommendedToAccept) {
            reason = "Critérios atendidos!";
        } else if (!meetsKmCriteria) {
            reason = String.format(Locale.getDefault(), "Baixo R$/Km (R$ %.2f)", metrics.ratePerKm);
        } else if (!meetsHourCriteria) {
            reason = String.format(Locale.getDefault(), "Baixo R$/Hora (R$ %.2f)", metrics.ratePerHour);
        } else {
            reason = "Critérios não atendidos.";
        }

        return new DecisionResult(recommendedToAccept, reason, metrics);
    }
}