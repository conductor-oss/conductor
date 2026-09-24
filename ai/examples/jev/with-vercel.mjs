// Keep the ephemeral Jev token in the child environment, never in arguments or logs.
import { getToken } from '@vercel/connect';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

try {
  const connector = process.env.JEV_CONNECTOR;
  if (!connector?.startsWith('jev/')) throw new Error('Missing connector');
  const token = await getToken(connector, { subject: { type: 'app' } });
  const child = spawn('python3', [
    fileURLToPath(new URL('./jev_agent.py', import.meta.url)),
    ...process.argv.slice(2), '--route', 'typesafe',
  ], {
    env: { ...process.env, TYPESAFE_API_KEY: token },
    stdio: 'inherit',
  });
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => child.kill(signal));
  }
  child.on('error', () => {
    console.error('Unable to start the Jev agent.');
    process.exitCode = 1;
  });
  child.on('exit', code => { process.exitCode = code ?? 1; });
} catch {
  console.error('Jev connection failed. Check JEV_CONNECTOR and local Vercel OIDC access.');
  process.exitCode = 1;
}
