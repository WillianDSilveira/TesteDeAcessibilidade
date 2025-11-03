package com.example.testedeacessibilidade;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater; // Import necessário para inflar o layout XML
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale; // Import necessário para formatação correta de floats

public class MyAcessibilityService extends AccessibilityService {

    private static final String TAG = "DEBUG_MONITOR";
    // Altere este nome de pacote para o aplicativo alvo, se necessário.
    private static final String TARGET_PACKAGE_NAME = "com.ubercab.driver";
    private static final String CHANNEL_ID = "acessibilidade_monitor";
    private static final int NOTIFICATION_ID = 101;

    // --- Variáveis de Controle e Debounce ---
    private long lastProcessedTime = 0;
    private static final long DEBOUNCE_INTERVAL_MS = 1000;
    private String lastProcessedContentHash = "";

    // --- Dados da corrida ---
    private String corridaValorStr = "R$ 0,00";
    private String passageiroNotaStr = "0,00";
    private AccessibilityNodeInfo botaoSelecionar = null;

    private float totalDistanciaKM = 0.0f;
    private float totalTempoMinutos = 0.0f;
    private float ratePerKm = 0.0f;
    private float ratePerHour = 0.0f; // Taxa por Hora adicionada

    // Lista para armazenar todos os textos dos nós para extração robusta de métricas
    private final List<String> allTextNodes = new ArrayList<>();

    private final Handler handler = new Handler();
    private FloatingOverlay floatingOverlay;

    // Variável para rastrear se o overlay foi mostrado com sucesso
    private boolean overlaySuccessfullyShown = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Serviço de Monitoramento CONECTADO e ativo.");

        // 🔹 Inicia o foreground service
        startForegroundServiceCompat();

        // 🔹 Inicializa o overlay. A permissão será checada antes de mostrar.
        floatingOverlay = new FloatingOverlay(this);
        Toast.makeText(this, "Serviço de acessibilidade ativo.", Toast.LENGTH_SHORT).show();

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Permissão de sobreposição ausente. Certifique-se de que a MainActivity a solicite.");
        }
    }

    // ============================================================
    // 🔹 FOREGROUND SERVICE (Notificação persistente)
    // ============================================================
    private void startForegroundServiceCompat() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Cria o canal de notificação (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitor de Acessibilidade",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Serviço ativo de monitoramento e análise de corridas");
            channel.enableLights(false);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }

        // Cria a notificação persistente
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitor ativo")
                .setContentText("O serviço de monitoramento está em execução.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        // Usa o FOREGROUND_SERVICE_TYPE_DATA_SYNC (API 29+) conforme declarado no Manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

    }

    // ============================================================
    // 🔹 Overlay (Janela flutuante)
    // ============================================================
    private class FloatingOverlay {
        private WindowManager windowManager;
        private View floatingView;
        public boolean isShowing = false;

        public FloatingOverlay(Context context) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        public void show(boolean recommendedToAccept, String reason) {
            if (!Settings.canDrawOverlays(MyAcessibilityService.this)) {
                Log.w(TAG, "Permissão de Sobreposição necessária, mas não concedida. Disparando Toast.");
                Toast.makeText(MyAcessibilityService.this,
                        "Permissão de Sobreposição Necessária! Conceda e tente novamente.",
                        Toast.LENGTH_LONG).show();
                overlaySuccessfullyShown = false;
                return;
            }

            if (isShowing) {
                Log.d(TAG, "Removendo overlay existente antes de mostrar o novo.");
                hide();
            }

            // 💡 NOVO: Infla o layout XML em vez de criar views dinamicamente
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // Assumindo que o arquivo XML foi nomeado overlay_layout.xml e está em res/layout/
            floatingView = inflater.inflate(R.layout.overlay_flutuante, null);

            // 1. Configura a cor da borda/botão e o texto de status
            TextView tvStatusTitle = floatingView.findViewById(R.id.tv_status_title);
            Button btnClose = floatingView.findViewById(R.id.btn_close_overlay);

            String recommendationText = recommendedToAccept ? "ACEITÁVEL" : "RECUSÁVEL";
            int color = recommendedToAccept ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"); // Verde ou Vermelho

            tvStatusTitle.setText(recommendationText + " | " + reason);
            tvStatusTitle.setBackgroundColor(color);
            // Configura a cor do botão baseado na recomendação
            btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            btnClose.setOnClickListener(v -> hide());

            // 2. Preenche as métricas
            TextView tvRatePerKm = floatingView.findViewById(R.id.tv_rate_per_km);
            TextView tvRatePerHour = floatingView.findViewById(R.id.tv_rate_per_hour); // Novo ID
            TextView tvPassengerNote = floatingView.findViewById(R.id.tv_passenger_note); // Novo ID
            TextView tvTotalTime = floatingView.findViewById(R.id.tv_total_time);
            TextView tvTotalKm = floatingView.findViewById(R.id.tv_total_km);

            tvRatePerKm.setText(String.format(Locale.getDefault(), "R$ %.2f", ratePerKm));
            tvRatePerHour.setText(String.format(Locale.getDefault(), "R$ %.2f", ratePerHour));
            tvPassengerNote.setText(passageiroNotaStr);
            tvTotalTime.setText(String.format(Locale.getDefault(), "%.0f min", totalTempoMinutos));
            tvTotalKm.setText(String.format(Locale.getDefault(), "%.1f km", totalDistanciaKM));

            // 3. Configuração da Janela (Window Manager)
            int type;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                type = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 150;

            try {
                windowManager.addView(floatingView, params);
                isShowing = true;
                overlaySuccessfullyShown = true;
                // Oculta após 5 segundos, garantindo a permanência mínima
                handler.postDelayed(hideRunnable, 5000);
                Log.i(TAG, "Overlay ADICIONADO com sucesso! Visível por 5s.");
            } catch (Exception e) {
                Log.e(TAG, "Erro CRÍTICO ao adicionar overlay. Verifique as permissões. Erro: " + e.getMessage(), e);
                overlaySuccessfullyShown = false;
            }
        }

        public void hide() {
            if (isShowing && floatingView != null) {
                try {
                    // Remove o callback de auto-ocultação se for escondido manualmente ou substituído
                    handler.removeCallbacks(hideRunnable);
                    windowManager.removeView(floatingView);
                    isShowing = false;
                    overlaySuccessfullyShown = false;
                    Log.i(TAG, "Overlay REMOVIDO com sucesso.");
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao remover overlay. Pode ser que a View já tenha sido removida. Erro: " + e.getMessage(), e);
                }
            }
        }

        private final Runnable hideRunnable = () -> {
            Log.i(TAG, "Overlay fechado automaticamente após 5 segundos.");
            hide();
        };
    }

    // ============================================================
    // 🔹 Lógica de extração, decisão e exibição
    // ============================================================
    private float parseValueString(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) return 0.0f;
        // Remove tudo que não for número, vírgula ou ponto, e troca vírgula por ponto para Float.parseFloat
        String cleanStr = valueStr.replaceAll("[^0-9,.]", "").trim().replace(",", ".");
        if (cleanStr.isEmpty()) return 0.0f;
        try {
            return Float.parseFloat(cleanStr);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Erro ao converter valor: " + valueStr);
            return 0.0f;
        }
    }

    // Extrai e soma todos os KM e Minutos encontrados em um texto
    private void extractAndSumMetrics(String text) {
        if (text == null) return;

        // Pattern para capturar X.Y km (ex: 3.1 km ou 4,2 km)
        Pattern kmPattern = Pattern.compile("(\\d{1,2}[.,]\\d)\\s*km");
        Matcher kmMatcher = kmPattern.matcher(text);
        while (kmMatcher.find()) {
            String kmStr = kmMatcher.group(1).replace(',', '.');
            try {
                totalDistanciaKM += Float.parseFloat(kmStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Erro ao parsear KM: " + kmStr);
            }
        }

        // Pattern para capturar Z minutos/minuto (ex: 8 minutos ou 9 minuto)
        Pattern minPattern = Pattern.compile("(\\d+)\\s*minuto(s)?");
        Matcher minMatcher = minPattern.matcher(text);
        while (minMatcher.find()) {
            try {
                totalTempoMinutos += Float.parseFloat(minMatcher.group(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Erro ao parsear Minutos: " + minMatcher.group(1));
            }
        }
    }

    // Método recursivo focado em coleta de texto e dados principais
    private void traverseNodesAndLog(AccessibilityNodeInfo node) {
        if (node == null) return;
        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.length() > 0) {
            String text = nodeText.toString();

            // Adiciona todo o texto para a lista de extração robusta (KM e Minutos)
            allTextNodes.add(text);

            // Captura Valor
            if (text.startsWith("R$")) {
                corridaValorStr = text;
                Log.d(TAG, "Valor detectado: " + corridaValorStr);
            }
            // Captura Nota
            else if (text.matches("^\\d[.,]\\d{1,2}$")) {
                passageiroNotaStr = text;
                Log.d(TAG, "Nota detectada: " + passageiroNotaStr);
            }

            // Captura Botão de Ação
            // Corrigido para "Aceitar"
            if (text.equals("Aceitar") && node.isClickable()) {
                botaoSelecionar = AccessibilityNodeInfo.obtain(node);
                Log.d(TAG, "Botão Aceitar encontrado.");
            }
        }

        for (int i = 0; i < node.getChildCount(); i++)
            traverseNodesAndLog(node.getChild(i));
    }

    private void showFloatingOverlay(boolean recommendedToAccept, String reason) {
        Log.e(TAG, "*****************************************************");
        Log.e(TAG, "* INICIANDO EXIBIÇÃO DO OVERLAY *");
        Log.e(TAG, "* VALOR: " + corridaValorStr);
        Log.e(TAG, "* R$/KM CALCULADO: " + String.format("R$ %.2f", ratePerKm));
        Log.e(TAG, "* R$/HORA CALCULADO: " + String.format("R$ %.2f", ratePerHour));
        Log.e(TAG, "* DISTÂNCIA TOTAL: " + String.format("%.1f km", totalDistanciaKM));
        Log.e(TAG, "* TEMPO TOTAL: " + String.format("%.0f min", totalTempoMinutos));
        Log.e(TAG, "* NOTA PASSAGEIRO: " + passageiroNotaStr);
        Log.e(TAG, "*****************************************************");

        if (floatingOverlay != null) floatingOverlay.show(recommendedToAccept, reason);
    }

    private void decideAndAct() {
        float valorNumerico = parseValueString(corridaValorStr);

        // 1. Cálculo R$/KM
        if (totalDistanciaKM > 0)
            ratePerKm = valorNumerico / totalDistanciaKM;
        else
            ratePerKm = 0.0f;

        // 2. Cálculo R$/Hora
        // Usa o tempo total (busca + viagem)
        if (totalTempoMinutos > 0) {
            // (Valor / Tempo em Minutos) * 60 minutos/hora
            ratePerHour = (valorNumerico / totalTempoMinutos) * 60.0f;
        } else {
            ratePerHour = 0.0f;
        }

        // --- Lógica de Decisão ---
        // Exemplo: Aceitar se R$/KM >= R$2.00 E R$/Hora >= R$40.00
        boolean meetsKmCriteria = ratePerKm >= 2.0f;
        boolean meetsHourCriteria = ratePerHour >= 40.0f;

        boolean recommendedToAccept = meetsKmCriteria && meetsHourCriteria;

        String reason;

        if (totalDistanciaKM == 0.0f && valorNumerico > 0) {
            reason = "Distância não detectada. Cálculo de taxa falhou.";
            recommendedToAccept = false;
        } else if (totalDistanciaKM == 0.0f && valorNumerico == 0) {
            reason = "Aguardando oferta completa.";
            recommendedToAccept = false;
        } else if (recommendedToAccept) {
            reason = "Critérios atendidos!";
        } else if (!meetsKmCriteria) {
            reason = String.format("Baixo R$/Km (R$ %.2f)", ratePerKm);
        } else if (!meetsHourCriteria) {
            reason = String.format("Baixo R$/Hora (R$ %.2f)", ratePerHour);
        } else {
            reason = "Critérios não atendidos.";
        }


        showFloatingOverlay(recommendedToAccept, reason);
    }

    private void logAndClearData() {
        // Limpa todas as variáveis para a próxima detecção
        corridaValorStr = "R$ 0,00";
        passageiroNotaStr = "0,00";
        totalDistanciaKM = 0.0f;
        totalTempoMinutos = 0.0f;
        ratePerKm = 0.0f;
        ratePerHour = 0.0f; // Limpa também a nova variável
        botaoSelecionar = null;
        allTextNodes.clear(); // Limpa a lista de textos coletados
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        try {
            int eventType = event.getEventType();
            // Só processamos eventos de mudança de estado ou conteúdo da janela
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                return;

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "null";
            if (!packageName.equals(TARGET_PACKAGE_NAME)) return;

            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return;

            // Busca pelo botão "Aceitar" para servir como gatilho da detecção de pedido
            // Corrigido de "Selecionar" para "Aceitar"
            List<AccessibilityNodeInfo> aceitarButtons = source.findAccessibilityNodeInfosByText("Aceitar");
            List<AccessibilityNodeInfo> selecionarButtons = source.findAccessibilityNodeInfosByText("Selecionar");

            boolean hasClickableAceitar = aceitarButtons != null && aceitarButtons.stream().anyMatch(AccessibilityNodeInfo::isClickable);
            boolean hasClickableSelecionar = selecionarButtons != null && selecionarButtons.stream().anyMatch(AccessibilityNodeInfo::isClickable);

            boolean isTriggerPresent = hasClickableAceitar || hasClickableSelecionar;


            if (isTriggerPresent) {
                logAndClearData();
                traverseNodesAndLog(source);

                // 💡 NOVO FLUXO: Processa TODOS os textos coletados para somar KM e Minutos
                for (String text : allTextNodes) {
                    extractAndSumMetrics(text);
                }

                // Cria o hash de conteúdo para Debounce, usando Valor, Distância e Tempo
                String currentContentHash = corridaValorStr + String.format("%.1f", totalDistanciaKM) + String.format("%.0f", totalTempoMinutos);
                long currentTime = System.currentTimeMillis();

                // Verifica se o hash é diferente OU se o tempo de debounce já passou
                if (!currentContentHash.equals(lastProcessedContentHash) ||
                        (currentTime - lastProcessedTime >= DEBOUNCE_INTERVAL_MS)) {
                    Log.i(TAG, ">>>> PEDIDO DETECTADO! BOTÃO 'Aceitar' ENCONTRADO. <<<<");
                    decideAndAct();
                    lastProcessedContentHash = currentContentHash;
                    lastProcessedTime = currentTime;
                } else {
                    Log.d(TAG, "Evento ignorado (Debounce/Hash Duplicado).");
                }
            }
            // 💡 NOTA: Não removemos o overlay aqui, permitindo que ele dure 5 segundos ou até ser substituído.

        } catch (Exception e) {
            Log.e(TAG, "Exceção no processamento de evento.", e);
        } finally {
            // A reciclagem do nó da fonte não é estritamente necessária aqui, o SO cuida disso.
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Serviço de Monitoramento INTERROMPIDO.");
        if (floatingOverlay != null) floatingOverlay.hide();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingOverlay != null) floatingOverlay.hide();
        Log.i(TAG, "Serviço de Monitoramento DESTRUÍDO.");
    }
}
