import fs from 'node:fs';
const repository = process.env.IMAGE_REPOSITORY;
const tag = process.env.IMAGE_TAG;
if (!/^\d{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com\/habitapp$/.test(repository ?? '') ||
    !/^[0-9]+-[a-f0-9]{12}$/.test(tag ?? '')) throw new Error('Invalid image repository or tag');
const path = 'helm/habitapp/values.yaml';
const original = fs.readFileSync(path, 'utf8');
const updated = original.replace(/^(  repository:).*$/m, `$1 ${repository}`)
  .replace(/^(  tag:).*$/m, `$1 "${tag}"`);
if (updated === original) throw new Error('No image configuration changed');
fs.writeFileSync(path, updated);
