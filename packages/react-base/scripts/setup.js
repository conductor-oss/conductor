#!/usr/bin/env node

const { execSync } = require('child_process');

const packagesToInstall = [
  'react@^19.1.0',
  'react-dom@^19.1.0',
  '@mui/material@^7.2.0',
  'eslint@^9.30.0',
  'eslint-plugin-react-hooks@^5.2.0',
  'typescript@^5.8.3',
  'vite@^7.0.0'
];

function installPackages() {
  try {
    console.log('Installing required packages...');
    const installCmd = `npm install ${packagesToInstall.join(' ')} --save-dev`;
    console.log(`Running: ${installCmd}`);
    execSync(installCmd, { stdio: 'inherit' });
    console.log('All packages installed successfully!');
  } catch (err) {
    console.error('Failed to install packages:', err);
    process.exit(1);
  }
}

installPackages();
