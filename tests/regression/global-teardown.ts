import fs from 'fs';
import path from 'path';
import { execSync, spawnSync } from 'child_process';
import { ensureArtifactDir, artifactPath } from './utils/artifacts';

const ffmpegPath = require('ffmpeg-static') as string | null;

export default async function globalTeardown() {
  await exportGifs();

  const repoRoot = path.resolve(__dirname, '..', '..');
  execSync('./scripts/stop.sh', { cwd: repoRoot, stdio: 'inherit' });
}

async function exportGifs() {
  if (!ffmpegPath) {
    console.warn('[global-teardown] ffmpeg not available; skipping GIF export.');
    return;
  }
  ensureArtifactDir();
  const videos = collectVideos(path.resolve(__dirname, 'test-results'));
  videos.forEach((video) => convertVideo(video));
}

function collectVideos(dir: string): string[] {
  if (!fs.existsSync(dir)) {
    return [];
  }
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return collectVideos(fullPath);
    }
    if (entry.isFile() && (entry.name.endsWith('.webm') || entry.name.endsWith('.mp4'))) {
      return [fullPath];
    }
    return [];
  });
}

function convertVideo(videoPath: string) {
  const gifName = `${path.basename(videoPath, path.extname(videoPath))}.gif`;
  const output = artifactPath(path.join('gifs', gifName));
  fs.mkdirSync(path.dirname(output), { recursive: true });
  spawnSync(
    ffmpegPath!,
    ['-y', '-i', videoPath, '-vf', 'fps=12,scale=1280:-1:flags=lanczos', '-loop', '0', output],
    { stdio: 'inherit' }
  );
}
