import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

import { createApp } from './app.ts';
import { Store } from './db.ts';
import { ClaudeExtractor } from './extract.ts';
import { SarvamTranscriber } from './transcribe.ts';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const dataDir = resolve(root, process.env.ORDERVAANI_DATA_DIR || 'data');
const model = process.env.ORDERVAANI_MODEL || 'claude-opus-5-5';

const store = new Store(join(dataDir, 'ordervaani.db'));
store.seedSampleCatalogue();

const hasAnthropicCredentials = Boolean(process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN);
const { app } = createApp({
  store,
  uploadDir: join(dataDir, 'uploads'),
  publicDir: join(root, 'public'),
  model,
  extractor: hasAnthropicCredentials ? new ClaudeExtractor({ model }) : undefined,
  transcriber: process.env.SARVAM_API_KEY
    ? new SarvamTranscriber({
        apiKey: process.env.SARVAM_API_KEY,
        model: process.env.SARVAM_STT_MODEL || undefined,
        languageCode: process.env.SARVAM_LANGUAGE || undefined,
      })
    : undefined,
});

// Local use only: there is no login, so by default only this computer can open it.
const host = process.env.HOST || '127.0.0.1';
const port = Number(process.env.PORT || 3100);
app.listen(port, host, () => {
  console.log(`OrderVaani is running at http://${host === '0.0.0.0' ? 'localhost' : host}:${port}`);
  console.log(`Data: ${dataDir}`);
  if (!hasAnthropicCredentials) console.log('ANTHROPIC_API_KEY is not set: orders will need their lines typed in by hand.');
  if (!process.env.SARVAM_API_KEY) console.log('SARVAM_API_KEY is not set: voice notes will need their words typed in.');
});
