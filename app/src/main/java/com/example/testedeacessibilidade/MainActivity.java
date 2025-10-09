package com.example.testedeacessibilidade;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.testedeacessibilidade.R;

public class MainActivity extends AppCompatActivity {

    private Button btnAccessibility, btnOverlay;

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
}