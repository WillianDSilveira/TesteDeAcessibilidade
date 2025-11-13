package com.example.testedeacessibilidade.data;

// A classe de métricas deve ser traduzida separadamente se estiver no seu projeto.
// Mantemos o nome original aqui:
// import com.example.testedeacessibilidade.data.RideMetrics;

public class ResultadoDecisao { // Tradução do nome da classe

    // Variáveis públicas
    public boolean recomendadoAceitar; // Tradução da variável
    public String razao; // Tradução da variável
    public MetricasCorrida metricas; // Tradução da variável

    // Resultados da API (para comparação)
    public float apiTotalDistanciaKM = 0.0f;
    public float apiTotalTempoMinutos = 0.0f;

    // Construtor
    public ResultadoDecisao(boolean recomendadoAceitar, String razao, MetricasCorrida metricas) { // Tradução dos parâmetros
        this.recomendadoAceitar = recomendadoAceitar;
        this.razao = razao;
        this.metricas = metricas;
    }

    // Método para definir os dados da API
    public void definirResultadosApi(float km, float min) { // Tradução do método
        this.apiTotalDistanciaKM = km;
        this.apiTotalTempoMinutos = min;
    }

    // Getters para a diferença (para usar no overlay)
    public float obterDiferencaKm() { // Tradução do método
        // *Verifique se as variáveis abaixo existem na classe RideMetrics*
        // Se metricas não possui .totalDistanciaKM, você deve ajustar a lógica
        // do Decisor/Extractor para passar o dado original aqui ou corrigir o RideMetrics.
        // O código traduzido mantém a lógica original, mas aponta a possível inconsistência:
        return apiTotalDistanciaKM - metricas.totalDistanciaKM;
    }

    public float obterDiferencaMinutos() { // Tradução do método
        // O mesmo vale aqui para .totalTempoMinutos
        return apiTotalTempoMinutos - metricas.totalTempoMinutos;
    }
}