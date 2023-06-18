#!/bin/bash

set +e # don't abort if commands exit with non zero exit code
gcloud firebase test android run \
    --app $APPLICATION_APK \
    --test $INSTRUMENTATION_APK \
    --results-dir $GCLOUD_BUCKET_DIRECTORY \
    --device model=redfin,version=30 `# Pixel 5` \
    --device model=panther,version=33 `# Pixel 7`
test_code=$?
set -e # abort if commands exit with non zero exit code

test_success="false"
exit_code=$test_code
# https://firebase.google.com/docs/test-lab/android/command-line#script_exit_codes
if [[ "$test_code" == "0" ]]; then # tests succeeded
    test_success="true"
elif [[ "$test_code" == "10" ]]; then # tests failed
    exit_code=0 # workflow job shouldn't fail just yet
fi

echo "test_success=$test_success" >> $GITHUB_OUTPUT

exit $exit_code

