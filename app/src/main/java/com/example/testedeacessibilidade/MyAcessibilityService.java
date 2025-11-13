package com.example.testedeacessibilidade;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
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

// MANUTENÇÃO DOS NOMES DE CLASSE E PACOTE DO SEU PROJETO
import com.example.testedeacessibilidade.business.ExtratorDadosCorrida; // Renomeado para ExtratorDadosCorrida na tradução
import com.example.testedeacessibilidade.business.DecisorCorrida; // Renomeado para DecisorCorrida na tradução
import com.example.testedeacessibilidade.business.VerificadorRota; // Renomeado para VerificadorRota na tradução
import com.example.testedeacessibilidade.business.VerificadorRota.RouteCallback; // Renomeado para VerificadorRota.CallbackRota na tradução
import com.example.testedeacessibilidade.data.DadosOfertaCorrida; // Renomeado para DadosOfertaCorrida na tradução
import com.example.testedeacessibilidade.data.ResultadoDecisao; // Renomeado para ResultadoDecisao na tradução
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

public class MyAcessibilityService extends AccessibilityService {

    private static final String TAG_DEPURACAO = "DEBUG_MONITOR";
    private static final String NOME_PACOTE_ALVO = "com.ubercab.driver";
    private static final String ID_CANAL = "acessibilidade_monitor";
    private static final int ID_NOTIFICACAO = 101;

    private long ultimoTempoProcessado = 0;
    private static final long INTERVALO_DEBOUNCE_MS = 1000;
    private String ultimoHashConteudoProcessado = "";

    private final DadosOfertaCorrida dadosCorrida = new DadosOfertaCorrida();
    private ExtratorDadosCorrida extratorDados;
    private DecisorCorrida decisorCorrida;
    private final Handler manipulador = new Handler();
    private OverlayFlutuante overlayFlutuante;
    private boolean overlayExibidoComSucesso = false;
    private VerificadorRota verificadorRota;

    private FusedLocationProviderClient clienteLocalizacaoFundida;
    private LocationCallback callbackLocalizacao;
    private String localizacaoAtualMotorista = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG_DEPURACAO, "Serviço de Monitoramento CONECTADO e ativo.");

        extratorDados = new ExtratorDadosCorrida(dadosCorrida);
        decisorCorrida = new DecisorCorrida();
        overlayFlutuante = new OverlayFlutuante(this);
        verificadorRota = new VerificadorRota();

        iniciarServicoPrimeiroPlanoCompativel();
        Toast.makeText(this, "Serviço de acessibilidade ativo.", Toast.LENGTH_SHORT).show();

        clienteLocalizacaoFundida = LocationServices.getFusedLocationProviderClient(this);
        iniciarAtualizacoesLocalizacao();
    }

    private void iniciarAtualizacoesLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG_DEPURACAO, "Permissão de Localização Ausente!");
            return;
        }

        callbackLocalizacao = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult resultadoLocalizacao) {
                if (resultadoLocalizacao == null) return;
                for (Location localizacao : resultadoLocalizacao.getLocations()) {
                    if (localizacao != null) {
                        localizacaoAtualMotorista = String.format(Locale.US, "%.6f,%.6f",
                                localizacao.getLatitude(), localizacao.getLongitude());
                        Log.d(TAG_DEPURACAO, "GPS Atualizado: " + localizacaoAtualMotorista);
                    }
                }
            }
        };

        LocationRequest requisicaoLocalizacao = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        clienteLocalizacaoFundida.requestLocationUpdates(requisicaoLocalizacao, callbackLocalizacao, Looper.getMainLooper());
    }

    private void pararAtualizacoesLocalizacao() {
        if (clienteLocalizacaoFundida != null && callbackLocalizacao != null) {
            clienteLocalizacaoFundida.removeLocationUpdates(callbackLocalizacao);
        }
    }

    @SuppressLint("MissingPermission")
    private void obterLocalizacaoAtual(Runnable aoLocalizacaoPronta) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG_DEPURACAO, "Permissão de Localização Ausente para obterLocalizacaoAtual!");
            return;
        }

        // Tenta obter a última localização rápida (cache)
        clienteLocalizacaoFundida.getLastLocation().addOnSuccessListener(localizacao -> {
            boolean temLocalizacaoRecente = false;

            if (localizacao != null) {
                long idade = System.currentTimeMillis() - localizacao.getTime();
                // Considera válida se tiver menos de 10 segundos
                if (idade < 10_000) {
                    localizacaoAtualMotorista = String.format(Locale.US, "%.6f,%.6f",
                            localizacao.getLatitude(), localizacao.getLongitude());
                    temLocalizacaoRecente = true;
                    Log.i(TAG_DEPURACAO, "📍 Localização recente usada (cache): " + localizacaoAtualMotorista);
                    aoLocalizacaoPronta.run();
                }
            }

            if (!temLocalizacaoRecente) {
                // Caso o cache seja velho ou nulo, solicita uma atualização REAL do GPS
                LocationRequest requisicao = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                        .setMinUpdateIntervalMillis(0)
                        .setMaxUpdates(1) // apenas 1 leitura
                        .build();

                Log.i(TAG_DEPURACAO, "⏳ Aguardando atualização real do GPS...");

                clienteLocalizacaoFundida.requestLocationUpdates(requisicao, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult resultadoLocalizacao) {
                        if (resultadoLocalizacao == null) {
                            Log.e(TAG_DEPURACAO, "⚠️ Falha ao obter atualização de localização.");
                            return;
                        }

                        Location localizacaoFresca = resultadoLocalizacao.getLastLocation();
                        if (localizacaoFresca != null) {
                            localizacaoAtualMotorista = String.format(Locale.US, "%.6f,%.6f",
                                    localizacaoFresca.getLatitude(), localizacaoFresca.getLongitude());
                            Log.i(TAG_DEPURACAO, "✅ Localização atual obtida do GPS: " + localizacaoAtualMotorista);

                            clienteLocalizacaoFundida.removeLocationUpdates(this); // evita múltiplas chamadas
                            aoLocalizacaoPronta.run();
                        }
                    }
                }, Looper.getMainLooper());
            }
        });
    }

    @Override
    public void onInterrupt() {
        pararAtualizacoesLocalizacao();
        if (overlayFlutuante != null) overlayFlutuante.esconder();
        Log.w(TAG_DEPURACAO, "Serviço de Monitoramento INTERROMPIDO.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pararAtualizacoesLocalizacao();
        if (overlayFlutuante != null) overlayFlutuante.esconder();
        dadosCorrida.clear();
        Log.i(TAG_DEPURACAO, "Serviço DESTRUÍDO.");
    }

    private void iniciarServicoPrimeiroPlanoCompativel() {
        NotificationManager gerenciadorNotificacao =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    ID_CANAL, "Monitor de Acessibilidade", NotificationManager.IMPORTANCE_LOW
            );
            canal.setDescription("Serviço ativo de monitoramento");
            gerenciadorNotificacao.createNotificationChannel(canal);
        }

        Notification notificacao = new NotificationCompat.Builder(this, ID_CANAL)
                .setContentTitle("Monitor ativo")
                .setContentText("O serviço de monitoramento está em execução.")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID_NOTIFICACAO, notificacao);
        }
    }

    // ============================================================
    // 🔹 Overlay (mantido sem alterações)
    // ============================================================
    private class OverlayFlutuante {
        private WindowManager gerenciadorJanelas;
        private View visaoFlutuante;
        public boolean estaExibindo = false;

        public OverlayFlutuante(Context contexto) {
            gerenciadorJanelas = (WindowManager) contexto.getSystemService(Context.WINDOW_SERVICE);
        }

        public void exibir(ResultadoDecisao resultado, DadosOfertaCorrida dados) {
            if (!Settings.canDrawOverlays(MyAcessibilityService.this)) {
                overlayExibidoComSucesso = false;
                return;
            }

            if (estaExibindo) esconder();

            LayoutInflater inflador = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            visaoFlutuante = inflador.inflate(R.layout.overlay_flutuante, null);

            TextView tvTituloStatus = visaoFlutuante.findViewById(R.id.tv_status_title);
            Button btnFechar = visaoFlutuante.findViewById(R.id.btn_close_overlay);

            String textoRecomendacao = resultado.recomendadoAceitar ? "ACEITÁVEL" : "RECUSÁVEL";
            int cor = resultado.recomendadoAceitar ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");

            tvTituloStatus.setText(textoRecomendacao + " | " + resultado.razao);
            tvTituloStatus.setBackgroundColor(cor);
            btnFechar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(cor));
            btnFechar.setOnClickListener(v -> esconder());

            TextView tvTaxaPorKm = visaoFlutuante.findViewById(R.id.tv_rate_per_km);
            TextView tvTaxaPorHora = visaoFlutuante.findViewById(R.id.tv_rate_per_hour);
            TextView tvNotaPassageiro = visaoFlutuante.findViewById(R.id.tv_passenger_note);
            TextView tvTempoTotal = visaoFlutuante.findViewById(R.id.tv_total_time);
            TextView tvKmTotal = visaoFlutuante.findViewById(R.id.tv_total_km);

            tvTaxaPorKm.setText(String.format(Locale.getDefault(), "R$ %.2f", resultado.metricas.taxaPorKm));
            tvTaxaPorHora.setText(String.format(Locale.getDefault(), "R$ %.2f", resultado.metricas.taxaPorHora));
            tvNotaPassageiro.setText(dados.passageiroNotaStr);

            String textoTempo;
            String textoKm;

            if (resultado.apiTotalTempoMinutos > 0.0f) {
                float diferencaMinutos = resultado.apiTotalTempoMinutos - dados.totalTempoMinutos;
                String sinalMinutos = diferencaMinutos >= 0 ? "+" : "";
                textoTempo = String.format(Locale.getDefault(),
                        "%.0f min (%s%.0f)", dados.totalTempoMinutos, sinalMinutos, diferencaMinutos);

                float diferencaKm = resultado.apiTotalDistanciaKM - dados.totalDistanciaKM;
                String sinalKm = diferencaKm >= 0 ? "+" : "";
                textoKm = String.format(Locale.getDefault(),
                        "%.1f km (%s%.1f)", dados.totalDistanciaKM, sinalKm, diferencaKm);
            } else {
                textoTempo = String.format(Locale.getDefault(), "%.0f min (Aguardando API)", dados.totalTempoMinutos);
                textoKm = String.format(Locale.getDefault(), "%.1f km (Aguardando API)", dados.totalDistanciaKM);
            }

            tvTempoTotal.setText(textoTempo);
            tvKmTotal.setText(textoKm);

            int tipo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            WindowManager.LayoutParams parametros = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    tipo,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            parametros.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            parametros.y = 150;

            try {
                gerenciadorJanelas.addView(visaoFlutuante, parametros);
                estaExibindo = true;
                overlayExibidoComSucesso = true;
                manipulador.postDelayed(runnableEsconder, 5000);
            } catch (Exception e) {
                Log.e(TAG_DEPURACAO, "Erro ao adicionar overlay: " + e.getMessage(), e);
                overlayExibidoComSucesso = false;
            }
        }

        public void esconder() {
            if (estaExibindo && visaoFlutuante != null) {
                try {
                    manipulador.removeCallbacks(runnableEsconder);
                    gerenciadorJanelas.removeView(visaoFlutuante);
                    estaExibindo = false;
                    overlayExibidoComSucesso = false;
                } catch (Exception e) {
                    Log.e(TAG_DEPURACAO, "Erro ao remover overlay: " + e.getMessage(), e);
                }
            }
        }

        private final Runnable runnableEsconder = this::esconder;
    }

    // ============================================================
    // 🔹 Lógica Principal (ajuste mínimo de sincronização GPS)
    // ============================================================
    @Override
    public void onAccessibilityEvent(AccessibilityEvent evento) {
        if (evento == null) return;

        try {
            int tipoEvento = evento.getEventType();
            if (tipoEvento != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    tipoEvento != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                return;

            String nomePacote = evento.getPackageName() != null ? evento.getPackageName().toString() : "null";
            if (!nomePacote.equals(NOME_PACOTE_ALVO)) return;

            AccessibilityNodeInfo fonte = evento.getSource();
            if (fonte == null) return;

            List<AccessibilityNodeInfo> botoesAceitar = fonte.findAccessibilityNodeInfosByText("Aceitar");
            boolean temAceitarClicavel = botoesAceitar != null && botoesAceitar.stream().anyMatch(AccessibilityNodeInfo::isClickable);

            List<AccessibilityNodeInfo> botoesSelecionar = fonte.findAccessibilityNodeInfosByText("Selecionar");
            boolean temSelecionarClicavel = botoesSelecionar != null && botoesSelecionar.stream().anyMatch(AccessibilityNodeInfo::isClickable);

            boolean gatilhoPresente = temAceitarClicavel || temSelecionarClicavel;
            if (!gatilhoPresente) return;

            extratorDados.extrair(fonte);

            String hashConteudoAtual = dadosCorrida.corridaValorStr +
                    String.format("%.1f", dadosCorrida.totalDistanciaKM) +
                    String.format("%.0f", dadosCorrida.totalTempoMinutos);
            long tempoAtual = System.currentTimeMillis();

            if (!hashConteudoAtual.equals(ultimoHashConteudoProcessado) ||
                    (tempoAtual - ultimoTempoProcessado >= INTERVALO_DEBOUNCE_MS)) {

                ResultadoDecisao resultado = decisorCorrida.decidir(dadosCorrida);
                overlayFlutuante.exibir(resultado, dadosCorrida);

                // 🚀 Agora garantimos localização atual antes da API:
                obterLocalizacaoAtual(() -> {
                    if (dadosCorrida.embarqueEndereco != null && !dadosCorrida.embarqueEndereco.isEmpty() &&
                            dadosCorrida.destinoEndereco != null && !dadosCorrida.destinoEndereco.isEmpty() &&
                            localizacaoAtualMotorista != null) {

                        Log.i(TAG_DEPURACAO, "Iniciando verificação de rota com API...");
                        Log.i(TAG_DEPURACAO, "Localização GPS usada: " + localizacaoAtualMotorista);

                        verificadorRota.verificarRota(
                                localizacaoAtualMotorista,
                                dadosCorrida.embarqueEndereco,
                                dadosCorrida.destinoEndereco,
                                new VerificadorRota.RouteCallback() {
                                    @Override
                                    public void onResult(float distanciaKm, float duracaoMinutos) {
                                        resultado.definirResultadosApi(distanciaKm, duracaoMinutos);
                                        overlayFlutuante.exibir(resultado, dadosCorrida);
                                    }

                                    @Override
                                    public void onError(String mensagem) {
                                        Log.e(TAG_DEPURACAO, "Erro na verificação da API: " + mensagem);
                                    }
                                });
                    } else {
                        Log.w(TAG_DEPURACAO, "Endereços incompletos ou localização nula. API pulada.");
                    }
                });

                ultimoHashConteudoProcessado = hashConteudoAtual;
                ultimoTempoProcessado = tempoAtual;
            }
        } catch (Exception e) {
            Log.e(TAG_DEPURACAO, "Exceção no processamento de evento.", e);
        }
    }
}