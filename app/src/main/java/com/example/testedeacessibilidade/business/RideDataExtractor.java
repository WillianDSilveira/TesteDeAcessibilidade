package com.example.testedeacessibilidade.business;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.example.testedeacessibilidade.data.RideOfferData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RideDataExtractor {

    private static final String TAG = "DEBUG_EXTRACTOR";
    private final RideOfferData data;

    public RideDataExtractor(RideOfferData data) {
        this.data = data;
    }

    /**
     * Inicia a extração de dados a partir do nó raiz.
     */
    public void extract(AccessibilityNodeInfo rootNode) {
        data.clear();
        traverseNodesAndCollect(rootNode);

        // Processa todos os textos coletados para somar KM e Minutos (Lógica de Regex)
        for (String text : data.allTextNodes) {
            extractAndSumMetrics(text);
        }

        extractAddresses();

        Log.i(TAG, "Extração Completa: Valor=" + data.corridaValorStr +
                ", KM=" + data.totalDistanciaKM +
                ", Tempo=" + data.totalTempoMinutos);
    }

    private void extractAddresses() {
        String lastTempoDistancia = null;
        boolean origemCaptured = false;

        for (int i = 0; i < data.allTextNodes.size(); i++) {
            String text = data.allTextNodes.get(i).replace("\u00A0", " ").trim();

            // Detecta padrões tipo "5 minutos (2.5 km)"
            if (text.matches(".*\\d+\\s*minuto(s)?\\s*\\(\\s*\\d+[.,]\\d+\\s*km\\s*\\).*")) {
                lastTempoDistancia = text;
            }
            // Se o último item foi um tempo/distância, o próximo é o endereço
            else if (lastTempoDistancia != null) {
                if (!origemCaptured) {
                    data.embarqueEndereco = text;
                    origemCaptured = true;
                } else {
                    data.destinoEndereco = text;
                }
                lastTempoDistancia = null;
            }
        }

        Log.i(TAG, "Endereço de embarque: " + data.embarqueEndereco);
        Log.i(TAG, "Endereço de destino: " + data.destinoEndereco);
    }


    // Método recursivo focado em coleta de texto e dados principais
    private void traverseNodesAndCollect(AccessibilityNodeInfo node) {

        if (node == null) return;
        CharSequence nodeText = node.getText();

        if (nodeText != null && nodeText.length() > 0) {
            String text = nodeText.toString();

            // 1. Adiciona todo o texto para a lista de extração robusta (KM e Minutos)
            data.allTextNodes.add(text);

            Log.i(TAG, "LISTA :" + data.allTextNodes );

            // 2. Captura Valor
            if (text.startsWith("R$")) {
                data.corridaValorStr = text;
            }
            // 3. Captura Nota
            else if (text.matches("^\\d[.,]\\d{1,2}$")) {
                data.passageiroNotaStr = text;
            }

            // 4. Captura Botão de Ação
            if (text.equals("Aceitar") || text.equals("Selecionar") && node.isClickable()) {
                // Obtém uma cópia do nó que persiste após o evento
                data.botaoAceitar = AccessibilityNodeInfo.obtain(node);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++)
            traverseNodesAndCollect(node.getChild(i));
    }

    // Extrai e soma todos os KM e Minutos encontrados em um texto
    private void extractAndSumMetrics(String text) {
        // Pattern para capturar X.Y km (ex: 3.1 km ou 4,2 km)
        Pattern kmPattern = Pattern.compile("(\\d{1,2}[.,]\\d)\\s*km");
        Matcher kmMatcher = kmPattern.matcher(text);
        while (kmMatcher.find()) {
            String kmStr = kmMatcher.group(1).replace(',', '.');
            try {
                data.totalDistanciaKM += Float.parseFloat(kmStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Erro ao parsear KM: " + kmStr);
            }
        }

        // Pattern para capturar Z minutos/minuto (ex: 8 minutos ou 9 minuto)
        Pattern minPattern = Pattern.compile("(\\d+)\\s*minuto(s)?");
        Matcher minMatcher = minPattern.matcher(text);
        while (minMatcher.find()) {
            try {
                data.totalTempoMinutos += Float.parseFloat(minMatcher.group(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Erro ao parsear Minutos: " + minMatcher.group(1));
            }
        }
    }
}
