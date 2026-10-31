# Prueba de viabilidad: traducir las funciones calientes de Ivona a C

Resultado en la máquina de pruebas (x86-64), mismas 10 frases, voz Conchita:

| Modo | RTF (sin 2 primeras frases) | Primer audio medio | Audio |
|---|---|---|---|
| qemu-arm | 0.26 | 243 ms | referencia |
| dynarmic puro | 0.25 | 240 ms | idéntico |
| dynarmic + 315 funciones traducidas a C | 0.12 | 112 ms | idéntico (mismo hash) |

Ficheros:
- dynrun.cpp: ejecutor de programas ARM32 sobre dynarmic (llamadas al sistema, hilos, cargador ELF, muestreador de perfil DYN_PROF, ganchos DYN_HOT, prueba diferencial --difftest).
- lift.py / build.py / prelude.h: traductor ARM (modo ARM, enteros) a C. build.py usa perfil_conchita.txt para elegir las funciones y genera hot_gen.cpp (no se sube: se regenera).
- Compilación de dynrun: g++ -std=c++20 -O2 dynrun.cpp hot_gen.o + libdynarmic.a (fork azahar-emu/dynarmic) -lpthread.
- Variables: DYN_HOT=1 activa funciones nativas; DYN_HOT_MAX=N limita cuántas; DYN_PROF=archivo guarda perfil.

Pendiente para llevarlo al teléfono: compilar dynarmic y el ejecutor para arm64 con el NDK, empaquetarlo en lugar de libqemu, y repetir la medición en el SM-A245M.
Uso personal: el código generado deriva del binario de Ivona; no se publica.
