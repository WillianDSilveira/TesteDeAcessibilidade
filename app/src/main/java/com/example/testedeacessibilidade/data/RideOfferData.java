package com.example.testedeacessibilidade.data;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO para armazenar os dados brutos extraídos diretamente da tela de acessibilidade.
 * Esta classe NÃO deve ter lógica de cálculo ou de decisão.
 */
public class RideOfferData {
    public String corridaValorStr = "R$ 0,00";
    public String passageiroNotaStr = "0,00";
    public float totalDistanciaKM = 0.0f;
    public float totalTempoMinutos = 0.0f;

    public String embarqueEndereco = "";
    public String destinoEndereco = "";

    // O nó é mantido aqui pois está relacionado ao dado de "ação"
    public AccessibilityNodeInfo botaoAceitar = null;

    // Lista para armazenar todos os textos dos nós para extração robusta de métricas
    public final List<String> allTextNodes = new ArrayList<>();


    public void clear() {
        corridaValorStr = "R$ 0,00";
        passageiroNotaStr = "0,00";
        totalDistanciaKM = 0.0f;
        totalTempoMinutos = 0.0f;

        // Reciclagem segura do AccessibilityNodeInfo (POO em contexto Android)
        if (botaoAceitar != null) {
            botaoAceitar.recycle();
            botaoAceitar = null;
        }
        allTextNodes.clear();
    }
}