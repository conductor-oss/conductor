#!/bin/bash
# This script will build the project.

if [ "$TRAVIS_SECURE_ENV_VARS" = "true" ]; then
  echo "Decrypting publishing credentials"
  openssl aes-256-cbc -k "$NETFLIX_OSS_SIGNING_FILE_PASSWORD" -in secrets/signing-key.enc -out secrets/signing-key -d
fi



