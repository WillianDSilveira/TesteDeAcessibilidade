package com.example.testedeacessibilidade;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility, btnOverlay;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnAccessibility = findViewById(R.id.btn_open_accessibility);
        btnOverlay = findViewById(R.id.btn_request_overlay);

        // Botão para abrir as configurações de acessibilidade
        btnAccessibility.setOnClickListener(v -> openAccessibilitySettings());

        // Botão para solicitar permissão de sobreposição
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());

        // Solicita permissões de localização
        checkAndRequestLocationPermissions();
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Ative o serviço 'Teste de Acessibilidade' na lista.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao abrir configurações de acessibilidade.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Conceda a permissão 'Desenhar sobre outros apps'.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Permissão de sobreposição já concedida.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Verifica e solicita as permissões de localização necessárias.
     */
    private void checkAndRequestLocationPermissions() {
        boolean fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineLocation || !coarseLocation) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Solicita BACKGROUND apenas em Android 10+ (necessário se o app precisar localização contínua)
            boolean backgroundLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!backgroundLocation) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    /**
     * Retorno da solicitação de permissões
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                Toast.makeText(this, "Permissões de localização concedidas.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissões de localização são necessárias para a localização em tempo real.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
