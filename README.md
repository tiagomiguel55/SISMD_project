# Histogram Equalization and Parallel Processing in Java

**Sistemas Multinúcleo e Distribuídos** — Mestrado em Engenharia Informática, ISEP  
**Autor:** Tiago Miguel Silva — 1250554

---

## Descrição

Implementação do algoritmo de equalização de histograma em Java, comparando cinco abordagens de processamento: sequencial, multithreaded sem thread pool, multithreaded com thread pool, Fork/Join, e CompletableFuture. O programa faz benchmark automático de todas as implementações e gera as imagens de output correspondentes.

---

## Requisitos

- Java 21 (OpenJDK 21 recomendado)
- As imagens de input devem estar acessíveis no sistema de ficheiros local

---

## Compilação

```bash
javac *.java
```

---

## Execução

```bash
java ApplyFilters
```

O programa irá pedir interativamente:

1. O caminho para a imagem de input (ex: `src.jpg`)
2. O número de threads a utilizar (ex: `8`)

### Com configuração de Garbage Collector

```bash
# G1GC (padrão da JVM)
java -XX:+UseG1GC -Xms512m -Xmx4g -Xlog:gc:file=gc_g1.log:time,uptime,level,tags ApplyFilters

# Parallel GC (máximo throughput)
java -XX:+UseParallelGC -Xms512m -Xmx4g -Xlog:gc:file=gc_parallel.log:time,uptime,level,tags ApplyFilters

# ZGC (latência ultra-baixa)
java -XX:+UseZGC -Xms512m -Xmx8g -Xlog:gc:file=gc_zgc.log:time,uptime,level,tags ApplyFilters
```

---

## Output

Após a execução, o programa gera os seguintes ficheiros na diretoria docs\processed_images:

| Ficheiro | Implementação |
|----------|--------------|
| `out_sequential.jpg` | Sequencial |
| `out_multithreaded.jpg` | Multithreaded sem Thread Pool |
| `out_threadpool.jpg` | Thread Pool |
| `out_forkjoin.jpg` | Fork/Join |
| `out_completablefuture.jpg` | CompletableFuture |

No terminal é apresentada uma tabela com os tempos médios e speedup de cada implementação, bem como estatísticas de GC.

---

## Estrutura do Projeto

| Ficheiro | Descrição |
|----------|-----------|
| `ApplyFilters.java` | Classe principal — benchmark e geração de outputs |
| `Filters.java` | Implementação sequencial (baseline) |
| `MultithreadedFilter.java` | Implementação com threads manuais |
| `ThreadPoolFilter.java` | Implementação com ExecutorService |
| `ForkJoinFilter.java` | Implementação com Fork/Join framework |
| `CompletableFutureFilter.java` | Implementação com CompletableFuture |
| `Utils.java` | API de leitura, escrita e cópia de imagens |

---

## Metodologia de Benchmark

- **Runs de aquecimento:** 3 (para estabilizar a JIT)
- **Runs medidos:** 5
- **Pausa entre runs:** `System.gc()` + 50 ms
- **O que é medido:** apenas `processImage()`, sem I/O nem criação do pool
- **Valor reportado:** média das 5 corridas medidas
