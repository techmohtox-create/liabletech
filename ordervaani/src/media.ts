import { execFile } from 'node:child_process';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { extname, join } from 'node:path';
import { promisify } from 'node:util';

import { IMAGE_MEDIA_TYPES, type ImageMediaType } from './extract.ts';

export const run = promisify(execFile);

let ffmpeg: Promise<boolean> | undefined;

export function hasFfmpeg(): Promise<boolean> {
  ffmpeg ??= run('ffmpeg', ['-version']).then(
    () => true,
    () => false,
  );
  return ffmpeg;
}

const AUDIO_EXT = ['.opus', '.ogg', '.oga', '.mp3', '.m4a', '.aac', '.wav', '.amr', '.webm', '.3gp'];
const IMAGE_EXT = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.heic', '.heif'];

/** Decides whether an upload is a voice note or a photo; WhatsApp downloads often have a generic type. */
export function kindOf(fileName: string, mime: string): 'voice' | 'photo' | null {
  const ext = extname(fileName).toLowerCase();
  if (mime.startsWith('audio/') || mime === 'application/ogg' || AUDIO_EXT.includes(ext)) {
    return 'voice';
  }
  if (mime.startsWith('image/') || IMAGE_EXT.includes(ext)) {
    return 'photo';
  }
  return null;
}

/** Claude accepts images up to 5 MB; phone photos are often larger, so leave headroom. */
const MAX_IMAGE_BYTES = 3_750_000;

/**
 * Returns the photo in a form Claude accepts. Large photos and other formats (such as iPhone
 * HEIC) are converted to a 2000-pixel JPEG with ffmpeg, which keeps handwriting readable.
 */
export async function prepareImage(path: string, mime: string): Promise<{ mediaType: ImageMediaType; base64: string }> {
  const size = (await stat(path)).size;
  if (IMAGE_MEDIA_TYPES.includes(mime as ImageMediaType) && size <= MAX_IMAGE_BYTES) {
    return { mediaType: mime as ImageMediaType, base64: (await readFile(path)).toString('base64') };
  }
  if (!(await hasFfmpeg())) {
    throw new Error('This photo is too large or in an unsupported format. Install ffmpeg, or send a JPEG or PNG under 3.7 MB.');
  }
  const dir = await mkdtemp(join(tmpdir(), 'ordervaani-'));
  try {
    const out = join(dir, 'photo.jpg');
    await run('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-i', path, '-vf', "scale='min(2000,iw)':-2", '-q:v', '4', out]);
    return { mediaType: 'image/jpeg', base64: (await readFile(out)).toString('base64') };
  } catch {
    throw new Error('Could not convert this photo. Send it as a JPEG or PNG.');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}
