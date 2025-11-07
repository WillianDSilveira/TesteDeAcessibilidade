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

// A classe foi renomeada para RouteVerifier (sem 'r') para consistência,
// mas funciona mesmo se você mantiver RouterVerifier, desde que corrija os imports.
public class RouterVerifier {

    private static final String TAG = "DEBUG_API";
    // ⭐ ATENÇÃO: SUBSTITUA ESTA CHAVE PELA SUA CHAVE DO GOOGLE MAPS ⭐
    private static final String API_KEY = "AIzaSyBGRJqe2kYur173c7bxRL6eCGUdVYfo4Qg";

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface RouteCallback {
        void onResult(float distanceKm, float durationMinutes);
        void onError(String message);
    }

    public void verifyRoute(String driverLocation, String embarqueAddress, String destinoAddress, RouteCallback callback) {

        executor.execute(() -> {
            try {
                String origin = URLEncoder.encode(driverLocation, "UTF-8"); // Ponto A (Motorista)
                String embarque = URLEncoder.encode(embarqueAddress, "UTF-8"); // Ponto B (Embarque)
                String destination = URLEncoder.encode(destinoAddress, "UTF-8"); // Ponto C (Destino)

                // 🔴 Lógica de Matriz 2x2:
                // Origins: Motorista (A) e Embarque (B)
                String originsParam = origin + "|" + embarque;
                // Destinations: Embarque (B) e Destino (C)
                String destinationsParam = embarque + "|" + destination;

                // O JSON de resposta terá 4 elementos (A->B, A->C, B->B, B->C)
                String urlString = String.format(
                        "https://maps.googleapis.com/maps/api/distancematrix/json?origins=%s&destinations=%s&mode=driving&departure_time=now&key=%s",
                        originsParam, destinationsParam, API_KEY);

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    parseAndReturnResult(response.toString(), callback);

                } else {
                    // Adicionando leitura do erro se a requisição falhar
                    try (BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String errorLine;
                        while ((errorLine = err.readLine()) != null) errorResponse.append(errorLine);
                        Log.e(TAG, "API Error Response: " + errorResponse.toString());
                    }
                    handler.post(() -> callback.onError("Erro na API: Código " + responseCode));
                }

                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Exceção na chamada da API: " + e.getMessage(), e);
                handler.post(() -> callback.onError("Exceção de rede: " + e.getMessage()));
            }
        });
    }

    private void parseAndReturnResult(String jsonResponse, RouteCallback callback) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            // 🚩 LOG DO JSON para debug em caso de falha de parsing
            Log.i(TAG, "JSON Response (Para Debug): " + jsonResponse);

            // Verificação de Status Geral da API (Deve ser "OK")
            String statusGeral = json.getString("status");
            if (!"OK".equals(statusGeral)) {
                handler.post(() -> callback.onError("Status geral da API não é OK: " + statusGeral));
                return;
            }

            JSONArray rows = json.getJSONArray("rows");

            // Esperamos duas linhas: [0] para Origens 'A' e 'B', [1] para Destinos 'B' e 'C'.
            if (rows.length() < 2) {
                handler.post(() -> callback.onError("A API não retornou as 2 linhas esperadas (Matriz 2x2)."));
                return;
            }

            // A matriz de elementos é:
            // Row 0 (Origem A): [A->B, A->C]
            // Row 1 (Origem B): [B->B, B->C]

            // 1. Segmento A -> B (Motorista -> Embarque) - Elemento [0][0]
            JSONObject elementAB = rows.getJSONObject(0).getJSONArray("elements").getJSONObject(0);

            // 2. Segmento B -> C (Embarque -> Destino) - Elemento [1][1]
            // Nota: O resultado [1][0] seria B->B (0 km/0 min)
            JSONObject elementBC = rows.getJSONObject(1).getJSONArray("elements").getJSONObject(1);

            double totalDistanceMeters = 0;
            double totalDurationSeconds = 0;

            // --- Processa A -> B ---
            String statusAB = elementAB.getString("status");
            if ("OK".equals(statusAB)) {
                totalDistanceMeters += elementAB.getJSONObject("distance").getDouble("value");
                totalDurationSeconds += elementAB.has("duration_in_traffic")
                        ? elementAB.getJSONObject("duration_in_traffic").getDouble("value")
                        : elementAB.getJSONObject("duration").getDouble("value");
            } else {
                handler.post(() -> callback.onError("Status do segmento A->B não é OK: " + statusAB));
                return;
            }

            // --- Processa B -> C ---
            String statusBC = elementBC.getString("status");
            if ("OK".equals(statusBC)) {
                totalDistanceMeters += elementBC.getJSONObject("distance").getDouble("value");
                totalDurationSeconds += elementBC.has("duration_in_traffic")
                        ? elementBC.getJSONObject("duration_in_traffic").getDouble("value")
                        : elementBC.getJSONObject("duration").getDouble("value");
            } else {
                handler.post(() -> callback.onError("Status do segmento B->C não é OK: " + statusBC));
                return;
            }

            float distanceKm = (float) (totalDistanceMeters / 1000.0);
            float durationMinutes = (float) (totalDurationSeconds / 60.0);

            // Resultado consolidado A->B->C
            handler.post(() -> callback.onResult(distanceKm, durationMinutes));

        } catch (Exception e) {
            Log.e(TAG, "Erro fatal ao parsear JSON e somar rotas: " + e.getMessage(), e);
            handler.post(() -> callback.onError("Erro fatal ao parsear JSON e somar rotas: " + e.getMessage()));
        }
    }
}