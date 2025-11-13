package com.example.testedeacessibilidade.business;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// A classe foi renomeada para VerificadorRota (mantendo o nome 'RouterVerifier' no import para compatibilidade externa)
public class VerificadorRota {

    private static final String TAG_DEPURACAO = "DEBUG_API";
    // ⭐ ATENÇÃO: SUBSTITUA ESTA CHAVE PELA SUA CHAVE DO GOOGLE MAPS ⭐
    private static final String CHAVE_API = "AIzaSyBGRJqe2kYur173c7bxRL6eCGUdVYfo4Qg";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler manipulador = new Handler(Looper.getMainLooper());

    public interface RouteCallback { // Mantendo o nome da interface em inglês por ser interna e ser usada no `MyAcessibilityService`
        void onResult(float distanciaKm, float duracaoMinutos);
        void onError(String mensagem);
    }

    public void verificarRota(String localizacaoMotorista, String enderecoEmbarque, String enderecoDestino, RouteCallback callback) {

        executor.execute(() -> {
            try {
                String origem = URLEncoder.encode(localizacaoMotorista, "UTF-8"); // Ponto A (Motorista)
                String embarque = URLEncoder.encode(enderecoEmbarque, "UTF-8"); // Ponto B (Embarque)
                String destino = URLEncoder.encode(enderecoDestino, "UTF-8"); // Ponto C (Destino)

                // 🔴 Lógica de Matriz 2x2:
                // Origins: Motorista (A) e Embarque (B)
                String parametrosOrigens = origem + "|" + embarque;
                // Destinations: Embarque (B) e Destino (C)
                String parametrosDestinos = embarque + "|" + destino;

                // O JSON de resposta terá 4 elementos (A->B, A->C, B->B, B->C)
                String stringUrl = String.format(
                        "https://maps.googleapis.com/maps/api/distancematrix/json?origins=%s&destinations=%s&mode=driving&departure_time=now&key=%s",
                        parametrosOrigens, parametrosDestinos, CHAVE_API);

                URL url = new URL(stringUrl);
                HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
                conexao.setRequestMethod("GET");
                conexao.setConnectTimeout(5000);

                int codigoResposta = conexao.getResponseCode();

                if (codigoResposta == HttpURLConnection.HTTP_OK) {
                    BufferedReader leitor = new BufferedReader(new InputStreamReader(conexao.getInputStream()));
                    StringBuilder resposta = new StringBuilder();
                    String linha;
                    while ((linha = leitor.readLine()) != null) resposta.append(linha);
                    leitor.close();

                    parsearERetornarResultado(resposta.toString(), callback);

                } else {
                    // Adicionando leitura do erro se a requisição falhar
                    try (BufferedReader leitorErro = new BufferedReader(new InputStreamReader(conexao.getErrorStream()))) {
                        StringBuilder respostaErro = new StringBuilder();
                        String linhaErro;
                        while ((linhaErro = leitorErro.readLine()) != null) respostaErro.append(linhaErro);
                        Log.e(TAG_DEPURACAO, "Resposta de Erro da API: " + respostaErro.toString());
                    }
                    manipulador.post(() -> callback.onError("Erro na API: Código " + codigoResposta));
                }

                conexao.disconnect();

            } catch (Exception e) {
                Log.e(TAG_DEPURACAO, "Exceção na chamada da API: " + e.getMessage(), e);
                manipulador.post(() -> callback.onError("Exceção de rede: " + e.getMessage()));
            }
        });
    }

    private void parsearERetornarResultado(String respostaJson, RouteCallback callback) {
        try {
            JSONObject json = new JSONObject(respostaJson);
            // 🚩 LOG DO JSON para debug em caso de falha de parsing
            Log.i(TAG_DEPURACAO, "Resposta JSON (Para Debug): " + respostaJson);

            // Verificação de Status Geral da API (Deve ser "OK")
            String statusGeral = json.getString("status");
            if (!"OK".equals(statusGeral)) {
                manipulador.post(() -> callback.onError("Status geral da API não é OK: " + statusGeral));
                return;
            }

            JSONArray linhas = json.getJSONArray("rows");

            // Esperamos duas linhas: [0] para Origens 'A' e 'B', [1] para Destinos 'B' e 'C'.
            if (linhas.length() < 2) {
                manipulador.post(() -> callback.onError("A API não retornou as 2 linhas esperadas (Matriz 2x2)."));
                return;
            }

            // A matriz de elementos é:
            // Linha 0 (Origem A): [A->B, A->C]
            // Linha 1 (Origem B): [B->B, B->C]

            // 1. Segmento A -> B (Motorista -> Embarque) - Elemento [0][0]
            JSONObject elementoAB = linhas.getJSONObject(0).getJSONArray("elements").getJSONObject(0);

            // 2. Segmento B -> C (Embarque -> Destino) - Elemento [1][1]
            // Nota: O resultado [1][0] seria B->B (0 km/0 min)
            JSONObject elementoBC = linhas.getJSONObject(1).getJSONArray("elements").getJSONObject(1);

            double distanciaTotalMetros = 0;
            double duracaoTotalSegundos = 0;

            // --- Processa A -> B ---
            String statusAB = elementoAB.getString("status");
            if ("OK".equals(statusAB)) {
                distanciaTotalMetros += elementoAB.getJSONObject("distance").getDouble("value");
                duracaoTotalSegundos += elementoAB.has("duration_in_traffic")
                        ? elementoAB.getJSONObject("duration_in_traffic").getDouble("value")
                        : elementoAB.getJSONObject("duration").getDouble("value");
            } else {
                manipulador.post(() -> callback.onError("Status do segmento A->B não é OK: " + statusAB));
                return;
            }

            // --- Processa B -> C ---
            String statusBC = elementoBC.getString("status");
            if ("OK".equals(statusBC)) {
                distanciaTotalMetros += elementoBC.getJSONObject("distance").getDouble("value");
                duracaoTotalSegundos += elementoBC.has("duration_in_traffic")
                        ? elementoBC.getJSONObject("duration_in_traffic").getDouble("value")
                        : elementoBC.getJSONObject("duration").getDouble("value");
            } else {
                manipulador.post(() -> callback.onError("Status do segmento B->C não é OK: " + statusBC));
                return;
            }

            float distanciaKm = (float) (distanciaTotalMetros / 1000.0);
            float duracaoMinutos = (float) (duracaoTotalSegundos / 60.0);

            // Resultado consolidado A->B->C
            manipulador.post(() -> callback.onResult(distanciaKm, duracaoMinutos));

        } catch (Exception e) {
            Log.e(TAG_DEPURACAO, "Erro fatal ao parsear JSON e somar rotas: " + e.getMessage(), e);
            manipulador.post(() -> callback.onError("Erro fatal ao parsear JSON e somar rotas: " + e.getMessage()));
        }
    }
}