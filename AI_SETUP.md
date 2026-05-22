# AI Setup for IntelliJ IDEA (Fakeworld Mod)

This guide sets up an AI assistant directly inside IntelliJ IDEA so you never
have to leave the editor to ask questions about this mod.

---

## Option A — GitHub Copilot (Recommended)

**Best for:** Inline completions + chat. Works with your existing GitHub account.

### Install
1. Open IntelliJ IDEA
2. `File → Settings → Plugins → Marketplace`
3. Search **GitHub Copilot** → Install → Restart IDE
4. After restart: click the Copilot icon in the bottom-right status bar → **Log in to GitHub**
5. Authorize in the browser that opens — done.

### Use
- **Inline suggestions** appear as you type (press `Tab` to accept)
- **Chat panel**: `Tools → GitHub Copilot → Open Chat` (or press `Alt+\`)
- Highlight any code → right-click → **Copilot → Explain / Fix / Generate Docs**

### Cost
- ~$10/month, OR free if you qualify for the GitHub Free tier (limited completions)
- Check: https://github.com/features/copilot

---

## Option B — Continue.dev (Free, supports local + cloud models)

**Best for:** Free usage, or running AI fully offline with Ollama.

### Install
1. `File → Settings → Plugins → Marketplace`
2. Search **Continue** → Install → Restart IDE
3. A Continue sidebar appears on the right

### Configure a model (pick one)

#### Cloud (Claude / GPT-4 via API key)
- Click the gear icon in the Continue sidebar
- Edit `~/.continue/config.json`:
```json
{
  "models": [
    {
      "title": "Claude Sonnet",
      "provider": "anthropic",
      "model": "claude-sonnet-4-5",
      "apiKey": "YOUR_ANTHROPIC_KEY_HERE"
    }
  ]
}
```

#### Local / Offline (Ollama — completely free)
1. Download Ollama: https://ollama.com
2. Run in terminal: `ollama pull llama3`
3. In `config.json`:
```json
{
  "models": [
    {
      "title": "Llama 3 (Local)",
      "provider": "ollama",
      "model": "llama3"
    }
  ]
}
```

### Use
- Open the Continue panel on the right side of IntelliJ
- Highlight Fakeworld code → press `Ctrl+L` to send it to chat
- Type your question directly in the panel

---

## Option C — CodeGPT (Free tier, simple UI)

1. `File → Settings → Plugins → Marketplace`
2. Search **CodeGPT** → Install → Restart IDE
3. `File → Settings → Tools → CodeGPT` → pick a provider and paste your API key
4. Open the CodeGPT chat panel from the right sidebar

Free tier allows limited daily messages without an API key.

---

## Quick Comparison

| Plugin        | Cost              | Offline? | Best for                  |
|---------------|-------------------|----------|---------------------------|
| GitHub Copilot | ~$10/mo or free  | No       | Inline completions + chat |
| Continue.dev  | Free (bring key) | Yes (Ollama) | Flexible, open source |
| CodeGPT       | Free tier         | No       | Simple chat UI            |

---

## After Installing

Run `git pull` in IntelliJ (`Git → Pull`) to get this file,
then follow whichever option suits you above.

For questions about this mod's code (Fabric 1.20.1, entities, mixins, etc.),
all three options work well — just paste the relevant class into the chat.
