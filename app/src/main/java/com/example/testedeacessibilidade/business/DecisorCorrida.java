package com.example.testedeacessibilidade.business;

import android.util.Log;
// Mantendo os nomes dos imports originais para não quebrar dependências
import com.example.testedeacessibilidade.data.DadosOfertaCorrida;
import com.example.testedeacessibilidade.data.ResultadoDecisao;
import com.example.testedeacessibilidade.data.DadosOfertaCorrida;
import com.example.testedeacessibilidade.data.MetricasCorrida;

import java.util.Locale;

/**
 * Responsável pela lógica de cálculo (R$/KM, R$/Hora) e decisão (Aceitar/Recusar).
 * Esta classe NÃO deve ter dependência da API de acessibilidade.
 */
public class DecisorCorrida { // Tradução do nome da classe

    private static final String TAG_DEPURACAO = "DEBUG_DECIDER"; // Tradução da variável

    // --- Regras de Negócio ---
    private static final float TAXA_MINIMA_POR_KM = 1.5f; // Tradução da variável
    private static final float TAXA_MINIMA_POR_HORA = 38.0f; // Tradução da variável

    /**
     * Converte a string de valor para float.
     */
    private float parsearStringValor(String stringValor) { // Tradução do método e parâmetro
        if (stringValor == null || stringValor.isEmpty()) return 0.0f;
        // Remove tudo que não for número, vírgula ou ponto, e troca vírgula por ponto
        String stringLimpa = stringValor.replaceAll("[^0-9,.]", "").trim().replace(",", "."); // Tradução da variável
        if (stringLimpa.isEmpty()) return 0.0f;
        try {
            return Float.parseFloat(stringLimpa);
        } catch (NumberFormatException e) {
            Log.e(TAG_DEPURACAO, "Erro ao converter valor: " + stringValor);
            return 0.0f;
        }
    }

    /**
     * Calcula as métricas R$/KM e R$/Hora e aplica a lógica de decisão.
     */
    public ResultadoDecisao decidir(DadosOfertaCorrida dados) { // Tradução do método e parâmetro
        float valorNumerico = parsearStringValor(dados.corridaValorStr); // Tradução da variável e chamada de método
        MetricasCorrida metricas = new MetricasCorrida(); // Tradução da variável

        // 1. Cálculo R$/KM
        if (dados.totalDistanciaKM > 0) {
            metricas.taxaPorKm = valorNumerico / dados.totalDistanciaKM;
        }

        // 2. Cálculo R$/Hora
        if (dados.totalTempoMinutos > 0) {
            metricas.taxaPorHora = (valorNumerico / dados.totalTempoMinutos) * 60.0f;
        }

        // --- Lógica de Decisão ---
        boolean atendeCriterioKm = metricas.taxaPorKm >= TAXA_MINIMA_POR_KM; // Tradução da variável
        boolean atendeCriterioHora = metricas.taxaPorHora >= TAXA_MINIMA_POR_HORA; // Tradução da variável
        boolean recomendadoAceitar = atendeCriterioKm && atendeCriterioHora; // Tradução da variável

        String razao; // Tradução da variável

        if (dados.totalDistanciaKM == 0.0f && valorNumerico > 0) {
            razao = "Distância não detectada. Cálculo de taxa falhou.";
            recomendadoAceitar = false;
        } else if (dados.totalDistanciaKM == 0.0f && valorNumerico == 0) {
            razao = "Aguardando oferta completa.";
            recomendadoAceitar = false;
        } else if (recomendadoAceitar) {
            razao = "Critérios atendidos!";
        } else if (!atendeCriterioKm) {
            razao = String.format(Locale.getDefault(), "Baixo R$/Km (R$ %.2f)", metricas.taxaPorKm);
        } else if (!atendeCriterioHora) {
            razao = String.format(Locale.getDefault(), "Baixo R$/Hora (R$ %.2f)", metricas.taxaPorHora);
        } else {
            razao = "Critérios não atendidos.";
        }

        return new ResultadoDecisao(recomendadoAceitar, razao, metricas);
    }
}