#!/usr/bin/env node

const [,, command] = process.argv;

if (command === 'install') {
  require('../scripts/setup.js');
} else {
  console.log('Unknown command:', command);
  process.exit(1);
}
