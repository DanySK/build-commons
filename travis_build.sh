#!/bin/bash
openssl aes-256-cbc -K $encrypted_71fd7511358b_key -iv $encrypted_71fd7511358b_iv -in prepare_environment.sh.enc -out prepare_environment.sh -d
bash prepare_environment.sh
set -e
./gradlew
./gradlew publishMainPublicationToMavenRepository
./gradlew publishPlugins --continue || exit 0
