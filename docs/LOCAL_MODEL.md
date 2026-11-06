# Running JARVIS fully on-device

Settings -> AI PROVIDER -> **ON-DEVICE** -> IMPORT MODEL FILE.

## Get a model (once, needs internet)
Hugging Face -> organisation **litert-community** -> pick a `.task` file (some models need you to accept the
model's license while logged in). Good starting points, by phone RAM:

| Phone RAM | Model size | Examples |
|-----------|-----------|----------|
| 4 GB | ~1B, int4/int8 | Gemma-3-1B-IT, Llama-3.2-1B-Instruct, Qwen2.5-0.5B/1.5B |
| 6-8 GB | 1.5B-3B | Qwen2.5-1.5B/3B-Instruct, Llama-3.2-3B-Instruct |
| 12 GB+ | 3B-4B | Gemma-3n / Phi-4-mini (if a `.task` exists) |

Only `.task` / `.bin` bundles work with this engine. `.gguf` (llama.cpp) and `.litertlm` do not.

## Tips
* Hindi/Hinglish: Qwen2.5 and Llama-3.2 are better than tiny Gemma. Test with your own phrases.
* If answers look like garbage, change **Prompt format** (Gemma / Llama 3 / Qwen-ChatML).
* If the app crashes while loading, the model is too big for your RAM: use a smaller one or lower context tokens.
* Small models make mistakes with tool JSON. JARVIS keeps its safety gates, so a bad plan is blocked or asks you first,
  but expect to rephrase more often than with Gemini/Groq.
* Vision (`analyze_screen`) still needs Gemini/OpenAI: text-only models cannot see images.
