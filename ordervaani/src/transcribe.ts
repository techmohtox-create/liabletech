import { mkdtemp, readdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, join } from 'node:path';

import { hasFfmpeg, run } from './media.ts';

/** Turns a voice note into text. */
export interface Transcriber {
  transcribe(path: string, mime: string): Promise<string>;
}

/** Sarvam's real-time endpoint accepts at most 30 seconds of audio per request. */
const CHUNK_SECONDS = 25;

/**
 * Speech to text with Sarvam AI (built for Indian languages, including Hindi and Marathi).
 * With ffmpeg installed, voice notes of any length and format (WhatsApp sends .opus) are
 * converted to 16 kHz mono WAV and sent in 25-second pieces. Without ffmpeg the file is sent
 * as it is, which works for short notes in formats Sarvam accepts.
 */
export class SarvamTranscriber implements Transcriber {
  private readonly apiKey: string;
  private readonly model: string;
  private readonly languageCode: string;

  constructor(options: { apiKey: string; model?: string; languageCode?: string }) {
    this.apiKey = options.apiKey;
    this.model = options.model ?? 'saarika:v2.5';
    // "unknown" lets Sarvam detect the language, so Hindi and Marathi notes both work.
    this.languageCode = options.languageCode ?? 'unknown';
  }

  async transcribe(path: string, mime: string): Promise<string> {
    if (!(await hasFfmpeg())) {
      return this.send(await readFile(path), basename(path), mime);
    }
    const dir = await mkdtemp(join(tmpdir(), 'ordervaani-'));
    try {
      await run('ffmpeg', [
        '-hide_banner', '-loglevel', 'error', '-i', path,
        '-ac', '1', '-ar', '16000', '-f', 'segment', '-segment_time', String(CHUNK_SECONDS),
        join(dir, 'part%03d.wav'),
      ]);
      const parts = (await readdir(dir)).filter((f) => f.endsWith('.wav')).sort();
      const texts: string[] = [];
      for (const part of parts) {
        texts.push(await this.send(await readFile(join(dir, part)), part, 'audio/wav'));
      }
      return texts.filter(Boolean).join(' ').trim();
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  }

  private async send(audio: Buffer, name: string, mime: string): Promise<string> {
    const form = new FormData();
    form.append('file', new Blob([new Uint8Array(audio)], { type: mime }), name);
    form.append('model', this.model);
    form.append('language_code', this.languageCode);
    const res = await fetch('https://api.sarvam.ai/speech-to-text', {
      method: 'POST',
      headers: { 'api-subscription-key': this.apiKey },
      body: form,
      signal: AbortSignal.timeout(60_000),
    });
    if (!res.ok) {
      const detail = (await res.text()).slice(0, 300);
      if (res.status === 401 || res.status === 403) {
        throw new Error('The Sarvam API key was rejected. Check SARVAM_API_KEY in .env and restart the app.');
      }
      throw new Error(`Speech-to-text failed (${res.status}): ${detail}`);
    }
    const body = (await res.json()) as { transcript?: string };
    return (body.transcript ?? '').trim();
  }
}
