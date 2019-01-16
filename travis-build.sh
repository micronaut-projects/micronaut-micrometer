#!/bin/bash
set -e
EXIT_STATUS=0

git config --global user.name "$GIT_NAME"
git config --global user.email "$GIT_EMAIL"
git config --global credential.helper "store --file=~/.git-credentials"
echo "https://$GH_TOKEN:@github.com" > ~/.git-credentials

if [[ $EXIT_STATUS -eq 0 ]]; then
    if [[ -n $TRAVIS_TAG ]]; then
        echo "Skipping Tests to Publish Release"
        ./gradlew pTML assemble --no-daemon || EXIT_STATUS=$?
    else
        ./gradlew --stop
        ./gradlew testClasses --no-daemon || EXIT_STATUS=$?

        ./gradlew --stop
        ./gradlew check --no-daemon || EXIT_STATUS=$?
    fi
fi

if [[ $EXIT_STATUS -eq 0 ]]; then
    echo "Publishing archives for branch $TRAVIS_BRANCH"
    if [[ -n $TRAVIS_TAG ]] || [[ $TRAVIS_BRANCH =~ ^1.0.x$ && $TRAVIS_PULL_REQUEST == 'false' ]]; then

      echo "Publishing archives"
      ./gradlew --stop
      if [[ -n $TRAVIS_TAG ]]; then
          ./gradlew bintrayUpload --no-daemon --stacktrace || EXIT_STATUS=$?
          if [[ $EXIT_STATUS -eq 0 ]]; then
            ./gradlew synchronizeWithMavenCentral --no-daemon
          fi
      else
          ./gradlew publish --no-daemon --stacktrace || EXIT_STATUS=$?
      fi

      if [[ $EXIT_STATUS -eq 0 ]]; then
       ./gradlew --console=plain --no-daemon docs  || EXIT_STATUS=$?

        git clone https://${GH_TOKEN}@github.com/micronaut-projects/micronaut-micrometer.git -b gh-pages gh-pages --single-branch > /dev/null

        cd gh-pages


        # If there is a tag present then this becomes the latest
        if [[ -n $TRAVIS_TAG ]]; then
            mkdir -p latest
            cp -r ../build/docs/. ./latest/
            git add latest/*

            version="$TRAVIS_TAG"
            version=${version:1}
            majorVersion=${version:0:4}
            majorVersion="${majorVersion}x"

            mkdir -p "$version"
            cp -r ../build/docs/. "./$version/"
            git add "$version/*"

            mkdir -p "$majorVersion"
            cp -r ../build/docs/. "./$majorVersion/"
            git add "$majorVersion/*"

        fi

        git commit -a -m "Updating docs for Travis build: https://travis-ci.org/$TRAVIS_REPO_SLUG/builds/$TRAVIS_BUILD_ID" && {
          git push origin HEAD || true
        }
        cd ..

        rm -rf gh-pages
      fi
   fi
fi

exit $EXIT_STATUS
