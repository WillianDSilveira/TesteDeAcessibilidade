package com.example.testedeacessibilidade.business;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

// Mantendo o nome do import original para não quebrar dependências
import com.example.testedeacessibilidade.data.DadosOfertaCorrida;
import com.example.testedeacessibilidade.data.DadosOfertaCorrida;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtratorDadosCorrida { // Tradução do nome da classe

    private static final String TAG_DEPURACAO = "DEBUG_EXTRACTOR";
    private final DadosOfertaCorrida dados; // Tradução do nome da variável

    public ExtratorDadosCorrida(DadosOfertaCorrida dados) {
        this.dados = dados;
    }

    /**
     * Inicia a extração de dados a partir do nó raiz.
     */
    public void extrair(AccessibilityNodeInfo noRaiz) { // Tradução do método
        dados.clear();
        percorrerNosEColetar(noRaiz);

        // Processa todos os textos coletados para somar KM e Minutos (Lógica de Regex)
        for (String texto : dados.allTextNodes) {
            extrairESomarMetricas(texto);
        }

        extrairEnderecos();

        Log.i(TAG_DEPURACAO, "Extração Completa: Valor=" + dados.corridaValorStr +
                ", KM=" + dados.totalDistanciaKM +
                ", Tempo=" + dados.totalTempoMinutos);
    }

    private void extrairEnderecos() { // Tradução do método
        String ultimoTempoDistancia = null;
        boolean origemCapturada = false;

        for (int i = 0; i < dados.allTextNodes.size(); i++) {
            String texto = dados.allTextNodes.get(i).replace("\u00A0", " ").trim();

            // Detecta padrões tipo "5 minutos (2.5 km)"
            if (texto.matches(".*\\d+\\s*minuto(s)?\\s*\\(\\s*\\d+[.,]\\d+\\s*km\\s*\\).*")) {
                ultimoTempoDistancia = texto;
            }
            // Se o último item foi um tempo/distância, o próximo é o endereço
            else if (ultimoTempoDistancia != null) {
                if (!origemCapturada) {
                    dados.embarqueEndereco = texto;
                    origemCapturada = true;
                } else {
                    dados.destinoEndereco = texto;
                }
                ultimoTempoDistancia = null;
            }
        }

        Log.i(TAG_DEPURACAO, "Endereço de embarque: " + dados.embarqueEndereco);
        Log.i(TAG_DEPURACAO, "Endereço de destino: " + dados.destinoEndereco);
    }


    // Método recursivo focado em coleta de texto e dados principais
    private void percorrerNosEColetar(AccessibilityNodeInfo no) { // Tradução do método

        if (no == null) return;
        CharSequence textoNo = no.getText(); // Tradução da variável

        if (textoNo != null && textoNo.length() > 0) {
            String texto = textoNo.toString();

            // 1. Adiciona todo o texto para a lista de extração robusta (KM e Minutos)
            dados.allTextNodes.add(texto);

            Log.i(TAG_DEPURACAO, "LISTA :" + dados.allTextNodes );

            // 2. Captura Valor
            if (texto.startsWith("R$")) {
                dados.corridaValorStr = texto;
            }
            // 3. Captura Nota
            else if (texto.matches("^\\d[.,]\\d{1,2}$")) {
                dados.passageiroNotaStr = texto;
            }

            // 4. Captura Botão de Ação
            if (texto.equals("Aceitar") || texto.equals("Selecionar") && no.isClickable()) {
                // Obtém uma cópia do nó que persiste após o evento
                dados.botaoAceitar = AccessibilityNodeInfo.obtain(no);
            }
        }

        for (int i = 0; i < no.getChildCount(); i++)
            percorrerNosEColetar(no.getChild(i)); // Chamada recursiva traduzida
    }

    // Extrai e soma todos os KM e Minutos encontrados em um texto
    private void extrairESomarMetricas(String texto) { // Tradução do método
        // Pattern para capturar X.Y km (ex: 3.1 km ou 4,2 km)
        Pattern padraoKm = Pattern.compile("(\\d{1,2}[.,]\\d)\\s*km"); // Tradução da variável
        Matcher comparadorKm = padraoKm.matcher(texto); // Tradução da variável
        while (comparadorKm.find()) {
            String strKm = comparadorKm.group(1).replace(',', '.'); // Tradução da variável
            try {
                dados.totalDistanciaKM += Float.parseFloat(strKm);
            } catch (NumberFormatException e) {
                Log.e(TAG_DEPURACAO, "Erro ao parsear KM: " + strKm);
            }
        }

        // Pattern para capturar Z minutos/minuto (ex: 8 minutos ou 9 minuto)
        Pattern padraoMin = Pattern.compile("(\\d+)\\s*minuto(s)?"); // Tradução da variável
        Matcher comparadorMin = padraoMin.matcher(texto); // Tradução da variável
        while (comparadorMin.find()) {
            try {
                dados.totalTempoMinutos += Float.parseFloat(comparadorMin.group(1));
            } catch (NumberFormatException e) {
                Log.e(TAG_DEPURACAO, "Erro ao parsear Minutos: " + comparadorMin.group(1));
            }
        }
    }
}