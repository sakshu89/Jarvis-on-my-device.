# Offline AI on Termux (Realme GT 6T, 8 GB RAM) -> JARVIS

1. Install Termux from F-Droid or GitHub (not the Play Store). Then:
   pkg update -y && pkg upgrade -y
   pkg install -y git cmake clang make wget
2. Build llama.cpp:
   git clone --depth 1 https://github.com/ggml-org/llama.cpp ~/llama.cpp
   cd ~/llama.cpp
   cmake -B build -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=ON -DLLAMA_CURL=OFF
   cmake --build build --config Release -j4 --target llama-server llama-cli
3. Download a model (Q4_K_M, 3B is the sweet spot for 8 GB):
   mkdir -p ~/models && cd ~/models
   wget -c https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf
4. Keep Termux alive: termux-wake-lock; Settings -> Battery -> Termux -> allow background activity;
   Developer options -> "Disable child process restrictions" (Android 14).
5. Start the server:
   ~/llama.cpp/build/bin/llama-server -m ~/models/qwen2.5-3b-instruct-q4_k_m.gguf -c 4096 -t 4 --host 127.0.0.1 --port 8080
6. JARVIS -> CONFIG -> OPENAI-COMPATIBLE: Base URL http://127.0.0.1:8080/v1, API key: anything (or blank), Model: local.
