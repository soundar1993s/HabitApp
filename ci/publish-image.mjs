import { execFileSync } from 'node:child_process';
import { setTimeout } from 'node:timers/promises';
const { IMAGE_REPOSITORY: repository, IMAGE_TAG: tag } = process.env;
if (!/^\d{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com\/habitapp$/.test(repository || '')) throw new Error('Invalid repository');
if (!/^\d+-[a-f0-9]{12}$/.test(tag || '')) throw new Error('Invalid image tag');
const capture = (cmd, args) => execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] }).trim();
async function retry(label, action) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    try { return action(); } catch {
      if (attempt === 3) throw new Error(`${label} failed after three attempts`);
      console.error(`${label} failed; retrying`);
      await setTimeout(attempt * 5000);
    }
  }
}
await retry('ECR login', () => execFileSync('docker', ['login', '--username', 'AWS', '--password-stdin', repository.split('/')[0]], {
  input: capture('aws', ['ecr', 'get-login-password']), stdio: ['pipe', 'inherit', 'inherit'],
}));
const image = `${repository}:${tag}`;
const digest = capture('docker', ['image', 'inspect', image, '--format', '{{.Id}}']);
await retry('ECR publication', () => {
  const result = JSON.parse(capture('aws', ['ecr', 'batch-get-image', '--repository-name', 'habitapp', '--image-ids', `imageTag=${tag}`, '--output', 'json']));
  if (result.images?.length) {
    if (JSON.parse(result.images[0].imageManifest).config?.digest !== digest) throw new Error('Immutable tag mismatch');
    console.log(`Verified existing ${image}`);
    return;
  }
  execFileSync('docker', ['push', '--platform', 'linux/amd64', image], { stdio: 'inherit' });
});
