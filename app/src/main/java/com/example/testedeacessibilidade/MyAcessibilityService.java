package com.example.testedeacessibilidade;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.testedeacessibilidade.business.RideDataExtractor;
import com.example.testedeacessibilidade.business.RideDecisionMaker;
import com.example.testedeacessibilidade.business.RouterVerifier;
import com.example.testedeacessibilidade.business.RouterVerifier.RouteCallback;
import com.example.testedeacessibilidade.data.RideOfferData;
import com.example.testedeacessibilidade.data.DecisionResult;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

public class MyAcessibilityService extends AccessibilityService {

    private static final String TAG = "DEBUG_MONITOR";
    private static final String TARGET_PACKAGE_NAME = "com.ubercab.driver";
    private static final String CHANNEL_ID = "acessibilidade_monitor";
    private static final int NOTIFICATION_ID = 101;

    private long lastProcessedTime = 0;
    private static final long DEBOUNCE_INTERVAL_MS = 1000;
    private String lastProcessedContentHash = "";

    private final RideOfferData rideData = new RideOfferData();
    private RideDataExtractor dataExtractor;
    private RideDecisionMaker decisionMaker;
    private final Handler handler = new Handler();
    private FloatingOverlay floatingOverlay;
    private boolean overlaySuccessfullyShown = false;
    private RouterVerifier routeVerifier;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String driverCurrentLocation = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Serviço de Monitoramento CONECTADO e ativo.");

        dataExtractor = new RideDataExtractor(rideData);
        decisionMaker = new RideDecisionMaker();
        floatingOverlay = new FloatingOverlay(this);
        routeVerifier = new RouterVerifier();

        startForegroundServiceCompat();
        Toast.makeText(this, "Serviço de acessibilidade ativo.", Toast.LENGTH_SHORT).show();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissão de Localização Ausente!");
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        driverCurrentLocation = String.format(Locale.US, "%.6f,%.6f",
                                location.getLatitude(), location.getLongitude());
                        Log.d(TAG, "GPS Atualizado: " + driverCurrentLocation);
                    }
                }
            }
        };

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void getCurrentLocation(Runnable onLocationReady) {
        // Se já tem uma localização recente, usa ela
        if (driverCurrentLocation != null) {
            onLocationReady.run();
            return;
        }

        // Tenta pegar uma única localização atual
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissão de Localização Ausente para getCurrentLocation!");
            onLocationReady.run();
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        driverCurrentLocation = String.format(Locale.US, "%.6f,%.6f",
                                location.getLatitude(), location.getLongitude());
                        Log.d(TAG, "Localização obtida no momento da chamada: " + driverCurrentLocation);
                    } else {
                        Log.w(TAG, "Não foi possível obter localização imediata.");
                    }
                    onLocationReady.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Falha ao obter localização atual: " + e.getMessage());
                    onLocationReady.run();
                });
    }

    @Override
    public void onInterrupt() {
        stopLocationUpdates();
        if (floatingOverlay != null) floatingOverlay.hide();
        Log.w(TAG, "Serviço de Monitoramento INTERROMPIDO.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        if (floatingOverlay != null) floatingOverlay.hide();
        rideData.clear();
        Log.i(TAG, "Serviço DESTRUÍDO.");
    }

    private void startForegroundServiceCompat() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Monitor de Acessibilidade", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Serviço ativo de monitoramento");
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitor ativo")
                .setContentText("O serviço de monitoramento está em execução.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    // ============================================================
    // 🔹 Overlay (mantido sem alterações)
    // ============================================================
    private class FloatingOverlay {
        private WindowManager windowManager;
        private View floatingView;
        public boolean isShowing = false;

        public FloatingOverlay(Context context) {
            windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        }

        public void show(DecisionResult result, RideOfferData data) {
            if (!Settings.canDrawOverlays(MyAcessibilityService.this)) {
                overlaySuccessfullyShown = false;
                return;
            }

            if (isShowing) hide();

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            floatingView = inflater.inflate(R.layout.overlay_flutuante, null);

            TextView tvStatusTitle = floatingView.findViewById(R.id.tv_status_title);
            Button btnClose = floatingView.findViewById(R.id.btn_close_overlay);

            String recommendationText = result.recommendedToAccept ? "ACEITÁVEL" : "RECUSÁVEL";
            int color = result.recommendedToAccept ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");

            tvStatusTitle.setText(recommendationText + " | " + result.reason);
            tvStatusTitle.setBackgroundColor(color);
            btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            btnClose.setOnClickListener(v -> hide());

            TextView tvRatePerKm = floatingView.findViewById(R.id.tv_rate_per_km);
            TextView tvRatePerHour = floatingView.findViewById(R.id.tv_rate_per_hour);
            TextView tvPassengerNote = floatingView.findViewById(R.id.tv_passenger_note);
            TextView tvTotalTime = floatingView.findViewById(R.id.tv_total_time);
            TextView tvTotalKm = floatingView.findViewById(R.id.tv_total_km);

            tvRatePerKm.setText(String.format(Locale.getDefault(), "R$ %.2f", result.metrics.ratePerKm));
            tvRatePerHour.setText(String.format(Locale.getDefault(), "R$ %.2f", result.metrics.ratePerHour));
            tvPassengerNote.setText(data.passageiroNotaStr);

            String timeText;
            String kmText;

            if (result.apiTotalTempoMinutos > 0.0f) {
                float minDiff = result.apiTotalTempoMinutos - data.totalTempoMinutos;
                String minSign = minDiff >= 0 ? "+" : "";
                timeText = String.format(Locale.getDefault(),
                        "%.0f min (%s%.0f)", data.totalTempoMinutos, minSign, minDiff);

                float kmDiff = result.apiTotalDistanciaKM - data.totalDistanciaKM;
                String kmSign = kmDiff >= 0 ? "+" : "";
                kmText = String.format(Locale.getDefault(),
                        "%.1f km (%s%.1f)", data.totalDistanciaKM, kmSign, kmDiff);
            } else {
                timeText = String.format(Locale.getDefault(), "%.0f min (Aguardando API)", data.totalTempoMinutos);
                kmText = String.format(Locale.getDefault(), "%.1f km (Aguardando API)", data.totalDistanciaKM);
            }

            tvTotalTime.setText(timeText);
            tvTotalKm.setText(kmText);

            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

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
                handler.postDelayed(hideRunnable, 5000);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao adicionar overlay: " + e.getMessage(), e);
                overlaySuccessfullyShown = false;
            }
        }

        public void hide() {
            if (isShowing && floatingView != null) {
                try {
                    handler.removeCallbacks(hideRunnable);
                    windowManager.removeView(floatingView);
                    isShowing = false;
                    overlaySuccessfullyShown = false;
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao remover overlay: " + e.getMessage(), e);
                }
            }
        }

        private final Runnable hideRunnable = this::hide;
    }

    // ============================================================
    // 🔹 Lógica Principal (ajuste mínimo de sincronização GPS)
    // ============================================================
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        try {
            int eventType = event.getEventType();
            if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                return;

            String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "null";
            if (!packageName.equals(TARGET_PACKAGE_NAME)) return;

            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return;

            List<AccessibilityNodeInfo> aceitarButtons = source.findAccessibilityNodeInfosByText("Aceitar");
            boolean hasClickableAceitar = aceitarButtons != null && aceitarButtons.stream().anyMatch(AccessibilityNodeInfo::isClickable);

            List<AccessibilityNodeInfo> selecionarButtons = source.findAccessibilityNodeInfosByText("Selecionar");
            boolean hasClickableSelecionar = selecionarButtons != null && selecionarButtons.stream().anyMatch(AccessibilityNodeInfo::isClickable);

            boolean isTriggerPresent = hasClickableAceitar || hasClickableSelecionar;
            if (!isTriggerPresent) return;

            dataExtractor.extract(source);

            String currentContentHash = rideData.corridaValorStr +
                    String.format("%.1f", rideData.totalDistanciaKM) +
                    String.format("%.0f", rideData.totalTempoMinutos);
            long currentTime = System.currentTimeMillis();

            if (!currentContentHash.equals(lastProcessedContentHash) ||
                    (currentTime - lastProcessedTime >= DEBOUNCE_INTERVAL_MS)) {

                DecisionResult result = decisionMaker.decide(rideData);
                floatingOverlay.show(result, rideData);

                // 🚀 Agora garantimos localização atual antes da API:
                getCurrentLocation(() -> {
                    if (rideData.embarqueEndereco != null && !rideData.embarqueEndereco.isEmpty() &&
                            rideData.destinoEndereco != null && !rideData.destinoEndereco.isEmpty() &&
                            driverCurrentLocation != null) {

                        Log.i(TAG, "Iniciando verificação de rota com API...");
                        Log.i(TAG, "Localização GPS usada: " + driverCurrentLocation);

                        routeVerifier.verifyRoute(
                                driverCurrentLocation,
                                rideData.embarqueEndereco,
                                rideData.destinoEndereco,
                                new RouteCallback() {
                                    @Override
                                    public void onResult(float distanceKm, float durationMinutes) {
                                        result.setApiResults(distanceKm, durationMinutes);
                                        floatingOverlay.show(result, rideData);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Log.e(TAG, "Erro na verificação da API: " + message);
                                    }
                                });
                    } else {
                        Log.w(TAG, "Endereços incompletos ou localização nula. API pulada.");
                    }
                });

                lastProcessedContentHash = currentContentHash;
                lastProcessedTime = currentTime;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exceção no processamento de evento.", e);
        }
    }
}
